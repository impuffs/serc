package com.tensorflow.android.noiseclassifier

import android.content.Context
import android.location.Location
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.*
import android.provider.MediaStore
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ml.quaterion.noiseClassification.Recognition
import com.tensorflow.android.audio.features.MFCC
import com.tensorflow.android.audio.features.WavFile
import com.tensorflow.android.audio.features.WavFileException
import kotlinx.android.synthetic.main.activity_main.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.IOException
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

    protected  class myRecorder : Thread(){
        private val bufferSize = 8192
        private val aformat = AudioFormat.Builder().setSampleRate(32000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
        private val audioRecorder = AudioRecord.Builder().setBufferSizeInBytes(bufferSize).setAudioFormat(aformat).setAudioSource(MediaRecorder.AudioSource.MIC).build();
        var x = 0
        //comment
        fun run(filename: File){
            var data = ByteArray(bufferSize);

            audioRecorder.startRecording();

            audioRecorder.read(data,0,bufferSize);

            filename.writeBytes(data);
            if (x >= 3){
                Log.d("myTag", "REMEMBER WE ONLY RAN 3 TIMES");
                return;
            }
            x = x + 1;

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Log.d("myTag", Environment.getExternalStorageDirectory().toString())

        //  val languages = resources.getStringArray(R.array.Languages)

        val externalStorage: File = Environment.getExternalStorageDirectory()

        val audioDirPath = externalStorage.absolutePath + "/audioData";
        val directory = File(audioDirPath)
        Log.d("myTag", directory.mkdir().toString())

        val fileNames: MutableList<String> = ArrayList()


        File(audioDirPath).walk().forEach{

            if(it.absolutePath.endsWith(".wav")){
                fileNames.add(it.name)
            }

        }

        // access the spinner
        val spinner = findViewById<Spinner>(R.id.spinner)
        if (spinner != null) {
            val adapter = ArrayAdapter(this,
                    android.R.layout.simple_spinner_item, fileNames)
            spinner.adapter = adapter

        }


        val mappingFile = File(audioDirPath + "/MappingFile.csv")
        //Another Change

        var recorder = myRecorder();

        record_button.setOnClickListener( View.OnClickListener {
            try {
                var location = Location("me");
                var date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date());
                var audioFile = ""
                if (record_button.text == "Record") {
                    var temporyTitleForButton = audioFileName.text.toString()
                    audioFile = date.toString() + location.toString()
                    var sd = File(audioDirPath + "/" + audioFile)
                    record_button.text = "Recording";
                    recorder.run(sd)
                    mappingFile.appendText(temporyTitleForButton + "," + audioFile + "\n")

                } else {
                    record_button.text = "Record";
                    recorder.interrupt();
                }
            } catch (e: java.lang.Exception){
                Log.d("myTag", e.toString());
            }

        })

        classify_button.setOnClickListener( View.OnClickListener {
            val selFilePath = spinner.selectedItem.toString()
            var audioFilePath = audioDirPath + '/' + selFilePath;
            if ( !TextUtils.isEmpty( selFilePath ) ){


                val result = classifyNoise(audioFilePath)
                result_text.text = "Predicted Noise : $result"
            }
            else{
                Toast.makeText( this@MainActivity, "Please enter a message.", Toast.LENGTH_LONG).show();
            }
        })

    }


    fun classifyNoise ( audioFilePath: String ): String? {

        val mNumFrames: Int
        val mSampleRate: Int
        val mChannels: Int
        var meanMFCCValues : FloatArray = FloatArray(1)

        var predictedResult: String? = "Unknown"

        var wavFile: WavFile? = null
        try {
            wavFile = WavFile.openWavFile(File(audioFilePath))
            mNumFrames = wavFile.numFrames.toInt()
            mSampleRate = wavFile.sampleRate.toInt()
            mChannels = wavFile.numChannels
            val buffer =
                    Array(mChannels) { DoubleArray(mNumFrames) }

            var frameOffset = 0
            val loopCounter: Int = mNumFrames * mChannels / 4096 + 1
            for (i in 0 until loopCounter) {
                frameOffset = wavFile.readFrames(buffer, mNumFrames, frameOffset)
            }

            //trimming the magnitude values to 5 decimal digits
            val df = DecimalFormat("#.#####")
            df.setRoundingMode(RoundingMode.CEILING)
            val meanBuffer = DoubleArray(mNumFrames)
            for (q in 0 until mNumFrames) {
                var frameVal = 0.0
                for (p in 0 until mChannels) {
                    frameVal = frameVal + buffer[p][q]
                }
                meanBuffer[q] = df.format(frameVal / mChannels).toDouble()
            }


            //MFCC java library.
            val mfccConvert = MFCC()
            mfccConvert.setSampleRate(mSampleRate)
            val nMFCC = 40
            mfccConvert.setN_mfcc(nMFCC)
            val mfccInput = mfccConvert.process(meanBuffer)
            val nFFT = mfccInput.size / nMFCC
            val mfccValues =
                    Array(nMFCC) { DoubleArray(nFFT) }

            //loop to convert the mfcc values into multi-dimensional array
            for (i in 0 until nFFT) {
                var indexCounter = i * nMFCC
                val rowIndexValue = i % nFFT
                for (j in 0 until nMFCC) {
                    mfccValues[j][rowIndexValue] = mfccInput[indexCounter].toDouble()
                    indexCounter++
                }
            }

            //code to take the mean of mfcc values across the rows such that
            //[nMFCC x nFFT] matrix would be converted into
            //[nMFCC x 1] dimension - which would act as an input to tflite model
            meanMFCCValues = FloatArray(nMFCC)
            for (p in 0 until nMFCC) {
                var fftValAcrossRow = 0.0
                for (q in 0 until nFFT) {
                    fftValAcrossRow = fftValAcrossRow + mfccValues[p][q]
                }
                val fftMeanValAcrossRow = fftValAcrossRow / nFFT
                meanMFCCValues[p] = fftMeanValAcrossRow.toFloat()
            }


        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: WavFileException) {
            e.printStackTrace()
        }

        predictedResult = loadModelAndMakePredictions(meanMFCCValues)

        return predictedResult

    }


    protected fun loadModelAndMakePredictions(meanMFCCValues : FloatArray) : String? {

        var predictedResult: String? = "unknown"

        //load the TFLite model in 'MappedByteBuffer' format using TF Interpreter
        val tfliteModel: MappedByteBuffer =
                FileUtil.loadMappedFile(this, getModelPath())
        val tflite: Interpreter

        /** Options for configuring the Interpreter.  */
        val tfliteOptions =
                Interpreter.Options()
        tfliteOptions.setNumThreads(2)
        tflite = Interpreter(tfliteModel, tfliteOptions)

        //obtain the input and output tensor size required by the model
        //for urban sound classification, input tensor should be of 1x40x1x1 shape
        val imageTensorIndex = 0
        val imageShape =
                tflite.getInputTensor(imageTensorIndex).shape()
        val imageDataType: DataType = tflite.getInputTensor(imageTensorIndex).dataType()
        val probabilityTensorIndex = 0
        val probabilityShape =
                tflite.getOutputTensor(probabilityTensorIndex).shape()
        val probabilityDataType: DataType =
                tflite.getOutputTensor(probabilityTensorIndex).dataType()

        //need to transform the MFCC 1d float buffer into 1x40x1x1 dimension tensor using TensorBuffer
        val inBuffer: TensorBuffer = TensorBuffer.createDynamic(imageDataType)
        inBuffer.loadArray(meanMFCCValues, imageShape)
        val inpBuffer: ByteBuffer = inBuffer.getBuffer()
        val outputTensorBuffer: TensorBuffer =
                TensorBuffer.createFixedSize(probabilityShape, probabilityDataType)
        //run the predictions with input and output buffer tensors to get probability values across the labels
        tflite.run(inpBuffer, outputTensorBuffer.getBuffer())


        //Code to transform the probability predictions into label values
        val ASSOCIATED_AXIS_LABELS = "labels.txt"
        var associatedAxisLabels: List<String?>? = null
        try {
            associatedAxisLabels = FileUtil.loadLabels(this, ASSOCIATED_AXIS_LABELS)
        } catch (e: IOException) {
            Log.e("tfliteSupport", "Error reading label file", e)
        }

        //Tensor processor for processing the probability values and to sort them based on the descending order of probabilities
        val probabilityProcessor: TensorProcessor = TensorProcessor.Builder()
                .add(NormalizeOp(0.0f, 255.0f)).build()
        if (null != associatedAxisLabels) {
            // Map of labels and their corresponding probability
            val labels = TensorLabel(
                    associatedAxisLabels,
                    probabilityProcessor.process(outputTensorBuffer)
            )

            // Create a map to access the result based on label
            val floatMap: Map<String, Float> =
                    labels.getMapWithFloatValue()

            //function to retrieve the top K probability values, in this case 'k' value is 1.
            //retrieved values are storied in 'Recognition' object with label details.
            val resultPrediction: List<Recognition>? = getTopKProbability(floatMap);

            //get the top 1 prediction from the retrieved list of top predictions
            predictedResult = getPredictedValue(resultPrediction)

        }
        return predictedResult

    }


    fun getPredictedValue(predictedList:List<Recognition>?): String?{
        val top1PredictedValue : Recognition? = predictedList?.get(0)
        return top1PredictedValue?.getTitle()
    }

    fun getModelPath(): String {
        // you can download this file from
        // see build.gradle for where to obtain this file. It should be auto
        // downloaded into assets.
        return "model.tflite"
    }

    /** Gets the top-k results.  */
    protected fun getTopKProbability(labelProb: Map<String, Float>): List<Recognition>? {
        // Find the best classifications.
        val MAX_RESULTS: Int = 1
        val pq: PriorityQueue<Recognition> = PriorityQueue(
                MAX_RESULTS,
                Comparator<Recognition> { lhs, rhs -> // Intentionally reversed to put high confidence at the head of the queue.
                    java.lang.Float.compare(rhs.getConfidence(), lhs.getConfidence())
                })
        for (entry in labelProb.entries) {
            pq.add(Recognition("" + entry.key, entry.key, entry.value))
        }
        val recognitions: ArrayList<Recognition> = ArrayList()
        val recognitionsSize: Int = Math.min(pq.size, MAX_RESULTS)
        for (i in 0 until recognitionsSize) {
            recognitions.add(pq.poll())
        }
        return recognitions
    }

    protected fun startRecording( recorder: AudioRecord, bufferSize: Int): Any? {
//        final int[] v = new int[] {R.id.buttonAck, R.id.buttonGo, R.id.buttonStanddown, R.id.buttonComplete};
        val x = 0
        recorder.startRecording()

            val data = ByteArray(bufferSize)
            try {
                val start = System.currentTimeMillis()
                //                final int i = v[x];
                val h = Handler(Looper.getMainLooper())
                h.post(object : Runnable {
                    override fun run() {
//                        receiver.receive(i);
                        val read = recorder.read(data, 0, data.size)
                        Log.i(
                            "myTag",
                            String.format("Read in Data: %d", read)
                        )
                    }
                })
                //                x = (x + 1) % v.length;
                Thread.sleep(200)
            } catch (e: Exception) {

            }
        return null
    }

}
