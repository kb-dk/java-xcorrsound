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
package dk.kb.xcorrsound;

import dk.kb.facade.XCorrSoundFacade;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
public class FingerprintCollapseTest {
    private static Logger log = LoggerFactory.getLogger(FingerprintStrategyIsmir.class);

    final static String X_ROOT = "/home/te/projects/java-xcorrsound/samples/last_xmas";

    //Files that has match using the slow search algorithm and all are correct
    final static String[] X_MATCHING = new String[] { // Under X_ROOT
            "P3_0600_0800_890110_001.mp3",
            "P3_0600_0800_891222_001.mp3",
            "P3_1000_1200_901211_001.mp3"};
            /*"P3_1400_1600_891205_001.mp3",
            "P3_0600_0800_891211_001.mp3",
            "P3_0600_0800_901225_001.mp3",
            "P3_1200_1400_891206_001.mp3",
            "P3_1400_1600_891224_001.mp3"
    };        */
    //"clip_P3_1400_1600_040806_001-java.mp3",
    final static String X_SOURCE_HQ = "last_xmas_chunk1.mp3"; // In resources
    final static String X_SOURCE_LQ = "last_xmas_youtube.mp3"; // In resources


    @Test
    void countMatches() throws Exception {
        generateAllPrints();
        Map<String, long[]> raws = loadAllPrints();
        Map<String, char[]> cpOR = loadAllCollapsePairOR();

        compareMatches(raws, cpOR, X_SOURCE_HQ);
        compareMatches(raws, cpOR, X_SOURCE_LQ);
    }

    private void compareMatches(Map<String, long[]> recRaw, Map<String, char[]> recPOR, String source) {
        long[] hq = load(Path.of(getResource(source)));
        log.info("Raw matches for '{}'", source);
        recRaw.forEach((key, value) -> {
            System.out.println(key + ": " + countMatching(hq, value));
        });
        char[] hqPOR = collapsePairOR(hq);
        log.info("Collapsed pair OR matches for '{}'", source);
        recPOR.forEach((key, value) -> {
            System.out.println(key + ": " + countMatching(hqPOR, value));
        });
    }

    @Test
    void sanityCheck() throws Exception {
        long[] snippet = XCorrSoundFacade.generateFingerPrintFromSoundFile(getResource(X_SOURCE_HQ));
        long[] snippet2 = load(Path.of(getResource(X_SOURCE_HQ)));
        assertEquals(snippet.length, snippet2.length, "Lengths should match");
        for (int i = 0 ; i < snippet.length ; i++) {
            assertEquals(snippet[i], snippet2[i], "Values at index " + i + " should match");
        }

        /*
        long[] recording = XCorrSoundFacade.generateFingerPrintFromSoundFile(X_ROOT + "/" + X_MATCHING[0]);
        System.out.println("Count base: " + countMatching(snippet, recording));

        long[] recording2 = load(Path.of(X_ROOT + "/" + X_MATCHING[0]));
        System.out.println("Count 2: " + countMatching(snippet, recording2));

        long[] snippet2 = load(Path.of(getResource(X_SOURCE_HQ)));
        System.out.println("Count 3: " + countMatching(snippet2, recording2));
        */

    }

    private long countMatching(long[] snippetPrints, long[] recordingPrints) {
        long matches = 0;
        for (long snippetPrint: snippetPrints) {
            for (long recordingPrint: recordingPrints) {
                if (snippetPrint == recordingPrint) {
                    matches++;
                }
            }
        }
        return matches;
    }
    private long countMatching(char[] snippetPrints, char[] recordingPrints) {
        long matches = 0;
        for (long snippetPrint: snippetPrints) {
            for (long recordingPrint: recordingPrints) {
                if (snippetPrint == recordingPrint) {
                    matches++;
                }
            }
        }
        return matches;
    }


    Map<String, char[]> loadAllCollapsePairOR() {
        return loadAllPrints().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, v -> collapsePairOR(v.getValue())));
    }

    char[] collapsePairOR(long[] raw)  {
        return toChar(FingerprintAnalysisTest.getCollapsed(raw, fps ->
                FingerprintAnalysisTest.collapseEveryOther(fps, (first, second) -> first | second)));
    }

    private char[] toChar(long[] values) {
        char[] cs = new char[values.length];
        for (int i = 0 ; i < values.length ; i++) {
            cs[i] = (char)values[i];
        }
        return cs;
    }

    Map<String, long[]> loadAllPrints()  {
        generateAllPrints();
        return Arrays.stream(X_MATCHING)
                .map(s -> X_ROOT + "/" + s)
                .map(Path::of)
                .collect(Collectors.toMap(Path::toString, this::load));
    }

    void generateAllPrints() {
        Arrays.stream(X_MATCHING)
                .map(s -> X_ROOT + "/" + s)
                .forEach(this::generatePrints);
        generatePrints(getResource(X_SOURCE_HQ));
        generatePrints(getResource(X_SOURCE_LQ));
    }

    String getResource(String resource) {
        return Thread.currentThread().getContextClassLoader().getResource(resource).getFile();
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

    private Path getRawPrintsPath(String soundFile) {
        final String base = soundFile.replaceAll("[.][^.]*$", "");
        return Path.of(base + ".rawPrints");
    }

    private void store(long[] rawFingerprints, Path destination) throws IOException {
        log.info("Storing {} fingerprints to '{}'", rawFingerprints.length, destination);
        try (DataOutputStream of = new DataOutputStream(IOUtils.buffer(FileUtils.openOutputStream(destination.toFile(), false)))) {
            for (long fingerprint: rawFingerprints) {
                of.writeInt(Integer.reverseBytes((int) fingerprint)); // To keep it compatible with the c version(?)
            }
        }
    }

    private long[] load(Path source) {
        if (source.toString().endsWith("mp3") || source.toString().endsWith("wav")) {
            source = getRawPrintsPath(source.toString());
        }
        System.out.println("Loading " + source);
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
            throw new RuntimeException("Failed loading fingerprints for '" + source + "'", e);
        }
    }
}
