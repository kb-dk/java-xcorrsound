package dk.kb.xcorrsound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dk.kb.facade.XCorrSoundFacade;



/*
 * This test will check if java implementation of the Ishmir algorithm 
 * produces same results at the original C++.
 */
public class JavaIshmirImplTest {

    
    /*
     * Will test a fingerprint generated with original C++ code can is generated 
     * identical with the java-implementation.  
     * 
     */
    @Test
    void testIdenticalFingerPrint() throws Exception {
        
        //Generate a new fingerprint with java
        String soundChunk = Thread.currentThread().getContextClassLoader().getResource("last_xmas_chunk1.mp3").getFile();
                        
        long[] fingerPrintsForFile = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunk);
        
        //Load fingerprint generated with C++ for same file;       
        
        String fingerPrintOrgFile = Thread.currentThread().getContextClassLoader().getResource("last_xmas_chunk1_org.fingerprint").getFile();         
        
        long[] fingerPrintOrg = XCorrSoundFacade.readFingerPrintFromFile(fingerPrintOrgFile);
        
        assertEquals(fingerPrintOrg.length,fingerPrintsForFile.length,"Finger print not matching, different file sizes");
                
        for (int i =0;i<fingerPrintOrg.length;i++) {
            assertTrue(fingerPrintOrg[i]==fingerPrintsForFile[i]);
        }        
        //Test with wave-file. (no ffmpeg conversion)        
        String soundChunkWave = Thread.currentThread().getContextClassLoader().getResource("last_xmas_chunk1.mp3.wav").getFile();
                 
        
        long[] fingerPrintsForFileWave = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunkWave);
        
        for (int i =0;i<fingerPrintOrg.length;i++) {
            assertTrue(fingerPrintOrg[i]==fingerPrintsForFileWave[i]);
        }
        
    }

    @Test
    void FingerPrintPerformanceTest() throws Exception {
                               
        String soundChunkWave = Thread.currentThread().getContextClassLoader().getResource("last_xmas_chunk1.mp3.wav").getFile();
         
        int maxRuns=100;
        long start=System.currentTimeMillis();
        for (int i =0;i<maxRuns;i++) {
           XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunkWave);
        }
        long time=System.currentTimeMillis()-start;
        System.out.println("Average time in millis:"+time/(1d*maxRuns));
        
        
    }
    
    
}
