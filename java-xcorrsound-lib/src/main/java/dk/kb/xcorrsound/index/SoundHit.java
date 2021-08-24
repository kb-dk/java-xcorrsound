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

import java.util.Comparator;
import java.util.Locale;

/**
 * Representation of a hit/match of a snippet into a recording.
 *
 * Note the optional score & offset for collapsed and raw fingerprints.
 * These represent higher quality scores via exhaustive Hamming distance calculation.
 */
public class SoundHit implements Comparable<SoundHit>{
    private final Sound snippet;
    private final int snippetChunkID;
    private final int snippetOffset;
    private final int snippetLength;
    private final Sound recording;
    private final int recordingChunkID;
    private final int matches;
    private final int maxPossibleMatches;
    private final int matchAreaStartFingerprint; // Inclusive
    private final int matchAreaEndFingerprint; // Exclusive
    private final double matchAreaStartSeconds;

    // Optional post-assigned info below
    private double collapsedScore = 0.0; // Optional score
    private int collapsedOffset = -1; // Optional offset for the highest score
    private double rawScore = 0.0; // Optional score
    private int rawOffset = -1; // Optional offset for the highest score

    public SoundHit(Sound snippet, int snippetChunkID, int snippetOffset, int snippetLength,
                    Sound recording, int recordingChunkID,
                    int matchAreaStartFingerprint, int matchAreaEndFingerprint,
                    int matches, int maxPossibleMatches) {
        this.snippet = snippet;
        this.snippetChunkID = snippetChunkID;
        this.snippetOffset = snippetOffset;
        this.snippetLength = snippetLength;
        this.recording = recording;
        this.recordingChunkID = recordingChunkID;
        this.matches = matches;
        this.maxPossibleMatches = maxPossibleMatches;
        this.matchAreaStartFingerprint = matchAreaStartFingerprint;
        this.matchAreaEndFingerprint = matchAreaEndFingerprint;
        // TODO: Not good enough as the fingerprints file might contain multiple sounds. Need origo for the recording
        this.matchAreaStartSeconds = offsetToSeconds(matchAreaStartFingerprint);
    }

    /**
     * @return the snippet used for matching.
     */
    public Sound getSnippet() {
        return snippet;
    }

    /**
     * @return the logical chunkID in the snippet.
     */
    public int getSnippetChunkID() {
        return snippetChunkID;
    }

    /**
     * @return the offset into the snippet where match counting started.
     */
    public int getSnippetOffset() {
        return snippetOffset;
    }

    /**
     * @return the number of fingerprints in the snippet used for matching.
     */
    public int getSnippetLength() {
        return snippetLength;
    }

    /**
     * @return the recording.
     */
    public Sound getRecording() {
        return recording;
    }

    /**
     * @return the chunk in the recording that caused the match.
     * Chunks has length {@link ChunkMap16#getChunkLength()}.
     * Note that the matching area has size chunkLength + {@link ChunkMap16#getChunkOverlap()}.
     * @see #getMatchAreaStartFingerprint()
     * @see #getMatchAreaEndFingerprint()
     */
    public int getRecordingChunkID() {
        return recordingChunkID;
    }

    /**
     * @return the start of the range of fingerprints in the recording where the matched occurred. Inclusive.
     */
    public int getMatchAreaStartFingerprint() {
        return matchAreaStartFingerprint;
    }

    /**
     * @return the end of the range of fingerprints in the recording where the matched occurred. Exclusive.
     */
    public int getMatchAreaEndFingerprint() {
        return matchAreaEndFingerprint;
    }

    public double getMatchAreaStartSeconds() {
        return matchAreaStartSeconds;
    }

    /**
     * @return matches / maxPossibleMatches.
     */
    public double getMatchFraction() {
        return 1.0 * matches / maxPossibleMatches;
    }

    public double getCollapsedScore() {
        return collapsedScore;
    }

    public void setCollapsedScore(double collapsedScore) {
        this.collapsedScore = collapsedScore;
    }

    public int getCollapsedOffset() {
        return collapsedOffset;
    }

    public void setCollapsedOffset(int collapsedOffset) {
        if (collapsedOffset < matchAreaStartFingerprint || collapsedOffset > matchAreaEndFingerprint) {
            throw new ArrayIndexOutOfBoundsException(String.format(
                    Locale.ROOT, "collapsedOffset %d is outside of matchArea [%d -> %d]",
                    collapsedOffset, matchAreaStartFingerprint, matchAreaEndFingerprint));
        }
        this.collapsedOffset = collapsedOffset;
    }

    public double getRawScore() {
        return rawScore;
    }

    public void setRawScore(double rawScore) {
        this.rawScore = rawScore;
    }

    public int getRawOffset() {
        return rawOffset;
    }

    public void setRawOffset(int rawOffset) {
        if (rawOffset < matchAreaStartFingerprint || rawOffset > matchAreaEndFingerprint) {
            throw new ArrayIndexOutOfBoundsException(String.format(
                    Locale.ROOT, "rawOffset %d is outside of matchArea [%d -> %d]",
                    rawOffset, matchAreaStartFingerprint, matchAreaEndFingerprint));
        }
        this.rawOffset = rawOffset;
    }

    private String getMatchAreaStartHumanTime() {
        return secondsToHumanTime(matchAreaStartSeconds);
    }

    private String secondsToHumanTime(double seconds) {
        int hours = (int) (seconds / (60 * 60));
        seconds -= hours * 60 * 60;
        int minutes = (int) (seconds / 60);
        seconds -= minutes * 60;
        return String.format(Locale.ROOT, "%02d:%02d:%04.1f", hours, minutes, seconds);
    }

    public double offsetToSeconds(int offset) {
        return offset * ChunkCounter.MS_PER_FINGERPRINT / 1000.0;
    }

    /**
     * @param offset index in the fingerprints array.
     * @return offset in human time (hh:mm:ss).
     */
    private String offsetToHumanTime(int offset) {
        return secondsToHumanTime(offsetToSeconds(offset));
    }

    /**
     * Matches are a low-quality indicator of match quality.
     * @return the number of matching fingerprints for the chunk that this Hit represents.
     */
    public int getMatches() {
        return matches;
    }

    @Override
    public String toString() {
        return "SoundHit{" +
               "recording='" + recording + '\'' +
               ", score(c=" + toStringScoreMatch(collapsedOffset, collapsedScore) +
               ", r=" + toStringScoreMatch(rawOffset, rawScore) + ")" +
               ", matches=" + matches + "/" + maxPossibleMatches +
               ", recordingChunk=" + recordingChunkID +
               ", matchArea=" + getMatchAreaStartHumanTime() +
               " [" + matchAreaStartFingerprint + "->" + matchAreaEndFingerprint + "]" +
               ", snippet=(" + snippet + ", chunk=" + snippetChunkID +
               ", offset=" + snippetOffset + ", length=" + snippetLength + ")" +
               '}';
    }

    private String toStringScoreMatch(int offset, double score) {
        return String.format(Locale.ROOT, "[%.2f, %s]", score, offsetToHumanTime(offset));
    }

    @Override
    public int compareTo(SoundHit o) {
        return SOUNDHIT_COMPARATOR.compare(this, o);
    }

    private static final Comparator<SoundHit> SOUNDHIT_COMPARATOR =
            Comparator.
                    comparing(SoundHit::getRawScore).reversed().
                    thenComparing(SoundHit::getCollapsedScore).reversed().
                    thenComparing(SoundHit::getMatches).reversed().
                    thenComparing(SoundHit::getRecordingChunkID).
                    thenComparing(hit -> hit.getRecording().getID());

}
