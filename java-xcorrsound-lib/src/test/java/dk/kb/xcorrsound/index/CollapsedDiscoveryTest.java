package dk.kb.xcorrsound.index;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

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
class CollapsedDiscoveryTest {

    final static String X_ROOT = "/home/te/projects/java-xcorrsound/samples/last_xmas/";
    //Files that has match using the slow search algorithm and all are correct
    final static String[] X_MATCHING = new String[] { // Under X_ROOT
            "P3_0600_0800_890110_001.mp3",
            "P3_0600_0800_891222_001.mp3",
            "P3_1000_1200_901211_001.mp3",
            "P3_1400_1600_891205_001.mp3",
            "P3_0600_0800_891211_001.mp3",
            "P3_0600_0800_901225_001.mp3",
            "P3_1200_1400_891206_001.mp3",
            "P3_1400_1600_891224_001.mp3"
    };
    //"clip_P3_1400_1600_040806_001-java.mp3",

    final static String B_ROOT = "/home/te/projects/java-xcorrsound/samples/barbie_girl/";
    final static String[] B_MATCHING = new String[]{ // Under B_ROOT
            "P3_0800_1000_970406_001.mp3",
            "P3_1000_1200_970701_001.mp3",
            "P3_1200_1400_970410_001.mp3",
            "P3_1400_1600_970325_001.mp3",
            "P3_1400_1600_970418_001.mp3",
            "P3_1800_2000_970404_001.mp3",
            "P3_2000_2200_970421_001.mp3",
            "P3_2000_2200_970426_001.mp3",
            "P3_2000_2200_970518_001.mp3"
    };

    // Random recordings with no special match for the snippets
    final static String R_ROOT = "/home/te/projects/java-xcorrsound/samples/random/";
    final static String[] R_RECORDINGS = new String[]{ // Under R_ROOT
            "f2fdb142-0cc3-4b51-815b-45aa6fbd0d89.mp3",
            "f20d92e2-61a3-492d-9767-1ecfa263a5a0.mp3",
            "f219404e-d2a4-45d4-8035-75991ce88a90.mp3",
            "f23737f8-35db-4e49-9c1f-5316cc3b5059.mp3",
            "f237404c-8081-41a2-a7ab-7528f943eb81.mp3",
            "f237533c-cf60-4c21-be51-bfe00771c4c1.mp3",
            "f23799a4-ae90-4aa0-91b1-006239ac4940.mp3",
            "f237de62-4e8b-4f38-ba5a-8e9b49bb923b.mp3",
            "f2613da2-dcf6-4996-b870-e83fb3a30f94.mp3",
            "f2614493-2f8a-4cc7-a2ad-7efa40d7dcfa.mp3",
            "f261face-dd71-4bfe-98c5-8d105a5439fb.mp3",
            "f284903e-8e70-42d5-8206-808310acc42c.mp3",
            "f284c0c2-b60c-4125-b0e1-142d6333362e.mp3",
            "f2c09341-511f-4cbb-9c66-296a6b1341b0.mp3",
            "f2c0c99c-c72a-4f03-9812-4fc6a56ec5c3.mp3",
            "f2d4835e-257d-4a17-a209-f43933878c98.mp3",
            "f2d4a42c-4cf0-4233-a51b-b5322de6e7f6.mp3",
            "f2d4d334-9bda-4798-91c4-91714a43128e.mp3",
            "f2d4ea80-432b-4d3c-b9d1-06ecd64e03e8.mp3",
            "f2e05513-47a9-4913-addd-cf373dc1b5c1.mp3",
            "f2e07d05-657f-4d7c-9bb8-125d2efdbdb5.mp3"
    };

    final static String X_SOURCE_HQ = "last_xmas_chunk1.mp3"; // In resources
    final static String X_SOURCE_LQ = "last_xmas_youtube.mp3"; // In resources

    final static String B_SOURCE = "Barbie_girl_chunk.mp3"; // In resources

    @Test
    void countCollapseMatches() throws IOException {
        final int MIN_LENGTH = 4;
        for (String snippet: Arrays.asList(
                getResource(X_SOURCE_HQ),
                getResource(X_SOURCE_LQ),
                getResource(B_SOURCE)
        )) {
            System.out.println("*******************************************************************************");
            countCollapseMatches(snippet, 2);
        }
    }

    private void countCollapseMatches(String snippet, int minLength) throws IOException {
        final String[] RECORDINGS = new String[]{
                X_ROOT + "P3_1400_1600_891224_001.mp3",
                X_ROOT + "P3_0600_0800_890110_001.mp3",
                X_ROOT + "P3_0600_0800_901225_001.mp3",
                X_ROOT + "P3_0600_0800_891211_001.mp3",

                B_ROOT + "P3_1400_1600_970418_001.mp3",
                B_ROOT + "P3_2000_2200_970421_001.mp3",
                B_ROOT + "P3_1200_1400_970410_001.mp3",
                B_ROOT + "P3_2000_2200_970426_001.mp3"
        };

        for (Collapsor.COLLAPSE_STRATEGY strategy: Collapsor.COLLAPSE_STRATEGY.values()) {
            List<int[]> matches = new ArrayList<>();
            for (String recording: RECORDINGS) { // Separate calculation from output to avoid log output mess
                matches.add(countTotalMatches(recording, snippet, minLength, strategy));
            }
            System.out.println("\nFor snippet " + snippet + " with strategy " + strategy + " and minLength " + minLength);
            for (int i = 0 ; i< RECORDINGS.length ; i++) {
                String recording = RECORDINGS[i];
                System.out.printf(Locale.ROOT,
                                  "raw=%2d, collapsed=%5d, recording=%s%n",
                                  matches.get(i)[0], matches.get(i)[1], recording);
            }
        }
    }

