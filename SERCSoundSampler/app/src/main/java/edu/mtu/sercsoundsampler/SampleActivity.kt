package edu.mtu.sercsoundsampler

import android.Manifest
import android.location.Location
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.sample_layout.*
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*

class SampleActivity : AppCompatActivity() {
    val TAG = "SampleActivity"
    val multiListener: MultiListener = MultiListener()
    var audioDirPath: String? = null
    var mappingFile: File? = null
    var dateFilename = ""
    var sd: File? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sample_layout)
        ensureDataFolderAndMappingFile()
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO)
            .withListener(multiListener)
            .check()
        if (multiListener.proceed) {
            Log.i(TAG, "Premissions granted, here we go...")
        }
        record_button.setOnClickListener( View.OnClickListener {
            try {
                var recorder = MyRecorder();
                var location = Location("me")
                if (multiListener.proceed && record_button.text == "Record") {
                    var date = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Date())
                    var capturedSound = audioFileName.text.toString()
                    dateFilename = date.toString() + ".pcm"
                    Log.i(TAG, "Recording " + dateFilename)
                    sd = File(audioDirPath + "/" + dateFilename)
                    record_button.text = "Recording";
                    recorder.run(sd!!, TAG)
                    addEntry(mappingFile!!, capturedSound, dateFilename, location)
//                    mappingFile!!.appendText(temporyTitleForButton + "," + audioFile + "\n")

                } else {
                    record_button.text = "Record"
                    recorder.interrupt()
                    audioFileName.setText("")
                }
            } catch (e: java.lang.Exception){
                Log.d(TAG, e.toString());
            }
        })
    }

    private fun addEntry(mappingFile: File, capturedSound: String, audioFilename: String, location: Location) {
        val w: PrintWriter = PrintWriter(mappingFile)
        w.append(audioFilename)
            .append(",")
            .append(capturedSound)
            .append(",")
            .append(location.latitude.toString())
            .append(",")
            .append(location.longitude.toString())
            .append("\n")
        w.close()
    }

    fun ensureDataFolderAndMappingFile() {
        Log.d(TAG, Environment.getExternalStorageDirectory().toString())
        val externalStorage: File = Environment.getExternalStorageDirectory()
        audioDirPath = externalStorage.absolutePath + "/audioData";
        val directory = File(audioDirPath)
        directory.mkdir()
        mappingFile = File(audioDirPath + "/MappingFile.csv")
        Log.d(TAG, "Ensured existence of: " + mappingFile!!.canonicalPath)
    }
}

class MyRecorder : Thread() {
    private val bufferSize = 8192
    private val aformat = AudioFormat.Builder().setSampleRate(32000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(
        AudioFormat.CHANNEL_IN_MONO).build();
    private val audioRecorder = AudioRecord.Builder().setBufferSizeInBytes(bufferSize).setAudioFormat(aformat).setAudioSource(
        MediaRecorder.AudioSource.MIC).build();

    var x = 0
    //comment
    fun run(filename: File, tag: String) {
        var data = ByteArray(bufferSize);

        audioRecorder.startRecording();

        audioRecorder.read(data,0,bufferSize);

        filename.writeBytes(data);
        if (x >= 3) {
            Log.d(tag, "REMEMBER WE ONLY RAN 3 TIMES");
            return;
        }
        x = x + 1;

    }
}

class MultiListener : MultiplePermissionsListener {
    var proceed: Boolean = false
    override fun onPermissionsChecked(prt: MultiplePermissionsReport) { proceed = true }
    override fun onPermissionRationaleShouldBeShown(permissions: List<PermissionRequest>, token: PermissionToken) {
    }
}