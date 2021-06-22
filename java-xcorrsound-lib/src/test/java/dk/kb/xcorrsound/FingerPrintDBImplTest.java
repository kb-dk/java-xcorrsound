package dk.kb.xcorrsound;

import dk.kb.xcorrsound.index.FingerprintDBIndexer;
import dk.kb.xcorrsound.search.FingerprintDBSearcher;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;

class FingerPrintDBImplTest {
    
    @Test
    public void insert() throws IOException, UnsupportedAudioFileException, InterruptedException {
    
        FingerprintDBIndexer ismir = new FingerprintDBIndexer();
        File testDB = resetDB();
    
        ismir.open(testDB.getAbsolutePath());
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("clip_P3_1400_1600_040806_001-java.mp3")
                               .getFile();
        ismir.insert(mp3file, "testFile");
    }
    
    private File resetDB() throws IOException {
        File testDB = new File("testDB");
        FileUtils.deleteQuietly(testDB);
        FileUtils.deleteQuietly(new File("testDB.map"));
        FileUtils.touch(testDB);
        return testDB;
    }
    
    @Test
    public void query() throws UnsupportedAudioFileException, InterruptedException, IOException {
        FingerprintDBSearcher ismir = new FingerprintDBSearcher();
        ismir.open("testDB");
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("chunck1.mp3")
                               .getFile();
        StringWriter result = new StringWriter();
        ismir.query_scan(mp3file, FingerprintDBSearcher.DEFAULT_CRITERIA, result);
        Assertions.assertTrue(result.toString().contains("at 00:03:53 with distance 363"));
        System.out.println(result);
    }
    
    @Test
    @Disabled
    public void queryLarge() throws UnsupportedAudioFileException, InterruptedException, IOException,
                                    URISyntaxException {
        FingerprintDBSearcher ismir = new FingerprintDBSearcher();
        File indexFile = new File(Thread.currentThread()
                                        .getContextClassLoader()
                                        .getResource("drp3_2007-12-01.ismir.index")
                                        .toURI());
        ismir.open(indexFile.getAbsolutePath());
        File mp3file = new File(Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("mceinar_chunk1.mp3")
                               .toURI());
        StringWriter result = new StringWriter();
        ismir.query_scan(mp3file.getAbsolutePath(), FingerprintDBSearcher.DEFAULT_CRITERIA, result);
        System.out.println(result.toString());

        //Assertions.assertTrue(result.toString().contains("at 00:03:51 with distance 363"));
    }
}