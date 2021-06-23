package dk.kb.xcorrsound.search;

import dk.kb.xcorrsound.FingerPrintDB;
import dk.kb.xcorrsound.Utils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class FingerprintDBSearcher extends FingerPrintDB implements AutoCloseable {
    
    public static final double DEFAULT_CRITERIA = 0.35 * (macro_sz * Integer.BYTES * 8);
    
    private static final Logger log = LoggerFactory.getLogger(FingerprintDBSearcher.class);
    
    public FingerprintDBSearcher() {
    }
    
    public FingerprintDBSearcher(int frameLength, int advance, int sampleRate, int bands) {
        super(frameLength, advance, sampleRate, bands);
    }
    
    public List<IsmirSearchResult> query_scan(String queryFilename, double criteria)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
        
        log.info("Starting query_scan for {}", queryFilename);
        // "queryFilename" is the name of the wav file that is our query.
        //ret.clear();
        //AudioFile a(queryFilename.c_str());
        
        //List<int16_t> samples;
        
        //a.getSamplesForChannel(0, samples);
        
        long[] fingerprints = this.getFingerprintStrategy().getFingerprintsForFile(queryFilename);
        return query_scan(fingerprints, criteria);
    }
    
    public List<IsmirSearchResult> query_scan(long[] fingerprints, double criteria)
            throws IOException {
        int[] db = new int[1024 * 1024 + macro_sz];
        
        this.openForSearching();
        
        log.info("Starting search in {}", dbFilename);
        int pos = 0;
        int prevMatchPos = Integer.MAX_VALUE;
        
        List<IsmirSearchResult> result = new ArrayList<>();
        
        while (true) {
            int bufferContentCount = readDBBlob(db);
            //log.info("Reading next blob of {} bytes from db", read_bytes);
            if (bufferContentCount <= 0) {
                break;
            }
            
            //i counts through the bufferContent.
            //pos counts through the actual DB contents, so it is NOT reset in each loop
            for (int i = 0; i < bufferContentCount; i += 8, pos += 8) {
                
                int sampleRate = this.getFingerprintStrategy().getSampleRate();
                
                //If we are to close to the previous match, just continue
                if (pos - prevMatchPos < (sampleRate / 64)
                    && prevMatchPos != Integer.MAX_VALUE) {
                    continue;
                }
                
                //Check for early termination
                if (hammingEarlyTerminate(fingerprints,
                                          db,
                                          i).getKey()) {
                    //log.debug("Stopping search at frame {} as noisy overlap ({})",i,dist);
                    continue;
                } else {
                    
                    log.trace("Found possible match at {}, examining further", i);
                    Map.Entry<Integer, Integer> checkNearPosResult = checkNearPos(fingerprints,
                                                                                  pos,
                                                                                  db,
                                                                                  i);
                    i += nearRange;
                    pos += nearRange;
                    
                    final Integer hitDist = checkNearPosResult.getKey();
                    final Integer hitPos = checkNearPosResult.getValue();
                    
                    if (hitDist < criteria) {
                        log.info("Found hit at offset {} with dist {}", hitPos, hitDist);
                        prevMatchPos = hitPos;
                        
                        
                        Pair<Integer, Integer> hitEntry = offsetsToFile.keySet()
                                                                       .stream()
                                                                       //Only those that END after this hit
                                                                       .filter(pair -> pair.getRight() > hitPos
                                                                                       && hitPos > pair.getLeft())
                                                                       .findFirst()
                                                                       .orElse(Pair.of(0, 0));
                        
                        String filenameResult = offsetsToFile.get(hitEntry);
                        
                        
                        Integer hitFileStart = hitEntry.getLeft();
                        
                        IsmirSearchResult ismirSearchResult = new IsmirSearchResult(filenameResult,
                                                                                    hitPos,
                                                                                    hitDist,
                                                                                    hitFileStart,
                                                                                    this.getFingerprintStrategy());
                        result.add(ismirSearchResult);
                    }
                    
                }
            }
        }
        log.info("Completed search in {}", dbFilename);
        return result;
        
    }
    
    
    // percentage error. Break if in the 'noise zone'.
    // only check every now and then (i % 5)
    // and we must have a decent baseline, i.e. at least 10% through computation.
    // this is a heuristic to terminate early if we can see
    // there will not be a match here.
    private static Map.Entry<Boolean, Integer> hammingEarlyTerminate(long[] fingerprints, int[] db, int start) {
        
        int dist = 0;
        for (int i = 0; i < macro_sz; ++i) {
            
            dist += matchFingerprints(fingerprints[i + fpSkip], db[i + start]);
            
            if ((i % 5) == 0 && i > macro_sz / 10) {
                double bitsSeenSoFar = i * 4 * 8;
                double errorPercentage = dist / bitsSeenSoFar;
                if (errorPercentage > 0.43 && errorPercentage < 0.537) {
                    return Map.entry(true, dist);
                }
            }
        }
        return Map.entry(false, dist);
    }
    
    
    // checks +/- 150 around posInIndex.
    private Map.Entry<Integer, Integer> checkNearPos(long[] fingerprints,
                                                     int posInIndex,
                                                     int[] db,
                                                     int posInDb)
            throws IOException {
        
        int[] window;
        
        //First we handle the case of the window is INSIDE the DB
        int windowStart = posInDb - nearRange;
        int windowEnd = posInDb + nearRange + macro_sz;
        if (windowStart > 0 && windowEnd < db.length) {
            window = Arrays.copyOfRange(db,
                    /* From */ windowStart,
                    /* To*/ windowEnd);
            
        } else { //The windows was NOT inside the db
            
            //Start from the posInIndex, or 0 if this would be negative
            windowStart = Math.max(0, posInIndex - nearRange);
            
            //File size in integers (i.e. how many 4-byte chunks the file contains)
            int fileEnd = (int) (dbFileLength / Integer.BYTES);
            //Cap windowEnd to not extend beyound end of file
            windowEnd = Math.min(posInIndex + nearRange + macro_sz, fileEnd);
            
            try (DataInputStream fin = new DataInputStream(new FileInputStream(dbFilename))) {
                IOUtils.skipFully(fin, (long) Integer.BYTES * windowStart);
                window = Utils.bytesToInts(fin.readNBytes(Integer.BYTES * (windowEnd - windowStart)));
            }
            
        }
        
        //get best (distance,index)
        Map.Entry<Integer, Integer> fullCheckResult = fullCheck(fingerprints, window);
        
        Integer distance = fullCheckResult.getKey();
        int index = fullCheckResult.getValue() + posInIndex;
        
        return Map.entry(distance, index);
        
    }
    
    private static Map.Entry<Integer, Integer> fullCheck(long[] fingerprints, int[] window) {
        
        int bestDist = Integer.MAX_VALUE;
        int bestIdx = Integer.MAX_VALUE;
        if (window.length < macro_sz) {
            return Map.entry(bestDist, bestIdx);
        }
        for (int i = 0; i < window.length - macro_sz; ++i) {
            int dist = fullHamming(fingerprints, window, i);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx  = i;
            }
        }
        return Map.entry(bestDist, bestIdx);
    }
    
    private static int fullHamming(long[] fingerprints, int[] db, int start) {
        
        int dist = 0;
        for (int i = 0; i < macro_sz; ++i) {
            dist += matchFingerprints(fingerprints[i + fpSkip], db[i + start]);
        }
        return dist;
        
    }
    
    
    private static int matchFingerprints(long inputFingerprint, int archiveFinterprint) {
        long archiveFingerprint = Integer.toUnsignedLong(archiveFinterprint);
        long matchingBits = inputFingerprint ^ archiveFingerprint; //XOR
        return Long.bitCount(matchingBits);
    }
    
}
