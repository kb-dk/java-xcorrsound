package dk.kb.facade;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;

import dk.kb.xcorrsound.FingerprintStrategyIsmir;

public class XCorrSoundFacade {

    public static long[] generateFingerPrintFromSoundFile(String fileName) throws Exception{
        FingerprintStrategyIsmir fpGenerator = new FingerprintStrategyIsmir(2048, 64, 5512, 32);
         long[] fp =  fpGenerator.getFingerprintsForFile(fileName, null, null);
         return fp;
    }
     
    /*
     * Probably move impl to a util class
     * Not sure this will be used except by unittest since fingerprints are concatenated
     */
    public static long[] readFingerPrintFromFile(String fingerPrintFile) throws Exception {    
        //TODO avoid ArrayList and load directly in array. How to determine size?
        ArrayList<Long>  fingerPrint = new ArrayList<>();

        DataInputStream fin = new DataInputStream(IOUtils.buffer(new FileInputStream(fingerPrintFile)));        
        while(fin.available()>0) {        
            int value = Integer.reverseBytes(fin.readInt());
            long unsigned =Integer.toUnsignedLong(value); //
            fingerPrint.add(unsigned);
        
        }        
        return fingerPrint.stream().mapToLong(i -> i).toArray(); //Map arraylist to array
    }
    
}
