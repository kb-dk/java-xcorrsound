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

/**
 * Methods for calculating scores for snippets in chunks.
 */
public class ScoreUtil {
    private static final Logger log = LoggerFactory.getLogger(ScoreUtil.class);

    // 16 significant bits
    @FunctionalInterface
    public interface Scorer16 {
        /**
         * @return the score for the given part of the snippet in given part of the record.
         */
        double score(char[] snippet, int snipStart, int snipEnd, char[] recording, int recStart, int recEnd);
    }

    // The number of significant bits are not fixed here. It is up to the implementation
    @FunctionalInterface
    public interface ScorerLong {
        /**
         * @return the score for the given part of the snippet in given part of the record.
         */
        double score(long[] snippet, int snipStart, int snipEnd, long[] recording, int recStart, int recEnd);
    }

    /**
     * Finds the position ther the overlap of the snippet in the recording has the highest amount of overlapping bits.
     * Returns the fraction of overlapping bits.
     *
     * Performance is {@code O(n*m)} where {@code n = snippet.length} and {@code m = recording.length}.
     * @param snippet    the snippet fingerprints  to check for within the recording fingerprints.
     * @param snipStart  index of the first fingerprint in the snipet to check, inclusive.
     * @param snipEnd    index of the last fingerprint in the snippet to check, exclusive.
     * @param recording  fingerprints for the recording.
     * @param recStart   index of the first fingerprint in the recording to search, inclusive.
     * @param recEnd     index of the last fingerprint in the recording to search, exclusive.
     * @param exhaustive if true, the sliding window in the recording continues until it is only 1 fingerprint long.
     *                   if false, the sliding window stops when its right edge reaches recEnd.
     * @return
     */
    public static double findBestMatch16(
            char[] snippet, int snipStart, int snipEnd, char[] recording, int recStart, int recEnd, boolean exhaustive) {
        if (snipStart < 0) {
            throw new IllegalArgumentException("Illegal snippet area start: snipStart=" + snipStart);
        }
        recEnd = Math.min(recEnd, recording.length);
        snipEnd = Math.min(snipEnd, snippet.length);

        int slidingStartMax = exhaustive ? recEnd-2 : recEnd-1-(snipEnd-snipStart);
        if (slidingStartMax < 0) { // Happens if snippet.length > recEnd-recStart.
            slidingStartMax = 1;
        }

        int bestStartPos = -1;
        double bestFraction = 0.0;

        // Extremely dumb brute force
        //System.out.println("Checking snipStart=" +snipStart + ", snipEnd=" + snipEnd + ", recStart=" + recStart + ", recEnd=" + recEnd + ", slidingEnd=" + slidingStartMax);
        for (int slidingOrigo = recStart ; slidingOrigo < slidingStartMax ; slidingOrigo++) {
            int maxSlidingWindowLength = recEnd-slidingOrigo;
            int realSnipEnd = Math.min(snipEnd, snipStart + maxSlidingWindowLength);
            long nonMatchingBits = 0;
//            if (slidingOrigo + realSnipEnd > recEnd) {
//                System.out.println("so=" + slidingOrigo + "+ en=" + realSnipEnd + " =" + (slidingOrigo+realSnipEnd) + " recE=" + recEnd);
//            }
            for (int i = snipStart; i < realSnipEnd ; i++) {
                nonMatchingBits += Long.bitCount(recording[slidingOrigo-snipStart+i] ^ snippet[i]);
            }
            double fraction = 1 - (1.0 * nonMatchingBits / ((realSnipEnd-snipStart) * 16));
            if (fraction > bestFraction) {
                bestFraction = fraction;
                bestStartPos = slidingOrigo;
            }
        }
        return bestFraction;
    }

