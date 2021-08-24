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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates and provides fingerprints for sound files.
 */
public class PrintHandler {
    private static final Logger log = LoggerFactory.getLogger(PrintHandler.class);

    /**
     * Processes the given file using {@link XCorrSoundFacade#generateFingerPrintFromSoundFile} and returns the result.
     *
     * The generated fingerprints are cached for subsequent calls.
     * @param recording a sound file.
     * @param cachePrints if true, generated prints are persistently cached as a sidecar file.
     * @return fingerprints for the recording.
     * @throws IOException if the file could not be loaded or processed.
     */
    public long[] getRawPrints(final Path recording, boolean cachePrints) throws IOException {
        if (!Files.exists(recording)) {
            throw new FileNotFoundException("The file '" + recording + "' does not exist");
        }

        final Path printsFile = getRawPrintsPath(recording);
        if (Files.exists(printsFile)) {
            return loadRawPrints(printsFile);
        }

        long[] raw = generatePrints(recording);

        if (cachePrints) {
            storeRawPrints(raw, printsFile);
        }

        return raw;
    }

    /**
     * Processes the given file using {@link XCorrSoundFacade#generateFingerPrintFromSoundFile} and returns the result.
     *
     * The generated fingerprints are cached for subsequent calls.
     * @param recording a sound file.
     * @param offset offset into the fingerprints (1 fingerprint = 4 bytes).
     * @param length the number of fingerprints to return (1 fingerprint = 4 bytes).
     * @param cachePrints if true, generated prints are persistently cached as a sidecar file.
     * @return fingerprints for the recording.
     * @throws IOException if the file could not be loaded or processed.
     */
    public long[] getRawPrints(final Path recording, int offset, int length, boolean cachePrints) throws IOException {
        if (!Files.exists(recording)) {
            throw new FileNotFoundException("The file '" + recording + "' does not exist");
        }

        final Path printsFile = getRawPrintsPath(recording);
        if (Files.exists(printsFile)) {
            return loadRawPrints(printsFile, offset, length);
        }

        long[] raw = generatePrints(recording);

        if (cachePrints) {
            storeRawPrints(raw, printsFile);
        }
        if (offset == 0 && length == raw.length) {
            return raw;
        }
        long[] subset = new long[length];
        System.arraycopy(raw, offset, subset, 0, length);
        return subset;
    }

    /**
     * Load raw fingerprints (32 bit/fingerprint) fully from the given printsFile.
     * @param printsFile a file containing fingerprints with 32 significant bits/print.
     * @return all the fingerprints in the file.
     * @throws IOException if the prints could not be loaded.
     */
    private long[] loadRawPrints(Path printsFile) throws IOException {
        if (!Files.isReadable(printsFile)) {
            throw new IOException("Unable to read '" + printsFile + "'");
        }
        int numPrints = (int) (Files.size(printsFile) / 4); // 32 bits/fingerprint
        return loadRawPrints(printsFile, 0, numPrints);
    }

    /**
     * Load a subset of the raw fingerprints (32 bit/fingerprint) from the given printsFile.
     * @param printsFile a file containing fingerprints with 32 significant bits/print.
     * @param offset offset into the fingerprints (1 fingerprint = 4 bytes).
     * @param length the number of fingerprints to return (1 fingerprint = 4 bytes).
     * @return a subset of the fingerprints in the file.
     */
    public long[] loadRawPrints(Path printsFile, int offset, int length) {
        try (FileInputStream is = new FileInputStream(printsFile.toFile())) {
            long skipped = is.skip(offset*4L);
            if (skipped != offset*4L) {
                throw new IllegalStateException(
                        "Could only skip to " + skipped + " when attempting skip to offset " + offset + " for " +
                        printsFile + "'");
            }
            byte[] buffer = new byte[length*4];
            IOUtils.readFully(is, buffer);
            return PrintHandler.bytesToRawPrints(buffer);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to find '" + printsFile + "'");
        } catch (IOException e) {
            throw new RuntimeException("Unable to read " + length*4 + " bytes (" + length + " fingerprints " +
                                       "from offset " + offset*4 + " (" + offset + " fingerprints) in '" +
                                       printsFile + "'");
        }
    }


    /**
     * Converts the given bytes to raw fingerprints, expecting the bytes to originate from the Ismir persistence
     * format.
     * @param bytes an array of bytes in Ismir format. Must be a multiple of 4.
     * @return raw fingerprints, 32 bit significant.
     */
    public static long[] bytesToRawPrints(byte[] bytes) {
        if ((bytes.length & 2) != 0) {
            throw new IllegalArgumentException("Got " + bytes.length + " bytes, which is not a multiple of 4");
        }
        final long[] prints = new long[bytes.length/4];
        for (int i = 0 ; i < prints.length ; i++) {
            prints[i] = Integer.toUnsignedLong(Integer.reverseBytes(
                    (bytes[i<<2]     << 24) +
                    (bytes[(i<<2)+1] << 16) +
                    (bytes[(i<<2)+2] << 8) +
                    (bytes[(i<<2)+3])
            ));
        }
        return prints;
    }

    /**
     * Generate fingerprints for the given soundFile. This method is non-caching and always perform a full generation.
     * @param soundFile a recording or snippet.
     * @return raw fingerprints for the sound.
     */
    public long[] generatePrints(Path soundFile) {
        long startTime = System.currentTimeMillis();
        log.info("Analysing '{}'", soundFile);
        long[] raw;
        try {
            raw = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundFile.toString());
        } catch (Exception e) {
            throw new RuntimeException("Failed generating fingerprint for '" + soundFile + "'", e);
        }
        log.info("Calculated {} fingerprints in {} seconds",
                 raw.length, (System.currentTimeMillis() - startTime) / 1000);
        return raw;
    }

    /**
     * Store the given fingerprints to the given destination. If a file already exists, it is overwritten.
     * @param rawFingerprints fingerprints for a sound file, 32 bit significant.
     * @param destination where to store the fingerprints.
     * @throws IOException if the data could not be written to the destination.
     */
    private void storeRawPrints(long[] rawFingerprints, Path destination) throws IOException {
        log.info("Storing {} fingerprints to '{}'", rawFingerprints.length, destination);
        try (DataOutputStream of = new DataOutputStream(IOUtils.buffer(FileUtils.openOutputStream(destination.toFile(), false)))) {
            for (long fingerprint: rawFingerprints) {
                of.writeInt(Integer.reverseBytes((int) fingerprint)); // To keep it compatible with the c version(?)
            }
        }
    }

    /**
     * @param soundFile path to a sound file.
     * @return path to raw fingerprints for the sound file (might not exist (yet)).
     */
    public Path getRawPrintsPath(Path soundFile) {
        if (soundFile.toString().endsWith("mp3") || soundFile.toString().endsWith("wav")) {
            final String base = soundFile.toString().replaceAll("[.][^.]*$", "");
            return Path.of(base + ".rawPrints");
        }
        return Path.of(soundFile + ".rawPrints");
    }

}
