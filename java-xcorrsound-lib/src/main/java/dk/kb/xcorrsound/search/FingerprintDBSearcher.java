package dk.kb.xcorrsound.search;

import dk.kb.xcorrsound.FingerPrintDB;
import dk.kb.xcorrsound.Utils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

public class FingerprintDBSearcher extends FingerPrintDB implements AutoCloseable {
    public static final double DEFAULT_CRITERIA = 0.35 * (macro_sz * Integer.BYTES * 8);
    private static Logger log = LoggerFactory.getLogger(FingerprintDBSearcher.class);
    
    public void query_scan(String queryFilename, double criteria, Writer resultWriter)
            throws IOException, UnsupportedAudioFileException, InterruptedException {
    
        log.info("Starting query_scan for {}", queryFilename);
        // "queryFilename" is the name of the wav file that is our query.
        //ret.clear();
        //AudioFile a(queryFilename.c_str());
    
        //List<int16_t> samples;
    
        //a.getSamplesForChannel(0, samples);
    
        long[] fingerprints = this.fp_strategy.getFingerprintsForFile(queryFilename);
        query_scan(fingerprints, criteria, resultWriter);
    }
    
    public void query_scan(long[] fingerprints, double criteria, Writer resultWriter)
            throws IOException {
        int[] db = new int[1024 * 1024 + macro_sz];
        
        this.openForSearching();
        
        log.info("Starting search in {}", dbFilename);
        int end = macro_sz;
        int pos = 0;
        int prevMatchPos = Integer.MAX_VALUE;
        
        long bytesReadFromStream = 0;
        
        while (true) {
            System.arraycopy(db, db.length - macro_sz,
                             db, 0, macro_sz);
            
            int count = readDBBlob(db, bytesReadFromStream);
            bytesReadFromStream += (long) count * Integer.BYTES;
            //log.info("Reading next blob of {} bytes from db", read_bytes);
            if (count <= 0) {
                break;
            }
            
            end = count + macro_sz;
            
            
            for (int i = 0; i < end - macro_sz; i += 8, pos += 8) {
                if (pos - prevMatchPos < (this.fp_strategy.getSampleRate() / 64) && prevMatchPos != Integer.MAX_VALUE) {
                    continue;
                }
                
                
                Map.Entry<Boolean, Integer> hammingEarlyTerminateResult = hammingEarlyTerminate(fingerprints,
                                                                                                db,
                                                                                                i);
                
                boolean earlyTermination = hammingEarlyTerminateResult.getKey();
                int dist = hammingEarlyTerminateResult.getValue();
                
                if (earlyTermination) {
                    //log.debug("Stopping search at frame {} as noisy overlap ({})",i,dist);
                    continue;
                }
                
                log.debug("Found possible match at {}, examining further", i);
                Map.Entry<Integer, Integer> checkNearPosResult = checkNearPos(fingerprints,
                                                                              this.dbFilename,
                                                                              pos,
                                                                              db,
                                                                              i);
                dist = checkNearPosResult.getKey();
                
                if (dist < criteria) {
                    log.info("Found hit at {} with dist {}", pos, dist);
                    prevMatchPos = pos;
                    int finalPos = pos;
                    Integer hitFileStart = offsetsToFile.keySet()
                                                        .stream()
                                                        .filter(value -> value
                                                                         < finalPos) //Only those that END after this hit
                                                        .sorted(Comparator.reverseOrder()) //Lowest first.
                                                        .findFirst() //The lowest first must be our hit
                                                        .orElse(0);
                    
                    Integer hitEntry = offsetsToFile.keySet()
                                                    .stream()
                                                    .filter(value -> value
                                                                     >= finalPos) //Only those that END after this hit
                                                    .sorted() //Lowest first.
                                                    .findFirst() //The lowest first must be our hit
                                                    .get();
                    String filenameResult = offsetsToFile.get(hitEntry);
                    
                    int sampleInFile = pos - hitFileStart;
                    int advance = this.fp_strategy.getAdvance();
                    int sampleRate = this.fp_strategy.getSampleRate();
                    
                    String timestamp = formatTimestamp(sampleInFile * advance / sampleRate);
                    
                    String ss = "match in '" + filenameResult
                                + "' at " + timestamp
                                + " with distance " + dist + "\n";
                    resultWriter.write(ss);
                    
                }
                if (i + nearRange > end - macro_sz) {
                    int tmp = end - macro_sz - i;
                    i   = i + tmp;
                    pos = pos + tmp;
                } else {
                    i += nearRange;
                    pos += nearRange;
                }
            }
        }
        log.info("Completed search in {}", dbFilename);
    
    }
    
    
    private static String formatTimestamp(int seconds) {
        return DurationFormatUtils.formatDuration(seconds * 1000, "HH:mm:ss", true);
    }
    
    
    // percentage error. Break if in the 'noise zone'.
    // only check every now and then (i % 5)
    // and we must have a decent baseline, i.e. at least 10% through computation.
    // this is a heuristic to terminate early if we can see
    // there will not be a match here.
    private static Map.Entry<Boolean, Integer> hammingEarlyTerminate(long[] fingerprints, int[] db, int start) {
        
        int dist = 0;
        for (int i = 0; i < macro_sz; ++i) {
            
            long exponent = Integer.toUnsignedLong(db[i + start]);
            long x = fingerprints[i + fpSkip] ^ exponent;
            int cnt = Long.bitCount(x);
            dist += cnt;
            
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
    
    
    // checks +/- 100 around pos.
    private static Map.Entry<Integer, Integer> checkNearPos(long[] fingerprints,
                                                            String dbFilename,
                                                            int pos,
                                                            int[] db,
                                                            int posInDb)
            throws IOException {
        
        if (posInDb > nearRange && posInDb < db.length - nearRange - macro_sz) {
            int[] window = Arrays.copyOfRange(db, posInDb - nearRange, posInDb + nearRange + macro_sz);
            
            Map.Entry<Integer, Integer> fullCheckResult = fullCheck(fingerprints, window);
            
            return Map.entry(fullCheckResult.getKey(), fullCheckResult.getValue() + posInDb - nearRange);
        }
        
        int begin = 0;
        int end = pos + nearRange + macro_sz;
        if (pos > nearRange) {
            begin = pos - nearRange;
        }
        
        int fileEnd = (int) (Files.size(Path.of(dbFilename)));
        
        if (end * Integer.BYTES > fileEnd) {
            end = fileEnd / Integer.BYTES;
        }
        
        
        try (DataInputStream fin = new DataInputStream(new FileInputStream(dbFilename))) {
            
            IOUtils.skipFully(fin, (long) Integer.BYTES * begin);
            
            int[] window = Utils.bytesToInts(fin.readNBytes(Integer.BYTES * (end - begin)));
            
            Map.Entry<Integer, Integer> fullCheckResult = fullCheck(fingerprints, window);
            
            return Map.entry(fullCheckResult.getKey(), fullCheckResult.getValue() + begin);
        }
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
            long x = fingerprints[i + fpSkip] ^ Integer.toUnsignedLong(db[i + start]);
            int cnt = Long.bitCount(x);
            dist += cnt;
        }
        return dist;
        
    }
    
}
