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

import java.util.Arrays;
import java.util.function.LongUnaryOperator;

/**
 * Responsible for collapsing 32 bit fingerprints to 16 bit.
 */
public class Collapsor {
    private static final Logger log = LoggerFactory.getLogger(Collapsor.class);

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
        or_half_16,
        /**
         * Retains every other bit, starting with the leftmost bit at index 0.
         */
        every_other_0,
        /**
         * Retains every other bit, starting with the leftmost bit at index 1.
         */
        every_other_1,
        /**
         * Retains the first 16 bits of the 32 bit fingerprint.
         */
        first_half,
        /**
         * Retains the last 16 bits of the 32 bit fingerprint.
         */
        last_half
    }

    public static final COLLAPSE_STRATEGY COLLAPSE_STRATEGY_DEFAULT = COLLAPSE_STRATEGY.or_pairs_16;

    private final Collapsor.COLLAPSE_STRATEGY collapseStrategy;

    public Collapsor() {
        this(COLLAPSE_STRATEGY_DEFAULT);
    }
    public Collapsor(COLLAPSE_STRATEGY collapseStrategy) {
        this.collapseStrategy = collapseStrategy;
    }

    /**
     * Collapse the given 32 significant bits fingerprint to 16 bits.
     * @param rawPrints fingerprints with 32 significant bits.
     * @return collapsed fingerprints.
     */
    public char[] getCollapsed(long[] rawPrints) {
        char[] collapsed;
        switch (collapseStrategy) {
            case or_pairs_16:
                collapsed = toChar(Collapsor.collapseTo16(rawPrints, fps ->
                        Collapsor.collapseEveryOther(fps, (first, second) -> first | second)));
                break;
            case or_half_16:
                collapsed = toChar(Collapsor.collapseTo16(rawPrints, fps ->
                        Collapsor.collapseHalf(fps, (first, second) -> first | second)));
                break;
            case every_other_0:
                collapsed = toChar(Collapsor.collapseTo16(rawPrints, fps ->
                        Collapsor.collapseEveryOther(fps, (first, second) -> first)));
                break;
            case every_other_1:
                collapsed = toChar(Collapsor.collapseTo16(rawPrints, fps ->
                        Collapsor.collapseEveryOther(fps, (first, second) -> second)));
                break;
            case first_half:
                collapsed = toChar(Collapsor.collapseTo16(rawPrints, fps ->
                        Collapsor.collapseHalf(fps, (first, second) -> first)));
                break;
            case last_half:
                collapsed = toChar(Collapsor.collapseTo16(rawPrints, fps ->
                        Collapsor.collapseHalf(fps, (first, second) -> second)));
                break;
            default: throw new UnsupportedOperationException(
                    "The COLLAPSE_STRATEGY '" + collapseStrategy + "' is currently not supported");
        }
        return collapsed;
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
    public static long collapseEveryOther(long fingerprint, LongBiFunction bitJoiner) {
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
    public static long collapseHalf(long fingerprint, LongBiFunction bitJoiner) {
        long a = fingerprint >> 16;
        long b = fingerprint & 0xFFFF;
        return bitJoiner.apply(a, b);
    }

}
