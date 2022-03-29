package dk.kb.xcorrsound;

import dk.kb.xcorrsound.index.FingerprintDBIndexer;
import dk.kb.xcorrsound.search.FingerprintDBSearcher;
import dk.kb.xcorrsound.search.IsmirSearchResult;
import org.apache.commons.io.FileUtils;
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

public abstract class FingerPrintDBNImplTest {
    
    
    private final int operand;
    private int BANDS;
    public final String DBFILE;
    
    public FingerPrintDBNImplTest(int operand, int BANDS) {
        this.operand = operand;
        this.BANDS   = BANDS;
        DBFILE       = "testDB" + this.BANDS;
    }
    
    @Test
    public void insert() throws IOException, UnsupportedAudioFileException, InterruptedException, URISyntaxException {
        File testDB = resetDB();
        FingerprintDBIndexer ismir = new FingerprintDBIndexer(2048, 64, 5512, BANDS, DBFILE);
        
        String mp3file = new File(Thread.currentThread()
                                        .getContextClassLoader()
                                        .getResource("Monk Turner + Fascinoma - It's Your Birthday!.mp3")
                                        .toURI()).getAbsolutePath();
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
    public void query() throws UnsupportedAudioFileException, InterruptedException, IOException, URISyntaxException {
        FingerprintDBSearcher ismir = new FingerprintDBSearcher(2048, 64, 5512, BANDS, DBFILE);
        
        String mp3file = new File(Thread.currentThread()
                                        .getContextClassLoader()
                                        .getResource("Monk Turner + Fascinoma - It's Your Birthday!-5secChunk.mp3")
                                        .toURI()).getAbsolutePath();
        
        List<IsmirSearchResult> results = ismir.query_scan(mp3file,
                                                           null,
                                                           FingerprintDBSearcher.DEFAULT_CRITERIA*BANDS/32);
    
        System.out.println(results);
        assertThat(results,
                   hasItem(
                           allOf(
                                   hasProperty("dist", equalTo(operand)),
                                   hasProperty("posInIndex", equalTo(1317)))));
    }
    
    
}