    /**
     * Finds the position ther the overlap of the snippet in the recording has the highest amount of overlapping bits.
     * Returns the fraction of overlapping bits.
     *
     * Performance is {@code O(n*m)} where {@code n = snippet.length} and {@code m = recording.length}.
     * @param snippet    the snippet fingerprints  to check for within the recording fingerprints.
     * @param snipStart  index of the first fingerprint in the snipet to check, inclusive.
     * @param snipEnd    index of the last fingerprint in the snippet to check, exclusive.
     * @param recording  fingerprints for the recording.
     * @param recStart   index of the first fingerprint in the recording to search, inclusive.
     * @param recEnd     index of the last fingerprint in the recording to search, exclusive.
     * @param exhaustive if true, the sliding window in the recording continues until it is only 1 fingerprint long.
     *                   if false, the sliding window stops when its right edge reaches recEnd.
     * @return
     */
    public static double findBestMatchLong(
            long[] snippet, int snipStart, int snipEnd, long[] recording, int recStart, int recEnd,
            int significantBits,
            boolean exhaustive) {
        if (snipStart < 0) {
            throw new IllegalArgumentException("Illegal snippet area start: snipStart=" + snipStart);
        }
        recEnd = Math.min(recEnd, recording.length);
        snipEnd = Math.min(snipEnd, snippet.length);

        int slidingStartMax = exhaustive ? recEnd-2 : recEnd-1-(snipEnd-snipStart);
        if (slidingStartMax < 0) { // Happens if snippet.length > recEnd-recStart.
            slidingStartMax = 1;
        }

        int bestStartPos = -1;
        double bestFraction = 0.0;

        // Extremely dumb brute force
        //System.out.println("Checking snipStart=" +snipStart + ", snipEnd=" + snipEnd + ", recStart=" + recStart + ", recEnd=" + recEnd + ", slidingEnd=" + slidingStartMax);
        for (int slidingOrigo = recStart ; slidingOrigo < slidingStartMax ; slidingOrigo++) {
            int maxSlidingWindowLength = recEnd-slidingOrigo;
            int realSnipEnd = Math.min(snipEnd, snipStart + maxSlidingWindowLength);
            long nonMatchingBits = 0;
//            if (slidingOrigo + realSnipEnd > recEnd) {
//                System.out.println("so=" + slidingOrigo + "+ en=" + realSnipEnd + " =" + (slidingOrigo+realSnipEnd) + " recE=" + recEnd);
//            }
            for (int i = snipStart; i < realSnipEnd ; i++) {
                nonMatchingBits += Long.bitCount(recording[slidingOrigo-snipStart+i] ^ snippet[i]);
            }
            double fraction = 1 - (1.0 * nonMatchingBits / ((realSnipEnd-snipStart) * significantBits));
            if (fraction > bestFraction) {
                bestFraction = fraction;
                bestStartPos = slidingOrigo;
            }
        }
        return bestFraction;
    }

    public static double matchingBits16Exhaustive(
            char[] snippet, int snipStart, int snipEnd, char[] recording, int recStart, int recEnd) {
        return findBestMatch16(snippet, snipStart, snipEnd, recording, recStart, recEnd, true);
    }

    public static double matchingBits16NonExhaustive(char[] snippet, int snipStart, int snipEnd, char[] recording, int recStart, int recEnd) {
        return findBestMatch16(snippet, snipStart, snipEnd, recording, recStart, recEnd, false);
    }

    public static double matchingBits32NonExhaustive(long[] snippet, int snipStart, int snipEnd, long[] recording, int recStart, int recEnd) {
        return findBestMatchLong(snippet, snipStart, snipEnd, recording, recStart, recEnd, 32, false);
    }

    /**
     * Trivial class with constant score.
     */
    public static class ConstantScorer16 implements Scorer16 {
        final double score;

        public ConstantScorer16(double score) {
            this.score = score;
        }

        @Override
        public double score(char[] snippet, int snipStart, int snipEnd, char[] recording, int recStart, int recEnd) {
            return 0;
        }

        public double getConstantScore() {
            return score;
        }

        @Override
        public String toString() {
            return "ConstantScorer16{" +
                   "score=" + score +
                   '}';
        }
    }

    /**
     * Trivial class with constant score.
     */
    public static class ConstantScorerLong implements ScorerLong {
        final double score;

        public ConstantScorerLong(double score) {
            this.score = score;
        }

        @Override
        public double score(long[] snippet, int snipStart, int snipEnd, long[] recording, int recStart, int recEnd) {
            return 0;
        }

        public double getConstantScore() {
            return score;
        }

        @Override
        public String toString() {
            return "ConstantScorerLong{" +
                   "score=" + score +
                   '}';
        }
    }
}
