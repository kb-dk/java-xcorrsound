package dk.kb.xcorrsound.search;

import dk.kb.xcorrsound.FingerprintStrategy;
import org.apache.commons.lang3.time.DurationFormatUtils;

public class IsmirSearchResult {
    private final String filenameResult;
    private final int posInIndex;
    private final Integer dist;
    private final Integer hitFileStart;
    private final FingerprintStrategy fingerprintStrategy;
    
    public IsmirSearchResult(String filenameResult,
                             int posInIndex,
                             Integer dist,
                             Integer hitFileStart,
                             FingerprintStrategy fingerprintStrategy) {
    
        this.filenameResult = filenameResult;
        this.posInIndex     = posInIndex;
        this.dist           = dist;
        this.hitFileStart   = hitFileStart;
        this.fingerprintStrategy = fingerprintStrategy;
    }
    
    
    public String toString(){
    
        int sampleInFile = posInIndex - hitFileStart;
    
        String timestamp = formatTimestamp(sampleInFile * fingerprintStrategy.getAdvance() / fingerprintStrategy.getSampleRate());
    
        String ss = "match in '" + filenameResult
                    + "' at " + timestamp
                    + " with distance " + dist + "\n";
        return ss;
    }
    
    
    private static String formatTimestamp(int seconds) {
        return DurationFormatUtils.formatDuration(seconds * 1000, "HH:mm:ss", true);
    }
}