    @Test
    void dumpXHQ() throws IOException {
        dumpSnippetSum(X_SOURCE_HQ);
    }
    @Test
    void dumpB() throws IOException {
        dumpSnippetSum(B_SOURCE);
    }
    void dumpSnippet(String snippetID) throws IOException {
        CollapsedDiscovery cd = new CollapsedDiscovery(10000, 2000, Collapsor.COLLAPSE_STRATEGY.or_pairs_16, true);
        Sound snippet = cd.toSound(getResource(snippetID));
        long[] rRaw = snippet.getRawPrints();
        char[] rCollapsed = cd.collapsor.getCollapsed(rRaw);
        Arrays.sort(rCollapsed);
        for (int i = 0 ; i < rCollapsed.length ; i++) {
            System.out.print(Long.toHexString(rCollapsed[i]) + " ");
            if (i != 0 && i % 100 == 0) {
                System.out.println("");
            }
        }
    }

    void dumpSnippetSum(String snippetID) throws IOException {
        System.out.println("Dumping count for collapsed for " + snippetID);
        CollapsedDiscovery cd = new CollapsedDiscovery(10000, 2000, Collapsor.COLLAPSE_STRATEGY.or_pairs_16, true);
        Sound snippet = cd.toSound(getResource(snippetID));
        char[] rCollapsed = cd.collapsor.getCollapsed(snippet.getRawPrints());
        Arrays.sort(rCollapsed);
        int last = -1;
        int count = 0;
        int unique = 0;
        for (int i = 0 ; i < rCollapsed.length ; i++) {
            int p = rCollapsed[i];
            if (last != p) {
                if (last != -1) {
                    System.out.printf(Locale.ROOT, "%s, %s, %d%n",
                                      leftPad(Long.toBinaryString(last), 16, "0"),
                                      leftPad(Long.toHexString(last), 4, "0"), count);
                }
                last = p;
                count = 0;
                unique++;
            }
            count++;
        }
        System.out.printf(Locale.ROOT, "%s, %s, %d%n",
                          leftPad(Long.toBinaryString(last), 16, "0"),
                          leftPad(Long.toHexString(last), 4, "0"), count);
        System.out.println("Unique: " + unique + "/" + rCollapsed.length);
    }

    private String leftPad(String s, int width, String padding) {
        while (s.length() < width) {
            s = padding + s;
        }
        return s;
    }

    private int[] countTotalMatches(String recordingPath, String snippetPath, int minLength, Collapsor.COLLAPSE_STRATEGY strategy) throws IOException {
        CollapsedDiscovery cd = new CollapsedDiscovery(1000, 100, strategy, true);

        Sound recording = cd.toSound(recordingPath);
        long[] rRaw = recording.getRawPrints();
        char[] rCollapsed = cd.collapsor.getCollapsed(rRaw);

        Sound snippet = cd.toSound(snippetPath);
        long[] sRaw = snippet.getRawPrints();
        char[] sCollapsed = cd.collapsor.getCollapsed(sRaw);

        int rawMatches = 0;
        for (long sPrint: sRaw) {
            for (long rPrint: rRaw) {
                if (sPrint == rPrint) {
                    rawMatches++;
                }
            }
        }

        int collapsedMatches = 0;
        for (int s = 0; s < sCollapsed.length-minLength+1; s++) {
            rLoop:
            for (int r = 0; r < rCollapsed.length-minLength+1; r++) {
                for (int o = 0 ; o < minLength ; o++) {
                    if (sCollapsed[s+o] != rCollapsed[r+o]) {
                        continue rLoop;
                    }
                }
                collapsedMatches++;
            }
        }
        return new int[]{rawMatches, collapsedMatches};
    }

    @Test
    void basicTestX() throws IOException {
        final int TOP_X = 30;
        final Collapsor.COLLAPSE_STRATEGY STRATEGY = Collapsor.COLLAPSE_STRATEGY.or_pairs_16;
        final int CHUNK_LENGTH = 2000;
        final int CHUNK_OVERLAP = 2000;


        CollapsedDiscovery cd = new CollapsedDiscovery(CHUNK_LENGTH, CHUNK_OVERLAP, STRATEGY, true);
        addRecordings(cd, X_ROOT, X_MATCHING);
        addRecordings(cd, B_ROOT, B_MATCHING);

        System.out.println("Total chunks in ChunkMap16: " + cd.chunkMap.getNumChunks());
        top(cd, X_SOURCE_HQ, TOP_X);
        top(cd, X_SOURCE_LQ, TOP_X);
        top(cd, B_SOURCE, TOP_X);
    }

