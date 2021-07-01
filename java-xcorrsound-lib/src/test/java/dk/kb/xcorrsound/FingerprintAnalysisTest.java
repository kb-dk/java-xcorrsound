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
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Displays statistics for generated fingerprints and experiments with making a smaller fingerprint with more
 * direct matches. Direct matching = exactly the same bit pattern in the fingerprints.
 */

/*
  Technical notes:

  1 fingerprint (32 significant bits) is created for each 11.62 milliseconds (86 fingerprints/second)
  1 hour ~= 300K fingerprints
  1 day ~= 7.4M fingerprints
  1 year = 2.7G fingerprints


  Assuming 100 years of material, divided in 10 minute chunks, we have ~5M chunks
  We can represent this as a bitmap, with 1 bit/chunk + some function to map from the bit-index to file + offset
  A bitmap of 5M chunks is ~ 1MByte

  If a collapsed fingerprint is 16 bit, there are 65K unique fingerprints.
  65K *  1MByte ~= 65 GByte to hold the full collapsed fingerprint lookup structure.
  This seems feasible to hold in memory, although not trivial.

  If we have it in memory, we can do fast search for individual fingerprints AND we can do fast ranking of the
  chunks with the most matches by adding the bitmaps belonging to the matching fingerprints.

  Using an 11 bit collapsor instead, there are only 2K unique fingerprints for a total of ~2GByte of memory.
  While that collapsor will have a lot more false positives for the individual fingerprints, bitmap adding might
  give us a usable answer.
 */
public class FingerprintAnalysisTest {

    @Test
    void testStats() throws Exception {
        //String soundChunk = Thread.currentThread().getContextClassLoader().getResource("last_xmas_chunk1.mp3").getFile();
        String soundChunk = Thread.currentThread().getContextClassLoader().getResource("clip_P3_1400_1600_040806_001-java.mp3").getFile(); // 238 seconds
        dumpStats(soundChunk);
    }

    void dumpStats(String soundChunk) throws Exception {
        // Only the low 32 bits are significant (so why represent is as a long?)
        long[] fingerprints = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundChunk);

        System.out.println("dumping fingerprint stats for '" + soundChunk + "'");
        System.out.println("Fingerprints: " + fingerprints.length);

        // The raw signal has, according to Thomas Egense, the problem of having too many false positives for
        // direct matching

        System.out.println("*********************************************");
        System.out.println("Raw signals (32 bit)");
        System.out.println("*********************************************");
        dumpStats(fingerprints, 32);

        // The collapsors below are variations of bit sampling for Hamming distance
        // https://en.wikipedia.org/wiki/Locality-sensitive_hashing#Bit_sampling_for_Hamming_distance
        // Very simple and very fast

        // OR is lenient, with more false positives but 0 false negatives, compared to raw

        System.out.println("*********************************************");
        System.out.println("Collapsed pairs OR signals (16 bits)");
        System.out.println("*********************************************");
        dumpStats(getCollapsed(fingerprints, fps ->  collapseEveryOther(fps, (first, second) -> first | second)));

        // Collapses halfs OR will have (guessing here) even more false positives than collapsed pairs
        // as the collapsing happens for bits far apart

        System.out.println("*********************************************");
        System.out.println("Collapsed halfs OR signals (16 bits)");
        System.out.println("*********************************************");
        dumpStats(getCollapsed(fingerprints, fps ->  collapseHalf(fps, (first, second) -> first | second)));

        // AND is harsh, with more false negatives but 0 false positives, compared to raw
        // Only makes sense in a scenario where raw has a lot of both false positives & negatives and
        // speed is of high priority

        System.out.println("*********************************************");
        System.out.println("Collapsed pairs AND signals (16 bits)");
        System.out.println("*********************************************");
        dumpStats(getCollapsed(fingerprints, fps ->  collapseEveryOther(fps, (first, second) -> first & second)));

        // The collapsed half version of AND is even harsher

        System.out.println("*********************************************");
        System.out.println("Collapsed halfs AND signals (16 bits)");
        System.out.println("*********************************************");
        dumpStats(getCollapsed(fingerprints, fps ->  collapseHalf(fps, (first, second) -> first & second)));

        // XOR is stupid since 2 paired spikes has the same signal as 0 spikes in the same places. Don't use it!

        /*
        System.out.println("*********************************************");
        System.out.println("Collapsed pairs XOR signals (16 bits)");
        System.out.println("*********************************************");
        dumpCollapsed(getCollapsed(fingerprints, fps ->  collapseEveryOther(fps, (first, second) -> first ^ second)));

        System.out.println("*********************************************");
        System.out.println("Collapsed halfs XOR signals (16 bits)");
        System.out.println("*********************************************");
        dumpCollapsed(getCollapsed(fingerprints, fps ->  collapseHalf(fps, (first, second) -> first ^ second)));
         */

