package dk.kb.xcorrsound;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.util.List;

public interface FingerprintStrategy {
    
    long[] getFingerprintsForFileForIndex(String filename)
            throws IOException, UnsupportedAudioFileException, InterruptedException;
    
    long[] getFingerprintsForFileForSearch(String filename,
                                  Long offsetSeconds)
            throws IOException, UnsupportedAudioFileException, InterruptedException;
    
    default int getFrameLength() {
        return 0;
    }
    
    default int getAdvance() {
        return 0;
    }
    
    default int getSampleRate() {
        return 0;
    }
}
