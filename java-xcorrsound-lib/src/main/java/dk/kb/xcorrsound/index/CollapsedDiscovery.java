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

import dk.kb.facade.XCorrSoundFacade;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/**
 * Uses collapsed fingerprints for discovery of sound snippets.
 *
 * Raw fingerprints with 32 significant bits are generated from recordings using
 * {@link XCorrSoundFacade#generateFingerPrintFromSoundFile}.
 * The raw fingerprints are collapsed to 16 bits and processed in chunks (default is 10 minute chunks).
 * For each possible collapsed fingerprint (there are 2^16 = 65536 unique collapsed prints) the chunks containing at
 * least one instance of the print is recorded.
 *
 * When calling {@link #findCandidates(String, int)}, collapsed fingerprints are calculated for the input snippet.
 * The chunks matching the snippet fingerprints are collected and the recording IDs for the chunks with the highest
 * amount of matches are returned.
 *
 * Note that this is a fairly coarse matching due to the collapsing step. For a higher quality result, post-processing
 * of the result set should be performed.
 *
 * Memory usage is 8KB/chunk of recording. With 10 minute chunks that is ~ 1MB/day of recording.
 *
 * Performance is O(#totalChunks). Currently processing is single threaded.
 * Shifting to a threaded model is fairly easy and the speedup will be near-linear, modulo CPU cache size limits.
 */
public class CollapsedDiscovery {
    private static final Logger log = LoggerFactory.getLogger(CollapsedDiscovery.class);

    public enum COLLAPSE_STRATEGY {
        /**
         * Collapses 32 bit down to 16 bit by ORing the bits pair wise.
         * This strategy has slower indexing than or_half_16, but is more representative if bits close to each other are
         * related to each other, e.g. if they represent spikes in frequency bands next to each other.
         */
        or_pairs_16,
        /**
         * Collapses 32 bit down to 16 bit by ORing the first 16 bits with the last 16 bits.
         * This strategy is very fast for indexing, but is a poor reduction if bits that are close to each other are
         * related. If bits in close proximity are not related, this strategy is preferable due to speed.
         */
        or_half_16
    };

    public static final COLLAPSE_STRATEGY COLLAPSE_STRATEGY_DEFAULT = COLLAPSE_STRATEGY.or_pairs_16;
    public static final int CHUNK_LENGTH_DEFAULT = 51600; // 10 minutes
    public static final int CHUNK_OVERLAP_DEFAULT = 1000; // 10 seconds

    private COLLAPSE_STRATEGY collapseStrategy;
    private ChunkMap16 chunkMap;

    public CollapsedDiscovery() {
        this(CHUNK_LENGTH_DEFAULT, CHUNK_OVERLAP_DEFAULT, COLLAPSE_STRATEGY_DEFAULT);
    }

    /**
     * Create a CollapsedDiscovery where fingerprints are collapsed using collapseStrategy and recordings are
     * searched in chunks of chunkLength fingerprints.
     * @param chunkLength  the number of collapsed fingerprints in each chunk.
     * @param chunkOverlap the number of collapsed fingerprints to search beyond the given chunkLength.
     *                     This should at least be the number of fingerprints that is generated for a given search
     *                     snippet to ensure full match.
     * @param collapseStrategy how to reduce the 32 bit fingerprints to at most 16 bits.
     */
    public CollapsedDiscovery(int chunkLength, int chunkOverlap, COLLAPSE_STRATEGY collapseStrategy) {
        this.collapseStrategy = collapseStrategy;
        chunkMap = new ChunkMap16(chunkLength, chunkOverlap);
    }

    /**
     * Generate raw fingerprints for the given recording, collapse the fingerprints using {@link #collapseStrategy}
     * and update the lookup structures with the result. After this the recording can be searched using
     * {@link #findCandidates(String, int)}.
     *
     * Multiple additions of the same recording will result in duplicate entries.
     * The fingerprints will be cached on storage.
     * @param recordingPath full path to a recording in MP3 or WAV format.
     * @throws IOException if fingerprints for the recording could not be generated.
     */
    public void addRecording(String recordingPath) throws IOException {
        long[] rawPrints = getRawPrints(Path.of(recordingPath));
        char[] collapsed = getCollapsed(rawPrints);
        chunkMap.addRecording(recordingPath, collapsed);
        log.debug("Added recording '" + recordingPath + "' with " + collapsed.length + " fingerprints");
    }

    private char[] getCollapsed(long[] rawPrints) {
        char[] collapsed;
        switch (collapseStrategy) {
            case or_pairs_16:
                collapsed = toChar(collapseTo16(rawPrints, fps ->
                        collapseEveryOther(fps, (first, second) -> first | second)));
                break;
            case or_half_16:
                collapsed = toChar(collapseTo16(rawPrints, fps ->
                        collapseHalf(fps, (first, second) -> first | second)));
                break;
            default: throw new UnsupportedOperationException(
                    "The COLLAPSE_STRATEGY '" + collapseStrategy + "' is currently not supported");
        }
        return collapsed;
    }

    /**
     * Uses the index structure for fast lookup for candidates. It is recommended to make a second pass of the returned
     * hits using Hamming distance or similar higher quality similarity calculation.
     * @param snippetPath the path to a sound snippet.
     *                    This should not result in more fingerprints than the chunkLength in {@link ChunkMap16}.
     * @param topX the number of hits to return.
     * @return the closes matches in descending order.
     * @throws IOException if it was not possible to generate fingerprints from snippetPath.
     */
    public List<ChunkCounter.Hit> findCandidates(String snippetPath, int topX) throws IOException {
        long[] rawPrints = getRawPrints(Path.of(snippetPath));
        char[] collapsed = getCollapsed(rawPrints);
        if (collapsed.length > chunkMap.getChunkOverlap()) {
            log.warn("Attempting search for snippet with {} fingerprints with a setup where the overlap between " +
                     "chunks is only {} fingerprints. This increases the probability of false negatives",
                     collapsed.length, chunkMap.getChunkOverlap());
        }
        return chunkMap.countMatches(collapsed).getTopMatches(topX);
    }

