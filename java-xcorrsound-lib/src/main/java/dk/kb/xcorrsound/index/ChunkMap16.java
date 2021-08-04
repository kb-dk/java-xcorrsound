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
 * Holds 16 bit fingerprints for files, providing access as overlapping chunks.
 *
 * A given recording is added as an array of 16 bit integers (chars) of arbitrary (max 2 billion) length.
 * Internally the recording is seen as chunks of length {@link #chunkLength}, with chunk-IDs being assigned sequentially
 * starting from 0.
 *
 * The method {@link #getMatchingChunksIDs(char)} returns a bitmap of the chunks containing at least one instance of the
 * given fingerprint. The bits in the bitmap corresponds to the chunks-IDs.
 *
 */
public class ChunkMap16 {
    private static final Logger log = LoggerFactory.getLogger(ChunkMap16.class);

    public static final int DEFAULT_INITIAL_CHUNK_COUNT = 1000;

    private final int chunkLength;
    private final int chunkOverlap;

    // Maps from fingerprint to chunks containing the fingerprint
    private final ChunkIDs[] chunkIDMap = new ChunkIDs[65536]; // chunkIDs with fingerprint as index
    private final List<String> recordIDs = new ArrayList<>(); // recordIDs with chunksID as index

    public ChunkMap16(int chunkLength, int chunkOverlap) {
        this(chunkLength, chunkOverlap, DEFAULT_INITIAL_CHUNK_COUNT);
    }
    public ChunkMap16(int chunkLength, int chunkOverlap, int initialChunkCount) {
        this.chunkLength = chunkLength;
        this.chunkOverlap = chunkOverlap;
        for (int i = 0 ; i < initialChunkCount ; i++) {
            chunkIDMap[i] = new ChunkIDs(initialChunkCount);
        }
    }

    /**
     * Add the given record, represented as an ID, with corresponding fingerprints.
     * @param recordingID  an unique ID for a recording.
     * @param fingerprints fingerprints (probably collapsed from 32 bit fingerprints) for the recording.
     */
    public void addRecording(String recordingID, char[] fingerprints) {
        recordingID = recordingID.intern();

        // Ensure there is room in the structures
        int totalChunks = getNumChunks() +  fingerprints.length / chunkLength + 1;
        if (chunkIDMap[0].extend(totalChunks)) {
            Arrays.stream(chunkIDMap).forEach(chunkIDs -> chunkIDs.extend(totalChunks));
        }

        // Iterate the chunks and update the bitmaps for all fingerprints in the chunk + overlap into the next chunk
        for (int chunkOrigo = 0 ; chunkOrigo < fingerprints.length ; chunkOrigo += chunkLength) {
            int max = Math.min(fingerprints.length, chunkOrigo + chunkLength + chunkOverlap);
            int chunkID = recordIDs.size();
            recordIDs.add(recordingID);
            for (int pos = chunkOrigo ; pos < max ; pos++) {
                int fingerprint = fingerprints[pos];
                chunkIDMap[fingerprint].set(chunkID);
            }
        }
    }

    public ChunkIDs getMatchingChunksIDs(char fingerprint) {
        return chunkIDMap[fingerprint];
    }

    /**
     * Search all chunks for the given fingerprints and count the number of times any chunk has been matched with any
     * fingerprint.
     * @param fingerprints fingerprints for a snippet.
     * @return a counter for all chunk matches.
     */
    public ChunkCounter countMatches(char[] fingerprints) {
        ChunkCounter counter = new ChunkCounter(chunkIDMap[0].getBitmap().length, recordIDs);
        for (char fingerprint: fingerprints) {
            counter.add(getMatchingChunksIDs(fingerprint));
        }
        return counter;
    }

    public int getNumChunks() {
        return recordIDs.size();
    }
}
