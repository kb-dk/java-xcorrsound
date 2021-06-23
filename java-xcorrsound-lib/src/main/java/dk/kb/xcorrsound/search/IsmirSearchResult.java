package dk.kb.xcorrsound.search;

import dk.kb.xcorrsound.FingerprintStrategy;
import org.apache.commons.lang3.time.DurationFormatUtils;

public class IsmirSearchResult {
    private final String filename;
    private final int posInIndex;
    private final int dist;
    private final int hitFileStart;
    private final FingerprintStrategy fingerprintStrategy;
    
    public IsmirSearchResult(String filename,
                             int posInIndex,
                             int dist,
                             int hitFileStart,
                             FingerprintStrategy fingerprintStrategy) {
    
        this.filename   = filename;
        this.posInIndex = posInIndex;
        this.dist           = dist;
        this.hitFileStart   = hitFileStart;
        this.fingerprintStrategy = fingerprintStrategy;
    }
    
    public String getFilename() {
        return filename;
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
    
    
    public String getTimestamp() {
        final int secondsIntoFile = getOffsetSeconds();
        return DurationFormatUtils.formatDuration(secondsIntoFile * 1000L, "HH:mm:ss", true);
    }
    
    public int getOffsetSeconds() {
        return (posInIndex - hitFileStart)
               * fingerprintStrategy.getAdvance() / fingerprintStrategy.getSampleRate();
    }
    
    
    public String toString(){
        
        String ss = "match in '" + filename
                    + "' at " + getTimestamp()
                    + " with distance " + dist + "\n";
        return ss;
    }
    
}