    /**
     * Process all rawPrints one at a time using the reducer.
     * @param rawPrints 32 bit significant fingerprint.
     * @param collapsor reduces a single 32 bit significant input to 16 significant bits.
     * @return rawPrints collapsed to 16 bit representations.
     */
    public static long[] collapseTo16(long[] rawPrints, LongUnaryOperator collapsor) {
        return Arrays.stream(rawPrints).map(collapsor).toArray();
    }

    /**
     * Processes bits in pairs by creating 2 16 bit values from 1 32 bit input (the upper 32 bits from the fingerprint
     * are discarded) by concatenating every other bit for value 1 and every other bit, offset by 1, for value 2.
     * After that the bitJoiner is applied to the two values and the result is returned.
     * @param fingerprint a fingerprint with 32 bit significant values.
     * @param bitJoiner   takes 2 bitsets (16 significant bits), joins them and returns the resulting bits.
     * @return
     */
    static long collapseEveryOther(long fingerprint, LongBiFunction bitJoiner) {
        long a = 0L;
        long b = 0L;
        for (int i = 0 ; i < 16 ; i++) {
            a |= ((fingerprint >>> (31 - i * 2)) & 0x1) << (15 - i);
            b |= ((fingerprint >>> (31 - i * 2 - 1)) & 0x1) << (15 - i);
        }
        return bitJoiner.apply(a, b);
    }

    /**
     * Processes bits in parallel matching first half and second half of the fingerprint.
     * Bits are represented at the last position of Longs.
     * @param fingerprint a fingerprint with 32 bit significant values.
     * @param bitJoiner   takes 2 bitsets (16 significant bits), joins them and returns the resulting bits.
     * @return
     */
    static long collapseHalf(long fingerprint, LongBiFunction bitJoiner) {
        long a = fingerprint >> 16;
        long b = fingerprint & 0xFFFF;
        return bitJoiner.apply(a, b);
    }

    /**
     * Convert all values to char (least significant 16 bits).
     * @param values a long array.
     * @return a char array of the same length as the input, containing the 16 least significant bit of all values.
     */
    private char[] toChar(long[] values) {
        char[] cs = new char[values.length];
        for (int i = 0 ; i < values.length ; i++) {
            cs[i] = (char)values[i];
        }
        return cs;
    }

    private long[] getRawPrints(final Path source) throws IOException {
        final Path processedSource = source.toString().endsWith("mp3") || source.toString().endsWith("wav") ?
                getRawPrintsPath(source.toString()) :
                source;
        if (!Files.exists(processedSource)) {
            try {
                generatePrints(source.toString());
            } catch (Exception e) {
                throw new IOException("Exception generating fingerprints for '" + source + "'", e);
            }
            if (!Files.exists(processedSource)) {
                throw new IOException("Unable to generate fingerprints for '" + source + "'");
            }
        }

        try {
            int numPrints = (int) (Files.size(source) / 4); // 32 bits/fingerprint
            long[] fingerprints = new long[numPrints];
            log.info("Loading {} fingerprints from '{}'", numPrints, source);
            try (DataInputStream of = new DataInputStream(IOUtils.buffer(FileUtils.openInputStream(source.toFile())))) {
                for (int i = 0; i < numPrints; i++) {
                    fingerprints[i] = Integer.toUnsignedLong(Integer.reverseBytes(of.readInt()));
                }
            }
            return fingerprints;
        } catch (Exception e) {
            throw new IOException("Failed loading fingerprints for '" + source + "'", e);
        }
    }

    void generatePrints(String soundFile) {
        final Path rawPrints = getRawPrintsPath(soundFile);
        if (Files.exists(rawPrints)) {
            log.debug("Fingerprints for '{}' already exists", rawPrints);
            return;
        }
        //String soundChunk = Thread.currentThread().getContextClassLoader().getResource("clip_P3_1400_1600_040806_001-java.mp3").getFile(); // 238 seconds
        long startTime = System.currentTimeMillis();
        log.info("Analysing '{}'", soundFile);
        long[] raw;
        try {
            raw = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed generating fingerprint for '" + soundFile + "'", e);
        }
        log.info("Calculated {} fingerprints in {} seconds",
                 raw.length, (System.currentTimeMillis()-startTime)/1000);
        try {
            store(raw, rawPrints);
        } catch (IOException e) {
            throw new RuntimeException("Failed storing " + raw.length + " prints for '" + soundFile + "'", e);
        }
    }

    private void store(long[] rawFingerprints, Path destination) throws IOException {
        log.info("Storing {} fingerprints to '{}'", rawFingerprints.length, destination);
        try (DataOutputStream of = new DataOutputStream(IOUtils.buffer(FileUtils.openOutputStream(destination.toFile(), false)))) {
            for (long fingerprint: rawFingerprints) {
                of.writeInt(Integer.reverseBytes((int) fingerprint)); // To keep it compatible with the c version(?)
            }
        }
    }

    private Path getRawPrintsPath(String soundFile) {
        final String base = soundFile.replaceAll("[.][^.]*$", "");
        return Path.of(base + ".rawPrints");
    }

}
