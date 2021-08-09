package dk.kb.xcorrsound.index;

import dk.kb.xcorrsound.FingerPrintDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;

public class FingerprintDBIndexer extends FingerPrintDB {
    
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    
    public FingerprintDBIndexer(String indexFilename) throws IOException {
        super(indexFilename);
    }
    
    public FingerprintDBIndexer(int frameLength, int advance, int sampleRate, int bands, String indexFilename)
            throws IOException {
        super(frameLength, advance, sampleRate, bands, indexFilename);
    }
    
    public void insert(String filename, String indexedName)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        // assume we can have the entire file "filename" in memory.
        // "filename" is the filename of a wav file.
        
        //AudioFile a(filename.c_str());
        
        // List<int16_t> samples;
        
        //a.getSamplesForChannel(0, samples);
        log.debug("Generating fingerprints for '{}'", filename);
        int[] fingerprints = this.getFingerprintStrategy().getFingerprintsForFile(filename, null, null);
        
        log.debug("Generated {} fingerprints for '{}'", fingerprints.length, filename);
        
        String indexName1 = "-".equals(filename) ? indexedName : filename;
        
        // append fingerprint stream to this.dbFilename
        writeDBToDisk(this.dbFilename, fingerprints, indexName1);
    }
    
}
