package dk.kb.xcorrsound;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import dk.kb.facade.XCorrSoundFacade;

public class JavaIsmirSearchPerformanceTest {

    //Files that has match using the slow search algorithm and all are correct
    static String[] match_last_xmas=new String[] {          
            "clip_P3_1400_1600_040806_001-java.mp3",            
            "P3_0600_0800_931216_001.mp3",//These below not in GIT
            "P3_0600_0800_931227_001.mp3",
            "P3_0800_1000_931224_001.mp3",
            "P3_1000_1200_931215_001.mp3",
            "P3_1200_1400_930103_001.mp3",
            "P3_1600_1800_931215_001.mp3",
            "P3_2000_2200_041007_001.mp3",
            "P3_2000_2200_931204_001.mp3",
            "P3_2200_0000_931204_001.mp3"            
    };
    
    
    /*
    
    @Test
    void TestExactBinaryMatch() throws Exception {
        
        //this file with last xmas is from another sound source than the radio files with match
        String soundChunk = Thread.currentThread().getContextClassLoader().getResource("last_xmas_youtube.mp3").getFile();                       
        long[] fingerPrintsForChunk = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunk);
        
        
        int filesWithMatch=0;
        for (String file : match_last_xmas) {           
            String soundFull = Thread.currentThread().getContextClassLoader().getResource(file).getFile();    
            long[] fingerPrintsForFull = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundFull);
        
           ArrayList<Long> offsetMatches = getOffsetMatchesExtact32BitMatch(fingerPrintsForChunk, fingerPrintsForFull);
           if (offsetMatches.size() > 0) {
               filesWithMatch++;
           }        
        }
        System.out.println("Excact binary batch found:"+filesWithMatch +" out of: "+match_last_xmas.length);                                
       }
*/
  /*  
    
    @Test
    void TestIshmirMatch() throws Exception {
        
        //this file with last xmas is from another sound source than the radio files with match
        String soundChunk = Thread.currentThread().getContextClassLoader().getResource("last_xmas_youtube.mp3").getFile();                       
        long[] fingerPrintsForChunk = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunk);
        
        
        int filesWithMatch=0;
        for (String file : match_last_xmas) {           
            String soundFull = Thread.currentThread().getContextClassLoader().getResource(file).getFile();    
            long[] fingerPrintsForFull = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundFull);
        
           ArrayList<Long> offsetMatches = getOffsetMatchesExtact32BitMatch(fingerPrintsForChunk, fingerPrintsForFull);
           if (offsetMatches.size() > 0) {
               filesWithMatch++;
           }        
        }
        System.out.println("Excact binary batch found:"+filesWithMatch +" out of: "+match_last_xmas.length);                                
       }
    /*
     * Uses the current implementation
     * 
     */    
   
    
    /*
    
    @Test
   void getOffsetIsmirMatching()  throws Exception{

        ArrayList<Long> offsetMatch = new ArrayList<Long>();
        String result=  XCorrSoundFacade.ishmirMatch("last_xmas_youtube.mp3","P3_0600_0800_931216_001.mp3");
        System.out.println(result);        
        
        
    }
    
*/
    
    
    /*
     * Looks for exact 32 bit match.
     * Performance is slow due to double loop. This will not be the case if values are stored in solr
     * 
     */    
    public ArrayList<Long> getOffsetMatchesExtact32BitMatch(long[] fingerPrintFromUpload, long[] fingerPrintStored){

        ArrayList<Long> offsetMatch = new ArrayList<Long>();
        //find max match, slow
        for (int i=0;i<fingerPrintFromUpload.length;i++) {
        
            for (int j=0;j<fingerPrintStored.length;j++) {          
                        
                if (fingerPrintFromUpload[i]==fingerPrintStored[j]) {
                    int matches=1;
                    //Now count matches starting from here.
                    int a=i;
                    int b=j;
                    while (fingerPrintFromUpload[++a]==fingerPrintStored[++b]) {                        
                        System.out.println("matches="+matches +" for pos:"+a +","+b +" value:"+fingerPrintStored[b]);
                        offsetMatch.add((long) b);
                        matches++;
                    }                 
             }                
           }
        }        
        return offsetMatch;
    }
    
    
    
    
    
}
