package dk.kb.xcorrsound;

import dk.kb.xcorrsound.index.FingerprintDBIndexer;
import dk.kb.xcorrsound.search.FingerprintDBSearcher;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

class FingerPrintDBImplTest {
    
    @Test
    void insert() throws IOException, UnsupportedAudioFileException, InterruptedException {
    
        FingerprintDBIndexer ismir = new FingerprintDBIndexer();
        FileUtils.touch(new File("testDB"));
        ismir.open("testDB");
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("clip_P3_1400_1600_040806_001-java.mp3")
                               .getFile();
        ismir.insert(mp3file, "testFile");
    }
    
    @Test
    void query() throws UnsupportedAudioFileException, InterruptedException, IOException {
        FingerprintDBSearcher ismir = new FingerprintDBSearcher();
        ismir.open("testDB");
        String mp3file = Thread.currentThread()
                               .getContextClassLoader()
                               .getResource("chunck1.mp3")
                               .getFile();
        StringWriter result = new StringWriter();
        ismir.query_scan(mp3file, result);
        System.out.println(result.toString());
    }
}