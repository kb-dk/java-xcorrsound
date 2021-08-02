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
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;

class FingerPrintDB16BitTest {
    
    public static final String DBFILE = "testDB16";
    
    @Test
    public void insert() throws IOException, UnsupportedAudioFileException, InterruptedException {
        
        FingerprintDBIndexer ismir = new FingerprintDBIndexer(2048, 64, 5512, 16);
        File testDB = resetDB();
        
        ismir.open(DBFILE);
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("clip_P3_1400_1600_040806_001-java.mp3")
                               .getFile();
        ismir.insert(mp3file, "testFile");
    }
    
    private File resetDB() throws IOException {
        File testDB = new File(DBFILE);
        FileUtils.deleteQuietly(testDB);
        FileUtils.deleteQuietly(new File(DBFILE+".map"));
        FileUtils.touch(testDB);
        return testDB;
    }
    
    @Test
    public void query() throws UnsupportedAudioFileException, InterruptedException, IOException {
        FingerprintDBSearcher ismir = new FingerprintDBSearcher(2048, 64, 5512, 16);
        ismir.open(DBFILE);
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("chunck1.mp3")
                               .getFile();
        StringWriter resultWriter = new StringWriter();
        List<IsmirSearchResult> results = ismir.query_scan(mp3file,
                                                           null,null,
                                                           FingerprintDBSearcher.DEFAULT_CRITERIA);
        
        
        assertThat(results,
                   hasItem(
                           allOf(
                                   hasProperty("dist", equalTo(79)),
                                   hasProperty("posInIndex", equalTo(20109)))));
        //Matchers.containsString("at 00:03:53 with distance 363"));
        //dist 79 if only using 16 bands
        System.out.println(resultWriter);
    }
    
    
    
  
}