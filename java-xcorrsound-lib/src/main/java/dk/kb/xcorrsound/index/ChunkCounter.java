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

/**
 * Keeps track of the number of matches for the individual chunks.
 */
public class ChunkCounter {
    private final List<String> recordIDs; // Needed for construction of Hits in {@link #getTopMatches}
    private final int[] counters;

    public ChunkCounter(int numChunks, List<String> recordIDs) {
        this.counters = new int[numChunks];
        this.recordIDs = recordIDs;
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
            hits.add(new Hit(recordIDs.get(chunkID), chunkIDtoRecordChunk(chunkID), matches));
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

        public Hit(String recordingID, int chunkID, int matches) {
            this.recordingID = recordingID;
            this.recordingChunkID = chunkID;
            this.matches = matches;
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
         */
        public int getRecordingChunkID() {
            return recordingChunkID;
        }

        /**
         * @return the number of matching fingerprints for the chunk that this Hit represents.
         */
        public int getMatches() {
            return matches;
        }
    }
}
