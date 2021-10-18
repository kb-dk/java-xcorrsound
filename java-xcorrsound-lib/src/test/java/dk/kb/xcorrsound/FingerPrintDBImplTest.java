package dk.kb.xcorrsound;

import dk.kb.xcorrsound.index.FingerprintDBIndexer;
import dk.kb.xcorrsound.search.FingerprintDBSearcher;
import dk.kb.xcorrsound.search.IsmirSearchResult;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;

class FingerPrintDBImplTest {
    
    public static final String DBFILE = "testDB32";
    
    @Test
    public void insert() throws IOException, UnsupportedAudioFileException, InterruptedException {
        File testDB = resetDB();
        FingerprintDBIndexer ismir = new FingerprintDBIndexer(2048, 64, 5512, 32, DBFILE);
        
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("clip_P3_1400_1600_040806_001-java.mp3")
                               .getFile();
        ismir.insert(mp3file, "testFile");
    }
    
    
    private File resetDB() throws IOException {
        File testDB = new File(DBFILE);
        FileUtils.deleteQuietly(testDB);
        FileUtils.deleteQuietly(new File(DBFILE + ".map"));
        FileUtils.touch(testDB);
        return testDB;
    }
    
    @Test
    public void query() throws UnsupportedAudioFileException, InterruptedException, IOException {
        FingerprintDBSearcher ismir = new FingerprintDBSearcher(2048, 64, 5512, 32, DBFILE);
        
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("chunck1.mp3")
                               .getFile();
    
        List<IsmirSearchResult> results = ismir.query_scan(mp3file,
                                                           null,
                                                           FingerprintDBSearcher.DEFAULT_CRITERIA);
        
        
        assertThat(results,
                   hasItem(
                           allOf(
                                   hasProperty("dist", equalTo(363)),
                                   hasProperty("posInIndex", equalTo(20109)))));
    }
    
    @Test
    @Disabled
    public void queryLarge() throws UnsupportedAudioFileException, InterruptedException, IOException,
                                    URISyntaxException {
        File indexFile = new File(Thread.currentThread()
                                        .getContextClassLoader()
                                        .getResource("drp3_2007-12-01.ismir.index")
                                        .toURI());
        FingerprintDBSearcher ismir = new FingerprintDBSearcher(2048, 64, 5512, 32, indexFile.getAbsolutePath());
        
        File mp3file = new File(Thread.currentThread()
                                      .getContextClassLoader()
                                      .getResource("mceinar_chunk1.mp3")
                                      .toURI());
        List<IsmirSearchResult> results = ismir.query_scan(mp3file.getAbsolutePath(),
                                                           null,
                                                           FingerprintDBSearcher.DEFAULT_CRITERIA);
        for (IsmirSearchResult result : results) {
            System.out.println(result.toString());
        }
        
        assertThat(results, hasItem(allOf(hasProperty("dist", equalTo(363)),
                                          hasProperty("posInIndex", equalTo(20109)))));
        
        //Assertions.assertTrue(result.toString().contains("at 00:03:51 with distance 363"));
    }
}
