package dk.kb.xcorrsound.index;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

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
class ScoreUtilTest {
    private static final Logger log = LoggerFactory.getLogger(ScoreUtilTest.class);
    // From CollapsedDiscoveryTest.chunkedFind
    // Source: last_xmas_youtube.mp3
    // Best match:
    // SoundHit{recording='PathSound(path='last_xmas/P3_1000_1200_901211_001.mp3')', score(c=[0.86, 01:36:48.0], r=[0.83, 01:36:48.0]), matches=197/325, recordingChunk=499, matchArea=01:36:38.4 [499000->500500], snippet=(PathSound(path='/home/te/projects/java-xcorrsound/java-xcorrsound-lib/target/test-classes/last_xmas_youtube.mp3'), chunk=0, offset=50, length=325)}
    // Worst match
    // SoundHit{recording='PathSound(path='last_xmas/P3_1400_1600_891224_001.mp3')', score(c=[0.72, 01:41:41.7], r=[0.54, 01:41:40.9]), matches=181/325, recordingChunk=524, matchArea=01:41:28.9 [524000->525500], snippet=(PathSound(path='/home/te/projects/java-xcorrsound/java-xcorrsound-lib/target/test-classes/last_xmas_youtube.mp3'), chunk=0, offset=50, length=325)}


    @Test
    void testKnownPoorMatch() throws IOException {
        PrintHandler printHandler = new PrintHandler();

        String snippetResource = "last_xmas_youtube.mp3";
        if (Thread.currentThread().getContextClassLoader().getResource(snippetResource) == null) {
            log.info("Unable to run test as test files are not available: " + snippetResource);
        }

        String snippetFile = TestHelper.getResource("last_xmas_youtube.mp3");
        long[] snippetRaw = printHandler.getRawPrints(Path.of(snippetFile), true);

        String X_ROOT = "/home/te/projects/java-xcorrsound/samples/last_xmas/";

        String bestRecording = X_ROOT + "P3_1000_1200_901211_001.mp3";
        long[] bestRecordingRaw = printHandler.getRawPrints(Path.of(bestRecording), true);

        String worstRecording = X_ROOT + "P3_1400_1600_891224_001.mp3";
        long[] worstRecordingRaw = printHandler.getRawPrints(Path.of(worstRecording), true);


        ScoreUtil.Match bestMatch = ScoreUtil.findBestMatchLong(snippetRaw, 0, 500, bestRecordingRaw, 0, bestRecordingRaw.length, 32, false);
        System.out.println("Best:  " + bestMatch);

        ScoreUtil.Match worstMatch = ScoreUtil.findBestMatchLong(snippetRaw, 0, 500, worstRecordingRaw, 0, worstRecordingRaw.length, 32, false);
        System.out.println("Worst: " + worstMatch + " seconds: " + worstMatch.offset/86);
    }

    @Test
    void testPreciseMatch() throws IOException {
        final int SNIP_START = 50; // Must be at least 1
        final int SNIP_END = 200;

        PrintHandler printHandler = new PrintHandler();
        final String SAMPLES = "/home/te/projects/java-xcorrsound/samples/";

        String perfectSnippet = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118_direct_clip_perfect_offset.wav";
        if (!Files.exists(Path.of(perfectSnippet))) {
            log.info("Unable to run test as sample files are not available: " + perfectSnippet);
        }
        long[] perfectRaw = printHandler.getRawPrints(Path.of(perfectSnippet), true);

        String recording = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118.wav";
        long[] recordingRaw = printHandler.getRawPrints(Path.of(recording), true);

        ScoreUtil.Match perfectMatch = ScoreUtil.findBestMatchLong(
                perfectRaw, SNIP_START, SNIP_END, recordingRaw, 0, recordingRaw.length, 32, false);
        assertTrue(perfectMatch.score > 0.9999,
                   "Perfect snippet should score ~1 but was " + perfectMatch);
        log.info("Perfect: " + perfectMatch);
        //TestHelper.dumpDiff(bassyRaw, SNIP_START, SNIP_END, recordingRaw, bassy.offset);
    }

