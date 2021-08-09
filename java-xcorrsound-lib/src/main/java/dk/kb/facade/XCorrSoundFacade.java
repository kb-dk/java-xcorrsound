package dk.kb.facade;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;

import dk.kb.xcorrsound.FingerprintStrategyIsmir;

public class XCorrSoundFacade {

    public static int[] generateFingerPrintFromSoundFile(String fileName) throws Exception{
        FingerprintStrategyIsmir fpGenerator = new FingerprintStrategyIsmir(2048, 64, 5512, 32);
         int[] fp =  fpGenerator.getFingerprintsForFile(fileName, null, null);
         return fp;
    }
    
    /*
     * Probably move impl to a util class
     * Not sure this will be used except by unittest since fingerprints are concatenated
     */
    public static int[] readFingerPrintFromFile(String fingerPrintFile) throws Exception {
        //TODO avoid ArrayList and load directly in array. How to determine size?
        ArrayList<Integer>  fingerPrint = new ArrayList<>();

        DataInputStream fin = new DataInputStream(IOUtils.buffer(new FileInputStream(fingerPrintFile)));
        while(fin.available()>0) {
            int value = Integer.reverseBytes(fin.readInt());
            fingerPrint.add(value);
        
        }
        return fingerPrint.stream().mapToInt(i -> i).toArray(); //Map arraylist to array
    }
    
}
