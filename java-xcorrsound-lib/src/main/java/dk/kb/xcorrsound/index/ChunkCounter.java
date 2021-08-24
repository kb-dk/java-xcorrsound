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

    private final List<Sound> recordings; // Needed for construction of Hits in {@link #getTopMatches}
    private final int[] counters;
    private final int numChunks;

    // Length & overlap needed for calculating absolute fingerprint offsets in the recordings.
    private final int chunkLength;
    private final int chunkOverlap;

    private int maxPossibleMatches = -1;

    public ChunkCounter(int numChunks, List<Sound> recordings, int chunkLength, int chunkOverlap) {
        this.numChunks = numChunks;
        this.counters = new int[numChunks];
        this.recordings = recordings;

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
     * For each chunk in chunks, adjust the matching position in counters with the given delta.
     * @param chunks marked chunks.
     * @param delta the amount to add to the counter.
     */
    public void add(ChunkIDs chunks, int delta) {
        if (delta != 0) {
            chunks.stream().forEach(chunkID -> counters[chunkID] += delta);
        }
    }

    /**
     * The maximum possible matches is the number of fingerprints in the snippet that is matched from.
     * @return the maximum number of matches.
     */
    public int getMaxPossibleMatches() {
        return maxPossibleMatches;
    }

    /**
     * The maximum possible matches is the number of fingerprints in the snippet that is matched from.
     * @return the maximum number of fingerprint matches.
     */
    public void setMaxPossibleMatches(int maxPossibleMatches) {
        this.maxPossibleMatches = maxPossibleMatches;
    }

    /**
     * Find the topX counter entries with the highest number of matches and translate them to recordings.
     * Note that the same recording can be returned multiple times for different chunks in the recording.
     * @param topX the number of chunks to locate.
     * @return topX matches in descending order.
     */
    public List<SoundHit> getTopMatches(int topX) {
        long[] pairs = new long[counters.length];
        for (int chunkID = 0 ; chunkID < counters.length ; chunkID++) {
            pairs[chunkID] = (((long)counters[chunkID]) << 32) | chunkID;
        }
        Arrays.sort(pairs);

        List<SoundHit> hits = new ArrayList<>(topX);
        for (int i = pairs.length-1 ; i >= 0 && i >= pairs.length-topX ; i--) {
            int chunkID = (int)(pairs[i] & 0xFFFF);
            int matches = (int)(pairs[i] >> 32);
            if (matches == 0) {
                break;
            }
            int recordChunkID = chunkIDtoRecordChunk(chunkID);
            int matchAreaStart = recordChunkID*chunkLength;
            int matchAreaEnd = matchAreaStart + chunkLength + chunkOverlap;
            // TODO: Extend with more info
            hits.add(new SoundHit(recordings.get(chunkID), recordChunkID,
                                  matchAreaStart, matchAreaEnd, matches, maxPossibleMatches));
        }
        return hits;
    }

    /**
     * @param globalChunkID the chunkID in the overall sequential chunk structure.
     * @return the chunkID in the record that corresponds to the globalChunkID.
     */
    private int chunkIDtoRecordChunk(int globalChunkID) {
        Sound sound = recordings.get(globalChunkID);
        int recordChunk = globalChunkID;
        while (recordChunk > 0 && recordings.get(recordChunk - 1).equals(sound)) {
            --recordChunk;
        }
        return globalChunkID-recordChunk;
    }
}
