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
 * This implementation is not thread safe.
 * TODO: Make the implementation thread safe so that recordings can be added while lookups are performed
 * FIXME: The last chunk for a recording might be very small, resulting is a high relative match rate
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
        for (int i = 0 ; i < 65536 ; i++) {
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
        int totalChunks = getNumChunks() + fingerprints.length / chunkLength + 1;
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

    /**
     * Search all chunks for the given fingerprints and count the number of times any chunk has been matched with any
     * fingerprint.
     * @param fingerprints fingerprints for a snippet.
     * @return a counter for all chunk matches.
     */
    public ChunkCounter countMatches(char[] fingerprints) {
        return countMatches(fingerprints, 0, fingerprints.length);
    }

    /**
     * Search all chunks for a subset of the given fingerprints and count the number of times any chunk has been
     * matched with any fingerprint from the subset.
     * @param fingerprints fingerprints for a snippet.
     * @param areaStart the start of the area of fingerprints to search. Inclusive.
     * @param areaEnd the end of the area of fingerprints to search. Exclusive.
     *                If areaEnd larger than the number of fingerprints, it is set to the number of fingerprints.
     * @return a counter for all chunk matches.
     */
    public ChunkCounter countMatches(char[] fingerprints, int areaStart, int areaEnd) {
        ChunkCounter counter = new ChunkCounter(getNumChunks(), recordIDs, chunkLength, chunkOverlap);
        areaEnd = Math.min(areaEnd, fingerprints.length);

        // Pseudo code
        //for (int index = areaStart ; index < areaEnd ; index++) {
        //    counter.add(getMatchingChunksIDs(fingerprints[index]));
        //}

        // Updating the ChunkCounter is a bit heavy so start by counting duplicate fingerprints
        int[] duplicates = new int[65536]; // char = 2 bytes = 65536 possibilities
        for (int index = areaStart ; index < areaEnd ; index++) {
            ++duplicates[fingerprints[index]];
        }
        // Iterate the dulicates and update the counter with the number of duplicates for any given fingerprint
        int count;
        for (int fingerprint = 0 ; fingerprint < duplicates.length ; fingerprint++) {
            if ((count = duplicates[fingerprint]) != 0) {
                counter.add(getMatchingChunksIDs((char) fingerprint), count);
            }
        }

        return counter;
    }

    public ChunkIDs getMatchingChunksIDs(char fingerprint) {
        return chunkIDMap[fingerprint];
    }


    public int getNumChunks() {
        return recordIDs.size();
    }

    public int getChunkLength() {
        return chunkLength;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }
}
