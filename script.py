import os



wavDirectory = "C:\\Users\\brend\\Desktop\\serc\\audio\\fold1"

#os.mkdir("C:\\Users\\brend\\Desktop\\serc\\copies")
copytoDirectory ="C:\\Users\\brend\\Desktop\\serc\\copies" 
csvPath = "C:\\Users\\brend\\Desktop\\serc\\UrbanSound8k.csv"
fd = open(csvPath, "r")
tuples = fd.readlines()

fd.close()

count = 0
for i in os.listdir(wavDirectory):
    for j in tuples:
        if(i in j):
            strings = j.split(",")
            print("XXXXXXXX")
            print(i)
            
            print(strings[7].strip() + str(count))
            count = count + 1
            #print(wavDirectory + "\\" + i)
            #print(copytoDirectory + "\\" + strings[7].strip() + ".wav")
            #os.rename(wavDirectory + "\\" + i,copytoDirectory + "\\" + strings[7].strip()+ str(count) + ".wav")
            
        #hmnm


    