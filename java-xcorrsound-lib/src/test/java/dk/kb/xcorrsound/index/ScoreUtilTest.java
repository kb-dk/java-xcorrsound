package dk.kb.xcorrsound.index;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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

        String snippetFile = getResource("last_xmas_youtube.mp3");
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
        final int SNIP_START = 0;
        final int SNIP_END = 50;


        PrintHandler printHandler = new PrintHandler();
        final String SAMPLES = "/home/te/projects/java-xcorrsound/samples/";

        String exactSnippet = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118_bassy.wav";
        if (!Files.exists(Path.of(exactSnippet))) {
            log.info("Unable to run test as test files are not available: " + SAMPLES);
        }
        long[] exactRaw = printHandler.getRawPrints(Path.of(exactSnippet), true);

        String bassySnippet = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118_direct_clip.wav";
        long[] bassyRaw = printHandler.getRawPrints(Path.of(bassySnippet), true);

        String recording = SAMPLES + "b27401cb-d635-4b7f-bcf9-bf93389e2118.wav";
        long[] recordingRaw = printHandler.getRawPrints(Path.of(recording), true);


        ScoreUtil.Match exact = ScoreUtil.findBestMatchLong(exactRaw, SNIP_START, SNIP_END, recordingRaw, 0, recordingRaw.length, 32, false);
        System.out.println("Exact: " + exact);
        dumpDiff(exactRaw, SNIP_START, SNIP_END, recordingRaw, exact.offset);

        ScoreUtil.Match bassy = ScoreUtil.findBestMatchLong(bassyRaw, SNIP_START, SNIP_END, recordingRaw, 0, recordingRaw.length, 32, false);
        System.out.println("Bassy: " + bassy);
    }

    private void dumpDiff(long[] snippetRaw, int snipStart, int snipEnd, long[] recordingRaw, int recordingStart) {
        int diffs = 0;
        int diffBits = 0;
        for (int i = 0 ; i < snipEnd-snipStart ; i++) {
            if (snippetRaw[snipStart+i] != recordingRaw[recordingStart+i]) {
                int nonMatching = Long.bitCount(snippetRaw[snipStart + i] ^ recordingRaw[recordingStart + i]);
                System.out.printf(
                        Locale.ROOT, "%3d: %s - %s (%2d)\n",
                        i, bin32(snippetRaw[snipStart + i]), bin32(recordingRaw[recordingStart + i]), nonMatching);
                diffBits += nonMatching;
            }
        }
        System.out.printf(Locale.ROOT, "Total diffs: %d/%d, bitDiffs= %d/%d, score=%f\n",
                          diffs, snipEnd-snipStart, diffBits, (snipEnd-snipStart)*32,
                          1.0-(diffBits*1.0/((snipEnd-snipStart)*32)));
    }

    private String bin32(long value) {
        String binary = Long.toBinaryString(value);
        while (binary.length() < 32) {
            binary = "0" + binary;
        }
        return binary;
    }

    String getResource(String resource) {
        return Files.exists(Path.of(resource)) ? resource :
                Thread.currentThread().getContextClassLoader().getResource(resource).getFile();
    }

}