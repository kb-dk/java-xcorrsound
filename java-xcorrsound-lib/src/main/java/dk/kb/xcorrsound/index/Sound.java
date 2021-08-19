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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.LongStream;

/**
 * Representation of a recording or general sound snippet with optional path to raw fingerprints.
 */
public interface Sound {

    /**
     * @return the ID of the recording. This is often a path.
     */
    String getID();

    /**
     * @return raw fingerprints for the recording. Depending on implementation this can be a heavy process.
     * @throws IOException if the fingerprints could not be produced.
     */
    long[] getRawPrints() throws IOException;

    /**
     * @param offset offset into the record fingerprints, measured in fingerprints.
     * @param length the number of raw fingerprints to fetch from the offset.
     * @return raw fingerprints for a subset of the recording. Depending on implementation this can be a heavy process.
     * @throws IOException if the fingerprints could not be produced.
     */
    default long[] getRawPrints(int offset, int length) throws IOException {
        long[] raw = getRawPrints();
        if (offset > raw.length) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset " + offset + " is larger than the number of raw fingerprints " + raw.length);
        }
        if (offset + length > raw.length) {
            throw new ArrayIndexOutOfBoundsException(
                    "offset " + offset + " + length " + length + " is larger than the number of raw fingerprints " +
                    raw.length);
        }
        if (offset == 0 && length == raw.length) {
            return raw;
        }
        long[] sub = new long[length];
        System.arraycopy(raw, offset, sub, 0, length);
        return sub;
    }

    /**
     * Representation of a Sound using the path to the recording (WAV or MP3).
     * Calls to {@link #getRawPrints()} might result in full raw fingerprints generation.
     */
    class PathSound implements Sound {
        private final Path path;
        private final boolean cachePrints;
        private final PrintHandler printHandler;

        /**
         * Generates a Sound representation without an explicit fingerprint file.
         * If {@link #getRawPrints()} is called, fingerprints are generated using the default {@link PrintHandler} and
         * fingerprints are NOT cached for subsequent calls.
         * @param path a WAV or MP3 file.
         * @throws FileNotFoundException if the path is not readable.
         */
        public PathSound(Path path) throws FileNotFoundException {
            this(path, false, new PrintHandler());
        }

        /**
         * Generates a Sound representation without an explicit fingerprint file.
         * @param path a WAV or MP3 file.
         * @param cachePrints if true {@link #getRawPrints()} is called and fingerprints are generated,
         *                    they will be cached as a sidecar file to {@link #getPath()}.
         * @param printHandler used for generating raw fingerprints for the sound.
         * @throws FileNotFoundException if the path is not readable.
         */
        public PathSound(Path path, boolean cachePrints, PrintHandler printHandler) throws FileNotFoundException {
            this.path = path;
            this.cachePrints = cachePrints;
            this.printHandler = printHandler;
            if (!Files.isReadable(path)) {
                throw new FileNotFoundException("The path '" + path + "' is not readable");
            }
        }

        /**
         * @return the ID of the recording, which for PathSound is a file path.
         */
        @Override
        public String getID() {
            return path.toString();
        }

        /**
         * @return the path for the Sound.
         */
        public Path getPath() {
            return path;
        }

        /**
         * Attempts to locate previously generated raw fingerprints. If that fails, a new set of raw fingerprints are
         * generated. Note that the generation of fingerprints is a heavy process.
         *
         * @return raw fingerprints for the recording.
         * @throws IOException if the fingerprints could not be produced.
         */
        @Override
        public long[] getRawPrints() throws IOException {
            return printHandler.getRawPrints(getPath(), cachePrints);
        }

        @Override
        public int hashCode() {
            return path.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PathSound && obj.hashCode() == hashCode();
        }

        @Override
        public String toString() {
            return "PathSound(path='" + path + "')";
        }
    }

    /**
     * A recording backed by a persistent fingerprints-file.
     */
    class PrintedSound implements Sound {
        private final String id;
        private final Path fingerprints;
        private final int offset;
        private final int length;

        /**
         * @param id           the ID of the recording. This is often a file path, but that is not a requirement.
         * @param fingerprints path for the raw fingerprints for the recording.
         */
        public PrintedSound(String id, Path fingerprints) throws IOException {
            this(id, fingerprints, 0, (int) (Files.size(fingerprints) / 4));
        }

        /**
         * @param id           the ID of the recording. This is often a file path, but that is not a requirement.
         * @param fingerprints path for the raw fingerprints for the recording.
         * @param offset       offset into the raw fingerprints.
         *                     A fingerprint is 4 bytes, so offset 3 is byte number 12 in the file.
         * @param length       the number of raw fingerprints for the recording.
         *                     A fingerprint is 4 bytes, so length 3 means 12 bytes of underlying data.
         */
        public PrintedSound(String id, Path fingerprints, int offset, int length) {
            this.id = id;
            this.fingerprints = fingerprints;
            this.offset = offset;
            this.length = length;
        }

        /**
         * @return the ID of the recording. This is often a file path, but that is not a requirement.
         */
        @Override
        public String getID() {
            return id;
        }

        /**
         * Note: The raw fingerprints file might contain fingerprints for more than just the current recording.
         * If used directly, also use {@link #getOffset()} and {@link #getLength()}.
         * @return the path for the raw fingerprints for the recording.
         */
        public Path getFingerprintsPath() {
            return fingerprints;
        }

        /**
         * @return offset into the raw fingerprints.
         * A fingerprint is 4 bytes, so offset 3 is byte number 12 in the file.
         */
        public int getOffset() {
            return offset;
        }

        /**
         * @return the number of raw fingerprints for the recording.
         * A fingerprint is 4 bytes, so length 3 means 12 bytes of underlying data.
         */
        public int getLength() {
            return length;
        }

        /**
         * Loads {@link #getLength()} fingerprints starting at {@link #getOffset()} from {@link #getFingerprintsPath()}.
         *
         * @return raw fingerprints for the recording.
         */
        @Override
        public long[] getRawPrints() {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        /**
         * Optimized version of {@code getRawPrints.subset(offset, length)} that only loads {@code length} fingerprints
         * from the underlying storage.
         *
         * @param offset offset into the raw fingerprints from {@link #getRawPrints()}. Note that this is effectively
         *               a double offset as {@link #getRawPrints()} uses the base {@link #getOffset()}.
         * @param length the number of raw fingerprints to fetch from the offset.
         * @return raw fingerprints for a subset of the recording.
         */
        @Override
        public long[] getRawPrints(int offset, int length) {
            throw new UnsupportedOperationException("Not implemented yet");
        }

        @Override
        public int hashCode() {
            return id.hashCode() + fingerprints.hashCode() + offset + length;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PrintedSound && obj.hashCode() == hashCode();
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "PrintedSound(id='%s', prints='%s', offset=%d, length=%d",
                                 id, fingerprints, offset, length);
        }
    }

    /**
     * In-memory Sound. Primarily used for testing.
     */
    class MemorySound implements Sound {
        private final String id;
        private final long[] fingerprints;

        public MemorySound(String id, long[] fingerprints) {
            this.id = id;
            this.fingerprints = fingerprints;
        }

        @Override
        public String getID() {
            return id;
        }

        @Override
        public long[] getRawPrints() {
            return fingerprints;
        }

        @Override
        public int hashCode() {
            return (int) (id.hashCode() + fingerprints.length + Arrays.stream(fingerprints).sum());
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof PrintedSound && obj.hashCode() == hashCode();
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "MemorySound(id='%s', #fingerprints=%d)",
                                 id, fingerprints.length);
        }
    }
}
