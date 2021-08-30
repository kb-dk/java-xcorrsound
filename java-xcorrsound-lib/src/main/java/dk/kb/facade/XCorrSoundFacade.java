package dk.kb.facade;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;


import dk.kb.xcorrsound.FingerprintStrategyIsmir;
import dk.kb.xcorrsound.index.FingerprintDBIndexer;
import dk.kb.xcorrsound.search.FingerprintDBSearcher;

public class XCorrSoundFacade {

    
    public static String ishmirMatch(String uploadFile,String indexFile)  throws Exception{
        
        FingerprintDBSearcher ismir = new FingerprintDBSearcher();
        
        
        
        FingerprintDBIndexer ismirIndex = new FingerprintDBIndexer();
        File testDB = new File("testDB");
        FileUtils.deleteQuietly(testDB);
        FileUtils.deleteQuietly(new File("testDB.map"));
        FileUtils.touch(testDB);
        
        
        ismirIndex.open(testDB.getAbsolutePath());
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("indexFile")
                               .getFile();
        ismirIndex.insert(mp3file, "testFile");
        
                
        ismir.open("testDB");
        
        StringWriter result = new StringWriter();
        ismir.query_scan(uploadFile, FingerprintDBSearcher.DEFAULT_CRITERIA, result);
        
        
         ismir.close();
         ismirIndex.close();
        return result.toString();        
        }
        
        
    
    /**
     * Generate fingerprints for the given soundFile. This method is non-caching and always perform a full generation.
     *
     * One fingerprint is generated for each 64 samples on 5512Hz inputs.
     * This method is a wrapper for {@code generateFingerPrintFromSoundFile(fileName, 0}
     * @param fileName a recording or snippet.
     * @return raw fingerprints for the sound.
     */
    public static long[] generateFingerPrintFromSoundFile(String fileName) throws Exception{
        FingerprintStrategyIsmir fpGenerator = new FingerprintStrategyIsmir();                
         long[] fp =  fpGenerator.getFingerprintsForFile(fileName);        
         return fp;
    }
     
    /**
     * Generate fingerprints for the given soundFile. This method is non-caching and always perform a full generation.
     *
     * One fingerprint is generated for each 64 samples on 5512Hz inputs. Adjusting sampleOffset makes it possible to
     * fine tune the fingerprints. In reality the difference between fingerprints for different offsets is small, but
     * it can be relevant when doing precision matching.
     * @param fileName a recording or snippet.
     * @param sampleOffset how far into the sample the fingerprinting should start.
     * @return raw fingerprints for the sound.
     */
    public static long[] generateFingerPrintFromSoundFile(String fileName, int sampleOffset) throws Exception{
        FingerprintStrategyIsmir fpGenerator = new FingerprintStrategyIsmir();
         long[] fp =  fpGenerator.getFingerprintsForFile(fileName, sampleOffset);
         return fp;
    }

    /*
     * Probably move impl to a util class
     * Not sure this will be used except by unittest since fingerprints are concatenated
     */
    public static long[] readFingerPrintFromFile(String fingerPrintFile) throws Exception {    
        //TODO avoid ArrayList and load directly in array. How to determine size?
        ArrayList<Long>  fingerPrint = new ArrayList<Long>(); 

        DataInputStream fin = new DataInputStream(IOUtils.buffer(new FileInputStream(fingerPrintFile)));        
        while(fin.available()>0) {        
            int value = Integer.reverseBytes(fin.readInt());
            long unsigned =Integer.toUnsignedLong(value); //
            fingerPrint.add(unsigned);
        
        }        
        return fingerPrint.stream().mapToLong(i -> i).toArray(); //Map arraylist to array
    }
    
}
