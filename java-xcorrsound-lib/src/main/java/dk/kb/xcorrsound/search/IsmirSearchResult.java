package dk.kb.xcorrsound.search;

import dk.kb.xcorrsound.FingerprintStrategy;
import org.apache.commons.lang3.time.DurationFormatUtils;

public class IsmirSearchResult {
    private final String filenameResult;
    private final int posInIndex;
    private final int dist;
    private final int hitFileStart;
    private final FingerprintStrategy fingerprintStrategy;
    
    public IsmirSearchResult(String filenameResult,
                             int posInIndex,
                             int dist,
                             int hitFileStart,
                             FingerprintStrategy fingerprintStrategy) {
    
        this.filenameResult = filenameResult;
        this.posInIndex     = posInIndex;
        this.dist           = dist;
        this.hitFileStart   = hitFileStart;
        this.fingerprintStrategy = fingerprintStrategy;
    }
    
    public String getFilenameResult() {
        return filenameResult;
    }
    
    public int getPosInIndex() {
        return posInIndex;
    }
    
    public int getDist() {
        return dist;
    }
    
    public int getHitFileStart() {
        return hitFileStart;
    }
    
    public FingerprintStrategy getFingerprintStrategy() {
        return fingerprintStrategy;
    }
    
    public String toString(){
        
        final int secondsIntoFile = (posInIndex - hitFileStart)
                            * fingerprintStrategy.getAdvance() / fingerprintStrategy.getSampleRate();
        String timestamp = formatTimestamp(secondsIntoFile);
    
        String ss = "match in '" + filenameResult
                    + "' at " + timestamp
                    + " with distance " + dist + "\n";
        return ss;
    }
    
    
    private static String formatTimestamp(int seconds) {
        return DurationFormatUtils.formatDuration(seconds * 1000, "HH:mm:ss", true);
    }
}