        System.out.println("*********************************************");
        System.out.println("Collapsed triples (minBits=2) (11 bits)");
        System.out.println("*********************************************");
        dumpStats(getCollapsed(fingerprints, fps ->  collapseTriples(fps, 2)), 11);

    }

    private void dumpStats(long[] collapsed) {
        dumpStats(collapsed, 16);
    }
    private void dumpStats(long[] collapsed, int significantBits) {
        Set<Long> uniques = new HashSet<>(collapsed.length);
        Set<Integer> uniqueCounts = new HashSet<>(collapsed.length);

        long[] bitCounts = new long[significantBits+1];
        long[] setBitsCounts = new long[significantBits+1];
        for (long fingerprint: collapsed) {
            setBitsCounts[Long.bitCount(fingerprint)]++;
            uniqueCounts.add(Long.bitCount(fingerprint));
            for (int i = 0 ; i < significantBits ; i++) {
                bitCounts[i] += (fingerprint >>> (significantBits-1-i)) & 0x1;
                uniques.add(fingerprint);
            }
        }
        System.out.println("Unique fingerprints (few=false positives, many=false negatives): " +
                           uniques.size() + "/" + collapsed.length);
        System.out.println("Unique bitcounts: " + uniqueCounts.size() + "/" + significantBits);
        System.out.println("Bitcount distribution");
        for (int i = 0 ; i < significantBits ; i++) {
            System.out.println(String.format(Locale.ROOT, "%2d: %6d", i, setBitsCounts[i]));
        }
        System.out.println("Set bit distribution (ideally there are little deviation between these)");
        for (int i = 0 ; i < significantBits ; i++) {
            System.out.println(String.format(Locale.ROOT, "%2d: %6d", i, bitCounts[i]));
        }
    }

    public static long[] getCollapsed(long[] raw, UnaryOperator<Long> reducer) {
        long[] collapsed = new long[raw.length];
        for (int i = 0 ; i < raw.length ; i++) {
            collapsed[i] = reducer.apply(raw[i]);
        }
        return collapsed;
    }

    /**
     * Processes bits in triples, requiring at least x (where x is 1-3) set bits in total for a signal.
     * @param fingerprint
     * @param minBits the minimum amount of input bits to be set in order to produce an output bit.
     * @return
     */
    static long collapseTriples(long fingerprint, int minBits) {
        long collapsed = 0L;
        for (int i = 0 ; i < 11 ; i++) { // 33 / 3 = 11. We accept that the outermost bit in the fingerprint is always 0
            long count = (fingerprint >>> (31 - i*3) & 0x1) +
                        (fingerprint >>> (31 - i*3 - 1) & 0x1) +
                        (fingerprint >>> (31 - i*3 - 2) & 0x1);
/*            if (i == 10) {
                System.out.println(String.format(Locale.ROOT, "%32s c=%d",
                                                 Long.toBinaryString(fingerprint), count));
            }*/
            if (count >= minBits || (i == 10 && count >= 1)) { // This makes the last 2 bits stronger, but what to do?
                collapsed |= 1 << (10-i);
            }
        }
        return collapsed;
    }

    /**
     * Processes bits in pairs.
     * @param fingerprint
     * @param bitJoiner   takes 2 bitsets (16 significant bits), joins them and returns the resulting bits.
     * @return
     */
    static long collapseEveryOther(long fingerprint, BiFunction<Long, Long, Long> bitJoiner) {
        long a = 0L;
        long b = 0L;
        for (int i = 0 ; i < 16 ; i++) {
            a |= ((fingerprint >>> (31 - i * 2)) & 0x1) << (15 - i);
            b |= ((fingerprint >>> (31 - i * 2 - 1)) & 0x1) << (15 - i);
        }
        /*
        System.out.println(String.format(Locale.ROOT, "%16s %16s -> %16s",
                                         Long.toBinaryString(a),
                                         Long.toBinaryString(b),
                                         Long.toBinaryString(bitJoiner.apply(a, b))));
                                         */
        return bitJoiner.apply(a, b);
    }

    /**
     * Processes bits in parallel matching first half and second half of the fingerprint.
     * Bits are represented at the last position of Longs.
     * @param fingerprint
     * @param bitJoiner   takes 2 bitsets (16 significant bits), joins them and returns the resulting bits.
     * @return
     */
    static long collapseHalf(long fingerprint, BiFunction<Long, Long, Long> bitJoiner) {
        long a = fingerprint >> 16;
        long b = fingerprint & 0xFFFF;
        /*
        System.out.println(String.format(Locale.ROOT, "%16s %16s -> %16s",
                                         Long.toBinaryString(a),
                                         Long.toBinaryString(b),
                                         Long.toBinaryString(bitJoiner.apply(a, b))));
         */
        return bitJoiner.apply(a, b);
    }

}
