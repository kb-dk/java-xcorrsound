/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.kb.xcorrsound.index;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Keeps track of the number of matches for the individual chunks.
 *
 * This implementation is not thread safe.
 */
public class ChunkCounter {

    public static final double MS_PER_FINGERPRINT = 11.62; // TODO: Get the exact value

    private final List<String> recordIDs; // Needed for construction of Hits in {@link #getTopMatches}
    private final int[] counters;
    private final int numChunks;

    // Length & overlap needed for calculating absolute fingerprint offsets in the recordings.
    private final int chunkLength;
    private final int chunkOverlap;

    public ChunkCounter(int numChunks, List<String> recordIDs, int chunkLength, int chunkOverlap) {
        this.numChunks = numChunks;
        this.counters = new int[numChunks];
        this.recordIDs = recordIDs;

        this.chunkLength = chunkLength;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * For each chunk in chunks, increment the matching position in counters with 1.
     * @param chunks marked chunks.
     */
    public void add(ChunkIDs chunks) {
        chunks.stream().forEach(chunkID -> ++counters[chunkID]);
    }

    /**
     * Find the topX counter entries with the highest number and translate the to recordings.
     * Note that the same recording can be returned multiple times; for different chunks in the recording.
     * @param topX the number of chunks to locate.
     * @return topX matches in descending order.
     */
    public List<Hit> getTopMatches(int topX) {
        long[] pairs = new long[counters.length];
        for (int chunkID = 0 ; chunkID < counters.length ; chunkID++) {
            pairs[chunkID] = (((long)counters[chunkID]) << 32) | chunkID;
        }
        Arrays.sort(pairs);

        List<Hit> hits = new ArrayList<>(topX);
        for (int i = pairs.length-1 ; i >= 0 && i >= pairs.length-topX ; i--) {
            int chunkID = (int)(pairs[i] & 0xFFFF);
            int matches = (int)(pairs[i] >> 32);
            if (matches == 0) {
                break;
            }
            int recordChunkID = chunkIDtoRecordChunk(chunkID);
            int matchAreaStart = recordChunkID*chunkLength;
            int matchAreaEnd = matchAreaStart + chunkLength + chunkOverlap;
            hits.add(new Hit(recordIDs.get(chunkID), recordChunkID, matchAreaStart, matchAreaEnd, matches));
        }
        return hits;
    }

    /**
     * @param globalChunkID the chunkID in the overall sequential chunk structure.
     * @return the chunkID in the record that corresponds to the globalChunkID.
     */
    private int chunkIDtoRecordChunk(int globalChunkID) {
        String recordID = recordIDs.get(globalChunkID);
        int recordChunk = globalChunkID;
        while (recordChunk > 0 && recordIDs.get(recordChunk-1).equals(recordID)) {
            --recordChunk;
        }
        return globalChunkID-recordChunk;
    }

    public static class Hit {
        private final String recordingID;
        private final int recordingChunkID;
        private final int matches;
        private final int matchAreaStartFingerprint; // Inclusive
        private final int matchAreaEndFingerprint; // Exclusive
        private final double matchAreaStartSeconds;
        private double score = 0.0; // Optional score

        public Hit(String recordingID, int chunkID,
                   int matchAreaStartFingerprint, int matchAreaEndFingerprint, int matches) {
            this.recordingID = recordingID;
            this.recordingChunkID = chunkID;
            this.matches = matches;
            this.matchAreaStartFingerprint = matchAreaStartFingerprint;
            this.matchAreaEndFingerprint = matchAreaEndFingerprint;
            this.matchAreaStartSeconds = matchAreaStartFingerprint * ChunkCounter.MS_PER_FINGERPRINT / 1000.0;
        }

        /**
         * @return the recording ID, which should be the full file path.
         */
        public String getRecordingID() {
            return recordingID;
        }

        /**
         * @return the chunk in the recording that caused the match.
         *         Chunks has length {@link ChunkMap16#getChunkLength()}.
         *          Note that the matching area has size chunkLength + {@link ChunkMap16#getChunkOverlap()}.
         * @see #getMatchAreaStartFingerprint()
         * @see #getMatchAreaEndFingerprint()
         */
        public int getRecordingChunkID() {
            return recordingChunkID;
        }

        /**
         * @return the start of the range of fingerprints in the recording where the matched occurred. Inclusive.
         */
        public int getMatchAreaStartFingerprint() {
            return matchAreaStartFingerprint;
        }

        /**
         * @return the end of the range of fingerprints in the recording where the matched occurred. Exclusive.
         */
        public int getMatchAreaEndFingerprint() {
            return matchAreaEndFingerprint;
        }

        public double getMatchAreaStartSeconds() {
            return matchAreaStartSeconds;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }

        private String getMatchAreaStartHumanTime() {
            double seconds = matchAreaStartSeconds;
            int hours = (int) (seconds / (60 * 60));
            seconds -= hours * 60 * 60;
            int minutes = (int) (seconds / 60);
            seconds -= minutes * 60;
            return String.format(Locale.ROOT, "%03d:%02d:%04.1f", hours, minutes, seconds);
        }

        /**
         * @return the number of matching fingerprints for the chunk that this Hit represents.
         */
        public int getMatches() {
            return matches;
        }

        @Override
        public String toString() {
            return "Hit{" +
                   "recordingID='" + recordingID + '\'' +
                   ", recordingChunkID=" + recordingChunkID +
                   ", matchAreaStart=" + getMatchAreaStartHumanTime() +
                   ", matchAreaStartFP=" + matchAreaStartFingerprint +
                   ", matchAreaEndFP=" + matchAreaEndFingerprint +
                   ", matches=" + matches +
                   ", score=" + score +
                   '}';
        }

    }
}