package dk.kb.xcorrsound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dk.kb.facade.XCorrSoundFacade;

import java.io.File;


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
        String soundChunk = new File(Thread.currentThread().getContextClassLoader().getResource("Monk Turner + Fascinoma - It's Your Birthday!.mp3").toURI()).getAbsolutePath();
        
        long[] fingerPrintsForFile = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunk);
        
        //Load fingerprint generated with C++ for same file;
    
        String fingerPrintOrgFile = new File(Thread.currentThread().getContextClassLoader().getResource("Monk Turner + Fascinoma - It's Your Birthday!.mp3.fingerprint").toURI()).getAbsolutePath();
    
    
        long[] fingerPrintOrg = XCorrSoundFacade.readFingerPrintFromFile(fingerPrintOrgFile);
        
        assertEquals(fingerPrintOrg.length,fingerPrintsForFile.length,"Finger print not matching, different file sizes");
        
        for (int i =0;i<fingerPrintOrg.length;i++) {
            assertEquals(fingerPrintOrg[i], fingerPrintsForFile[i]);
        }
        //Test with wave-file. (no ffmpeg conversion)
        String soundChunkWave = new File(Thread.currentThread().getContextClassLoader().getResource("Monk Turner + Fascinoma - It's Your Birthday!.mp3.wav").toURI()).getAbsolutePath();
        
        long[] fingerPrintsForFileWave = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunkWave);
        
        for (int i =0;i<fingerPrintOrg.length;i++) {
            assertEquals(fingerPrintOrg[i], fingerPrintsForFileWave[i]);
        }
        
    }

    @Test
    void FingerPrintPerformanceTest() throws Exception {
    
        String soundChunkWave = new File(Thread.currentThread().getContextClassLoader().getResource("Monk Turner + Fascinoma - It's Your Birthday!.mp3.wav").toURI()).getAbsolutePath();
    
    
        int maxRuns=100;
        long start=System.currentTimeMillis();
        for (int i =0;i<maxRuns;i++) {
           XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunkWave);
        }
        long time=System.currentTimeMillis()-start;
        System.out.println("Average time in millis:"+time/(1d*maxRuns));
        
        
    }
    
    
}
