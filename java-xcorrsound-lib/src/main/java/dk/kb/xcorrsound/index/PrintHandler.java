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
     * Load raw fingerprints (32 bit/fingerprint) fully from the given printsFile.
     * @param printsFile a file containing fingerprints with 32 significant bits/print.
     * @return all the fingerprints in the file.
     * @throws IOException if the prints could not be loaded.
     */
    private long[] loadRawPrints(Path printsFile) throws IOException {
        if (!Files.isReadable(printsFile)) {
            throw new IOException("Unable to read '" + printsFile + "'");
        }

        try {
            int numPrints = (int) (Files.size(printsFile) / 4); // 32 bits/fingerprint
            long[] fingerprints = new long[numPrints];
            log.debug("Loading {} fingerprints from '{}'", numPrints, printsFile);
            try (DataInputStream of = new DataInputStream(IOUtils.buffer(
                    FileUtils.openInputStream(printsFile.toFile())))) {
                for (int i = 0; i < numPrints; i++) {
                    fingerprints[i] = Integer.toUnsignedLong(Integer.reverseBytes(of.readInt()));
                }
            }
            return fingerprints;
        } catch (Exception e) {
            throw new IOException("Failed loading fingerprints from '" + printsFile + "'", e);
        }
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
