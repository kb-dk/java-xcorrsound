package dk.kb.xcorrsound.index;

import dk.kb.xcorrsound.FingerPrintDB;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class FingerprintDBIndexer extends FingerPrintDB implements AutoCloseable {
    
    private Logger log = LoggerFactory.getLogger(this.getClass());
    
   
    
    public void insert(String filename, String indexedName)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        // assume we can have the entire file "filename" in memory.
        // "filename" is the filename of a wav file.
        
        //AudioFile a(filename.c_str());
        
        // List<int16_t> samples;
        
        //a.getSamplesForChannel(0, samples);
        log.info("Generating fingerprints for '{}'",filename);
        long[] fingerprints = this.fp_strategy.getFingerprintsForFile(filename);
System.out.println(fingerprints.length);
System.out.println(fingerprints);
        
        log.info("Generated {} fingerprints for '{}'",fingerprints.length, filename);
        
        String indexName1 = "-".equals(filename) ? indexedName : filename;
        
        // append fingerprint stream to this.dbFilename
        writeDBToDisk(this.dbFilename, fingerprints, indexName1);
    }
    
}