    private void top(CollapsedDiscovery cd, String snippet, int topX) throws IOException {
        List<ChunkCounter.Hit> hqHits = cd.findCandidates(getResource(snippet), topX);
        System.out.println("*** Hits for " + snippet);
        hqHits.forEach(System.out::println);
    }

    @Test
    void chunkedFind() throws IOException {
        final int TOP_X = 30;
        final Collapsor.COLLAPSE_STRATEGY STRATEGY = Collapsor.COLLAPSE_STRATEGY.every_other_0;
        //  500 = very promising results, 42GB/year
        // 5000 = might work, 4GB/year
        final int R_CHUNK_LENGTH = 20000;
        final int R_CHUNK_OVERLAP = 2000;

        final int S_CHUNK_LENGTH = 500;
        final int S_CHUNK_OVERLAP = 0;

        CollapsedDiscovery cd = new CollapsedDiscovery(R_CHUNK_LENGTH, R_CHUNK_OVERLAP, STRATEGY, true).
                setCollapsedScorer(ScoreUtil::matchingBits16NonExhaustive).
                setRawScorer(ScoreUtil::matchingBits32NonExhaustive);

        //addRecordings(cd, X_ROOT, new String[]{"P3_1000_1200_901211_001.mp3"});
        addRecordings(cd, R_ROOT, R_RECORDINGS);
        //addRecordings(cd, B_ROOT, new String[]{"P3_0800_1000_970406_001.mp3"});
        addRecordings(cd, X_ROOT, X_MATCHING);
        addRecordings(cd, B_ROOT, B_MATCHING);

        System.out.println("Record chunk length " + R_CHUNK_LENGTH + ", 1 year ~= " + (86L*60*60*24*365/R_CHUNK_LENGTH* 8/1024) + "MB");
        System.out.println("Total chunks in ChunkMap16: " + cd.chunkMap.getNumChunks());
        System.out.println("Snippet chunk length " + S_CHUNK_LENGTH + " ~= " + S_CHUNK_LENGTH/86 + " seconds");
        topChunked(cd, X_SOURCE_HQ, TOP_X, S_CHUNK_LENGTH, S_CHUNK_OVERLAP);
        topChunked(cd, X_SOURCE_LQ, TOP_X, S_CHUNK_LENGTH, S_CHUNK_OVERLAP);
        topChunked(cd, B_SOURCE, TOP_X, S_CHUNK_LENGTH, S_CHUNK_OVERLAP);
    }

    private void topChunked(CollapsedDiscovery cd, String snippet, int topX, int sChunkLength, int sChunkOverlap) throws IOException {
        final int PRE_SKIP = 50;
        final int POST_SKIP = 50;

        List<List<ChunkCounter.Hit>> chunkHits =
                cd.findCandidates(getResource(snippet), topX, PRE_SKIP, POST_SKIP, sChunkLength, sChunkOverlap);
        long[] snippetPrints = new PrintHandler().getRawPrints(Path.of(getResource(snippet)), true);
        System.out.println("\n*** Hits for " + snippet + " with " + snippetPrints.length + " prints " +
                           "(" + snippetPrints.length*ChunkCounter.MS_PER_FINGERPRINT/1000 + " seconds)");
        for (int i = 0 ; i < chunkHits.size() ; i++) {
            //chunkHits.get(i).sort(Comparator.comparing(ChunkCounter.Hit::getRecordingID).thenComparingInt(ChunkCounter.Hit::getMatchAreaStartFingerprint));
            List<ChunkCounter.Hit> hits = chunkHits.get(i);
            System.out.println("chunk " + i);

            System.out.println(" - Sorted by matches");
            hits.stream().
                    map(Object::toString).
                    map(str -> str.replace("/home/te/projects/java-xcorrsound/samples/", "")).
                    forEach(System.out::println);

            System.out.println(" - Sorted by score, duplicate recordings removed");
            final Set<String> seenRecordings = new HashSet<>();
            hits.stream().
                    filter(hit -> seenRecordings.add(hit.getRecording().getID())).
                    sorted(Comparator.comparing(ChunkCounter.Hit::getCollapsedScore).reversed()).
                    map(Object::toString).
                    map(str -> str.replace("/home/te/projects/java-xcorrsound/samples/", "")).
                    forEach(System.out::println);
        }
    }

    void addRecordings(CollapsedDiscovery cd, String root, String[] instances)  {
        Arrays.stream(instances)
                .map(s -> root + s)
                .forEach(rec -> {
                    try {
                        cd.addRecording(rec);
                    } catch (IOException e) {
                        throw new RuntimeException("Could not add '" + rec + "'", e);
                    }
                });
    }

    String getResource(String resource) {
        return Thread.currentThread().getContextClassLoader().getResource(resource).getFile();
    }


}