    @Test
    void testImpreciseMatch() throws IOException {
        final int SNIP_START = 50;
        final int SNIP_END = 200;

        PrintHandler printHandler = new PrintHandler();
        final String SAMPLES = "/home/te/projects/java-xcorrsound/samples/";

        String bassySnippet = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118_bassy.wav";
        if (!Files.exists(Path.of(bassySnippet))) {
            log.info("Unable to run test as test files are not available: " + bassySnippet);
        }
        long[] bassyRaw = printHandler.getRawPrints(Path.of(bassySnippet), true);

        String recording = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118.wav";
        long[] recordingRaw = printHandler.getRawPrints(Path.of(recording), true);

        ScoreUtil.Match bassyMatch = ScoreUtil.findBestMatchLong(
                bassyRaw, SNIP_START, SNIP_END, recordingRaw, 0, recordingRaw.length, 32, false);
        assertTrue(bassyMatch.score < 0.9999,
                   "Imprecise snippet should score < 1 but was " + bassyMatch);
        assertTrue(bassyMatch.score > 0.9,
                   "Imprecise snippet should score > 0.9, as it is not THAT imprecise, but was " + bassyMatch);
        log.info("Bassy: " + bassyMatch);
        //TestHelper.dumpDiff(bassyRaw, SNIP_START, SNIP_END, recordingRaw, bassy.offset);
    }

    @Test
    void testRawScore() {
        final int RECORD_PRINTS = 10000;
        final int RECORD_SEARCH_OFFSET = 200;
        final int SNIPPET_OFFSET = 600;
        final int SNIPPET_PRINTS = 100;

        Random r = new Random(87); // Fixed seed = deterministic
        long[] record = new long[RECORD_PRINTS];
        for (int i = 0 ; i < RECORD_PRINTS ; i++) {
            record[i] = r.nextInt();
        }

        long[] perfectSnippet = new long[SNIPPET_PRINTS];
        System.arraycopy(record, SNIPPET_OFFSET, perfectSnippet, 0, SNIPPET_PRINTS);

        long[] twoOffSnippet = new long[SNIPPET_PRINTS];
        System.arraycopy(record, SNIPPET_OFFSET, twoOffSnippet, 0, SNIPPET_PRINTS);
        twoOffSnippet[SNIPPET_PRINTS/2]++;
        twoOffSnippet[SNIPPET_PRINTS/3]++;

        ScoreUtil.Match perfectMatch = ScoreUtil.matchingBits32NonExhaustive(
                perfectSnippet, 0, SNIPPET_PRINTS, record, RECORD_SEARCH_OFFSET, RECORD_PRINTS);
        assertTrue(perfectMatch.score > 0.9999,
                   "The perfect match should be perfect but was " + perfectMatch);
        assertEquals(perfectMatch.offset, SNIPPET_OFFSET,
                     "The perfect match should have the expected offset");

        ScoreUtil.Match twoOffMatch = ScoreUtil.matchingBits32NonExhaustive(
                twoOffSnippet, 0, SNIPPET_PRINTS, record, RECORD_SEARCH_OFFSET, RECORD_PRINTS);
        assertFalse(twoOffMatch.score > 0.9999,
                   "The two fingerprints off match should not be perfect but was " + perfectMatch);
        assertEquals(twoOffMatch.offset, SNIPPET_OFFSET, // Yes, same as perfect. It is just a small change
                     "The two fingerprints off match should have the expected offset");
    }

    @Test
    void testLocateSampleOffset() throws IOException {
        final String SAMPLES = "/home/te/projects/java-xcorrsound/samples/";
        final int SNIP_START = 0;
        final int SNIP_END = 500;

        PrintHandler ph = new PrintHandler();

        String recording = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118.wav";
        if (!Files.exists(Path.of(recording))) {
            log.info("Unable to run test as test files are not available: " + recording);
        }
        final long[] recordingRaw = ph.getRawPrints(Path.of(recording), true);
           // 2021-08-30 16:35:16 [main] INFO  d.k.x.search.FingerprintDBSearcher(FingerprintDBSearcher.java:97) - Found hit at 103640 with dist 0
        String exactSnippet = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118_direct_clip_perfect_offset.wav";
        ScoreUtil.Match best = new ScoreUtil.Match(0, 0);
        ScoreUtil.Match worst = new ScoreUtil.Match(0, 2);
        int bestSampleOffset = 0;
        int worstSampleOffset = 0;
        for (int sampleOffset = 0 ; sampleOffset < 64 ; sampleOffset++) {
            long[] exactRaw = ph.generatePrints(Path.of(exactSnippet), sampleOffset);

            ScoreUtil.Match exact = ScoreUtil.findBestMatchLong(exactRaw, SNIP_START, SNIP_END, recordingRaw, 0, recordingRaw.length, 32, false);
            System.out.println("Exact: " + exact);
            if (worst.score > exact.score) {
                worst = exact;
                worstSampleOffset = sampleOffset;
            }
            if (best.score < exact.score) {
                best = exact;
                bestSampleOffset = sampleOffset;
            }
//            TestHelper.dumpDiff(exactRaw, SNIP_START, SNIP_END, recordingRaw, exact.offset);
        }
        System.out.println("Worst match: " + worst + " (sampleOffset=" + worstSampleOffset + ")");
        System.out.println("Best match:  " + best + " (sampleOffset=" + bestSampleOffset + ")");
    }


}