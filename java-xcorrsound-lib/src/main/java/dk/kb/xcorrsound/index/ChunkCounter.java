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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            hits.add(new Hit(recordIDs.get(chunkID), chunkID, matches));
        }
        return hits;
    }

    public class Hit {
        private final String recordingID;
        // TODO: Add chunkID relative to the recording, instead of the absolute chunkID
        private final int absoluteChunkID;
        private final int matches;

        public Hit(String recordingID, int chunkID, int matches) {
            this.recordingID = recordingID;
            this.absoluteChunkID = chunkID;
            this.matches = matches;
        }

        public String getRecordingID() {
            return recordingID;
        }

        public int getAbsoluteChunkID() {
            return absoluteChunkID;
        }

        public int getMatches() {
            return matches;
        }
    }
}
