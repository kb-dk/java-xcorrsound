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
 * Conceptually holds 16 bit fingerprints for files, providing match counting in overlapping chunks.
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
    final ChunkIDs[] chunkIDMap = new ChunkIDs[65536]; // chunkIDs with fingerprint as index
    final List<Sound> recordings = new ArrayList<>();

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
     * @param sound        a representation of a sound.
     * @param fingerprints reduced fingerprints (probably collapsed from 32 bit fingerprints) for the recording.
     */
    public void addRecording(Sound sound, char[] fingerprints) {
        // Ensure there is room in the structures
        int totalChunks = getNumChunks() + fingerprints.length / chunkLength + 1;
        if (chunkIDMap[0].extend(totalChunks)) {
            Arrays.stream(chunkIDMap).forEach(chunkIDs -> chunkIDs.extend(totalChunks));
        }

        // Iterate the chunks and update the bitmaps for all fingerprints in the chunk + overlap into the next chunk
        for (int chunkOrigo = 0 ; chunkOrigo < fingerprints.length ; chunkOrigo += chunkLength) {
            int max = Math.min(fingerprints.length, chunkOrigo + chunkLength + chunkOverlap);
            int chunkID = recordings.size();
            recordings.add(sound);
            for (int pos = chunkOrigo ; pos < max ; pos++) {
                int fingerprint = fingerprints[pos];
                chunkIDMap[fingerprint].set(chunkID);
            }
        }
    }

    /**
     * Search all chunks for the given fingerprints and count the number of times any chunk has been matched with any
     * fingerprint.

     * Note: This method does not support subsequent fine counting.
     * @param fingerprints fingerprints for a snippet.
     * @return a counter for all chunk matches.
     */
    public ChunkCounter countMatches(char[] fingerprints) {
        return countMatches(fingerprints, 0, fingerprints.length);
    }

    /**
     * Search all chunks for a subset of the given fingerprints and count the number of times any chunk has been
     * matched with any fingerprint from the subset.
     *
     * Note: This method does not support subsequent fine counting.
     * @param fingerprints fingerprints for a snippet.
     * @param areaStart the start of the area of fingerprints to search. Inclusive.
     * @param areaEnd the end of the area of fingerprints to search. Exclusive.
     *                If areaEnd larger than the number of fingerprints, it is set to the number of fingerprints.
     * @return a counter for all chunk matches.
     */
    public ChunkCounter countMatches(char[] fingerprints, int areaStart, int areaEnd) {
        return countMatches(null, 0, -1, -1, fingerprints, areaStart, areaEnd);
    }

    /**
     * Search all chunks for a subset of the given fingerprints and count the number of times any chunk has been
     * matched with any fingerprint from the subset.
     *
     * This method supports subsequent fine counting.
     * @param snippet the source of the collapsed fingerprints. Used for fine counting.
     * @param snippetChunkID the logical chunk into the snippet.
     * @param snippetOffset the offset, measured in fingerprints, into the snippet fingerprints. Used for fine counting.
     * @param snippetLength the number of snippet fingerprints used for counting. Used for fine counting.
     * @param fingerprints fingerprints for a snippet.
     * @param areaStart the start of the area of fingerprints to search. Inclusive. This is normally = snippetOffset.
     * @param areaEnd the end of the area of fingerprints to search. Exclusive. This is normally = snippetOffset + snippetLength.
     *                If areaEnd larger than the number of fingerprints, it is set to the number of fingerprints.
     * @return a counter for all chunk matches.
     */
    public ChunkCounter countMatches(Sound snippet, int snippetChunkID, int snippetOffset, int snippetLength,
                                     char[] fingerprints, int areaStart, int areaEnd) {
        ChunkCounter counter = new ChunkCounter(
                snippet, snippetChunkID, snippetLength, snippetOffset,
                getNumChunks(), recordings, chunkLength, chunkOverlap);
        areaEnd = Math.min(areaEnd, fingerprints.length);
        counter.setMaxPossibleMatches(areaEnd-areaStart);
        // Pseudo code
        //for (int index = areaStart ; index < areaEnd ; index++) {
        //    counter.add(getMatchingChunksIDs(fingerprints[index]));
        //}

        // Updating the ChunkCounter is a bit heavy so start by counting duplicate fingerprints
        int[] duplicates = new int[65536]; // char = 2 bytes = 65536 possibilities
        for (int index = areaStart ; index < areaEnd ; index++) {
            ++duplicates[fingerprints[index]];
        }
        // Iterate the duplicates and update the counter with the number of duplicates for any given fingerprint
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
        return recordings.size();
    }

    public int getChunkLength() {
        return chunkLength;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

}
