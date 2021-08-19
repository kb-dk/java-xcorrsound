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

    public static final int CHUNK_LENGTH_DEFAULT = 25000; //  5 minutes
    public static final int CHUNK_OVERLAP_DEFAULT = 1000; // 10 seconds
    public static final boolean CACHE_PRINTS_DEFAULT = true;

    ScoreUtil.Scorer16 collapsedScorer = new ScoreUtil.ConstantScorer16(0.0); // Optional calculator of scores
    ScoreUtil.ScorerLong rawScorer = new ScoreUtil.ConstantScorerLong(0.0); // Optional calculator of scores

    ChunkMap16 chunkMap;
    Collapsor collapsor;
    PrintHandler printHandler;
    boolean cachefingerprints;

    /**
     * Default setup of CollapsedDiscovery.
     */
    public CollapsedDiscovery() {
        this(CHUNK_LENGTH_DEFAULT, CHUNK_OVERLAP_DEFAULT, Collapsor.COLLAPSE_STRATEGY_DEFAULT);
    }

    /**
     * Create a CollapsedDiscovery where fingerprints are collapsed using collapseStrategy and recordings are
     * searched in chunks of chunkLength fingerprints.
     * The {@link #printHandler} will be the default instantiation of {@link PrintHandler}.
     * @param chunkLength  the number of collapsed fingerprints in each chunk.
     * @param chunkOverlap the number of collapsed fingerprints to search beyond the given chunkLength.
     *                     This should at least be the number of fingerprints that is generated for a given search
     *                     snippet to ensure full match.
     * @param collapseStrategy how to reduce the 32 bit fingerprints to at most 16 bits.
     * @param cacheFingerprints if true, generated fingerprints are persistently cached.
     */
    public CollapsedDiscovery(int chunkLength, int chunkOverlap, Collapsor.COLLAPSE_STRATEGY collapseStrategy,
                              boolean cacheFingerprints) {
        this(chunkLength, chunkOverlap, collapseStrategy, cacheFingerprints, new PrintHandler());
    }

    /**
     * Create a CollapsedDiscovery where fingerprints are collapsed using collapseStrategy and recordings are
     * searched in chunks of chunkLength fingerprints.
     * @param chunkLength  the number of collapsed fingerprints in each chunk.
     * @param chunkOverlap the number of collapsed fingerprints to search beyond the given chunkLength.
     *                     This should at least be the number of fingerprints that is generated for a given search
     *                     snippet to ensure full match.
     * @param collapseStrategy how to reduce the 32 bit fingerprints to at most 16 bits.
     * @param cacheFingerprints if true, generated fingerprints are persistently cached.
     * @param printHandler used for generating missing fingerprints.
     */
    public CollapsedDiscovery(int chunkLength, int chunkOverlap, Collapsor.COLLAPSE_STRATEGY collapseStrategy,
                              boolean cacheFingerprints, PrintHandler printHandler) {
        collapsor = new Collapsor(collapseStrategy);
        chunkMap = new ChunkMap16(chunkLength, chunkOverlap);
        this.cachefingerprints = cacheFingerprints;
        this.printHandler = printHandler;
    }

    /**
     * Generate raw fingerprints for the given recording, collapse the fingerprints to 16 bit and update the lookup
     * structures with the result. After this the recording can be searched using {@link #findCandidates(String, int)}.
     *
     * Multiple additions of the same recording will result in duplicate entries.
     * The fingerprints will be cached on storage.
     * @param recordingPath full path to a recording in MP3 or WAV format.
     * @throws IOException if fingerprints for the recording could not be produced.
     */
    public void addRecording(String recordingPath) throws IOException {
        addRecording(toSound(recordingPath));
    }

    /**
     * Take a file with already generated fingerprints, generate collapsed prints from the stated range and update the
     * lookup structures with the result. After this the recording can be searched using {@link #findCandidates}.
     * @param recordingID  unique ID for the recording. This is often a file path, but that is not a requirement.
     * @param fingerprints pre-calculated fingerprints for the recording.
     * @param offset offset into the fingerprints for the recording.
     * @param length the number of fingerprints for the recording.
     * @throws IOException if the fingerprints for the sound could not be accessed.
     */
    public void addRecording(String recordingID, Path fingerprints, int offset, int length) throws IOException {
        addRecording(new Sound.PrintedSound(recordingID, fingerprints, offset, length));
    }

    /**
     * Take a Sound, get the raw fingerprints from {@link Sound#getRawPrints()}, collapses them with {@link #collapsor}
     * and update the lookup structures with the result.
     * After this the recording can be searched using {@link #findCandidates}.
     * @param sound the recording to index.
     * @throws IOException if the fingerprints for the sound could not be produced.
     */
    public void addRecording(Sound sound) throws IOException {
        char[] collapsed = collapsor.getCollapsed(sound.getRawPrints());
        chunkMap.addRecording(sound, collapsed);
        log.debug("Added " + sound);
    }

    /**
     * Convenience method for producing a sound using the given path, {@link #cachefingerprints} and
     * {@link #printHandler}.
     * @param recordingPath path to a sound file.
     * @return a Sound object, capable of providing fingerprints for the file.
     * @throws FileNotFoundException if the sound file could not be found.
     */
    public Sound toSound(String recordingPath) throws FileNotFoundException {
        return new Sound.PathSound(Path.of(recordingPath), cachefingerprints, printHandler);
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
        Sound snippet = toSound(snippetPath);
        char[] collapsed = collapsor.getCollapsed(snippet.getRawPrints());

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

        Sound snippet = new Sound.PathSound(Path.of(snippetPath), cachefingerprints, printHandler);
        long[] snipRaw = snippet.getRawPrints();
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
                long[] recRaw = hit.getRecording().getRawPrints(); // TODO: Utilize hit.getMatchAreaStartFingerprint()
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

}
