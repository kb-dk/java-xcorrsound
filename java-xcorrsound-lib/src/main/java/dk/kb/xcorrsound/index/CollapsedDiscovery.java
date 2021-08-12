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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 * Memory usage is 8KB/chunk of recording. With 10 minute chunks that is ~1MB/day of recording.
 *
 * Performance is O(#totalChunks). Currently processing is single threaded.
 * Shifting to a threaded model is fairly easy and the speedup will be near-linear, modulo CPU cache size limits.
 */
public class CollapsedDiscovery {
    private static final Logger log = LoggerFactory.getLogger(CollapsedDiscovery.class);

    ;

    public static final int CHUNK_LENGTH_DEFAULT = 51600; // 10 minutes
    public static final int CHUNK_OVERLAP_DEFAULT = 1000; // 10 seconds

    ScoreUtil.Scorer16 collapsedScorer = new ScoreUtil.ConstantScorer16(0.0); // Optional calculator of scores
    ScoreUtil.ScorerLong rawScorer = new ScoreUtil.ConstantScorerLong(0.0); // Optional calculator of scores

    ChunkMap16 chunkMap;
    Collapsor collapsor;

    public CollapsedDiscovery() {
        this(CHUNK_LENGTH_DEFAULT, CHUNK_OVERLAP_DEFAULT, Collapsor.COLLAPSE_STRATEGY_DEFAULT);
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
    public CollapsedDiscovery(int chunkLength, int chunkOverlap, Collapsor.COLLAPSE_STRATEGY collapseStrategy) {
        collapsor = new Collapsor(collapseStrategy);
        chunkMap = new ChunkMap16(chunkLength, chunkOverlap);
    }

    /**
     * Generate raw fingerprints for the given recording, collapse the fingerprints to 16 bit and update the lookup
     * structures with the result. After this the recording can be searched using {@link #findCandidates(String, int)}.
     *
     * Multiple additions of the same recording will result in duplicate entries.
     * The fingerprints will be cached on storage.
     * @param recordingPath full path to a recording in MP3 or WAV format.
     * @throws IOException if fingerprints for the recording could not be generated.
     */
    public void addRecording(String recordingPath) throws IOException {
        long[] rawPrints = getRawPrints(Path.of(recordingPath));
        char[] collapsed = collapsor.getCollapsed(rawPrints);
        chunkMap.addRecording(recordingPath, collapsed);
        log.debug("Added recording '" + recordingPath + "' with " + collapsed.length + " fingerprints");
    }

    public ScoreUtil.Scorer16 getCollapsedScorer() {
        return collapsedScorer;
    }

    public CollapsedDiscovery setCollapsedScorer(ScoreUtil.Scorer16 scorer) {
        this.collapsedScorer = scorer;
        return this;
    }

    public ScoreUtil.ScorerLong getRawScorer() {
        return rawScorer;
    }

    public CollapsedDiscovery setRawScorer(ScoreUtil.ScorerLong rawScorer) {
        this.rawScorer = rawScorer;
        return this;
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
        char[] collapsed = getCollapsed(Path.of(snippetPath));

        if (collapsed.length > chunkMap.getChunkOverlap()) {
            log.warn("Attempting search for snippet with {} fingerprints with a setup where the overlap between " +
                     "chunks is only {} fingerprints. This increases the probability of false negatives",
                     collapsed.length, chunkMap.getChunkOverlap());
        }
        return chunkMap.countMatches(collapsed).getTopMatches(topX);
    }

    /**
     * Uses the index structure for fast lookup for candidates. It is recommended to make a second pass of the returned
     * hits using Hamming distance or similar higher quality similarity calculation.
     *
     * The input snippet is fingerprinted and the fingerprints divided into chunks before searching.
     * @param snippetPath the path to a sound snippet.
     *                    This should not result in more fingerprints than the chunkLength in {@link ChunkMap16}.
     * @param topX the number of hits to return.
     * @param preSkip the number of fingerprint to skip at the start of the fingerprints from the snipper. Inclusive.
     * @param postSkip the number of fingerprint to skip at the end of the fingerprints from the snipper. Exclusive.
     * @param chunkLength the chunk length for
     * @return the closes matches in descending order for each chunk of snippet fingerprints, starting from chunk 0.
     * @throws IOException if it was not possible to generate fingerprints from snippetPath.
     */
    public List<List<ChunkCounter.Hit>> findCandidates(
            String snippetPath, int topX, int preSkip, int postSkip, int chunkLength, int chunkOverlap)
            throws IOException {
        long[] snipRaw = getRawPrints(Path.of(snippetPath));
        char[] snipCollapsed = collapsor.getCollapsed((snipRaw));

        if (preSkip + postSkip >= snipCollapsed.length) {
            log.warn("preSkip={} + postSkip={} >= numSnippets={}. Empty result list returned",
                     preSkip, postSkip, snipCollapsed.length);
            return Collections.emptyList();
        }

        int numChunks = (snipCollapsed.length-preSkip-postSkip) / chunkLength;
        if (numChunks*chunkLength < (snipCollapsed.length-preSkip-postSkip)) {
            numChunks++;
        }

        List<List<ChunkCounter.Hit>> chunkResults = new ArrayList<>(numChunks);
        for (int chunk = 0 ; chunk < numChunks ; chunk++) {
            int snipStart = preSkip + chunk*chunkLength;
            int snipEnd = Math.min(snipStart + chunkLength + chunkOverlap, snipCollapsed.length-postSkip);
            List<ChunkCounter.Hit> hits = chunkMap.countMatches(snipCollapsed, snipStart, snipEnd).getTopMatches(topX);
            for (ChunkCounter.Hit hit: hits) {
                long[] recRaw = getRawPrints(Path.of(hit.getRecordingID()));
                char[] recCollapsed = collapsor.getCollapsed((recRaw));
                hit.setCollapsedScore(collapsedScorer.score(
                        snipCollapsed, snipStart, snipEnd, recCollapsed, hit.getMatchAreaStartFingerprint(), hit.getMatchAreaEndFingerprint()));
                hit.setRawScore(rawScorer.score(
                        snipRaw, snipStart, snipEnd, recRaw, hit.getMatchAreaStartFingerprint(), hit.getMatchAreaEndFingerprint()));
            }
            chunkResults.add(hits);
        }
        return chunkResults;
/*        if (collapsed.length > chunkMap.getChunkOverlap()) {
            log.warn("Attempting search for snippet with {} fingerprints with a setup where the overlap between " +
                     "chunks is only {} fingerprints. This increases the probability of false negatives",
                     collapsed.length, chunkMap.getChunkOverlap());
        }*/
    }

    /**
     * Processes the given file using {@link XCorrSoundFacade#generateFingerPrintFromSoundFile} and returns the result.
     *
     * The generated fingerprints are cached for subsequent calls.
     * @param recording a sound file.
     * @return fingerprints for the recording.
     * @throws IOException if the file could not be loaded or processed.
     */
    long[] getRawPrints(final Path recording) throws IOException {
        if (!Files.exists(recording)) {
            throw new FileNotFoundException("The file '" + recording + "' does not exist");
        }
        final Path processedSource = recording.toString().endsWith("mp3") || recording.toString().endsWith("wav") ?
                getRawPrintsPath(recording.toString()) :
                recording;
        if (!Files.exists(processedSource)) {
            try {
                generatePrints(recording.toString());
            } catch (Exception e) {
                throw new IOException("Exception generating fingerprints for '" + recording + "'", e);
            }
            if (!Files.exists(processedSource)) {
                throw new IOException("Unable to generate fingerprints for '" + recording + "'");
            }
        }

        try {
            int numPrints = (int) (Files.size(processedSource) / 4); // 32 bits/fingerprint
            long[] fingerprints = new long[numPrints];
            log.debug("Loading {} fingerprints from '{}'", numPrints, processedSource);
            try (DataInputStream of = new DataInputStream(IOUtils.buffer(
                    FileUtils.openInputStream(processedSource.toFile())))) {
                for (int i = 0; i < numPrints; i++) {
                    fingerprints[i] = Integer.toUnsignedLong(Integer.reverseBytes(of.readInt()));
                }
            }
            return fingerprints;
        } catch (Exception e) {
            throw new IOException("Failed loading fingerprints for '" + recording + "'", e);
        }
    }


    /**
     * Extracts raw fingerprints using {@link #getRawPrints(Path)} and collapses them using {@link #collapsor}.
     * @param recording a sound file.
     * @return collapsed fingerprints for the recording.
     * @throws IOException
     */
    char[] getCollapsed(final Path recording) throws IOException {
        long[] rawPrints = getRawPrints(recording);
        return collapsor.getCollapsed(rawPrints);
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
