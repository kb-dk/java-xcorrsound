package dk.kb.xcorrsound.index;

import dk.kb.facade.XCorrSoundFacade;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

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
class PrintHandlerTest {
    // Disabled as if was used purely for debugging
    void testLastX() throws Exception {
        // Mimick JavaIshmirImplTest
        String soundFile = Thread.currentThread().getContextClassLoader().getResource("last_xmas_chunk1.mp3").getFile();
        assertPrints(soundFile);
        assertPrints("/home/te/projects/java-xcorrsound/samples/b27401cb-d635-4b7f-bcf9-bf93389e2118.wav");
    }

    private void assertPrints(String soundFile) throws Exception {
        long[] xPrints = XCorrSoundFacade.generateFingerPrintFromSoundFile(soundFile);
        System.out.println("XCorr facade delivered " + xPrints.length + " prints for " + soundFile);

        long[] hPrints = new PrintHandler().generatePrints(Path.of(soundFile));
        System.out.println("PrintHandler delivered " + hPrints.length + " prints for " + soundFile);
        assertEquals(xPrints.length, hPrints.length, "Both print generators should deliver the same amount of prints");
    }

    @Test
    void testSubPrintOffset() throws IOException {
        PrintHandler ph = new PrintHandler();
        Path soundFile = Path.of(TestHelper.getResource("last_xmas_chunk1.mp3"));
        long[] basePrints = ph.generatePrints(soundFile, 0);

        for (int sub = 0 ; sub < 1 ; sub++) {
            long[] subPrints = ph.getRawPrints(soundFile, sub, 0, -1, true);
            if (!Arrays.equals(basePrints, subPrints)) {
                System.out.println("sampleOffset=" + sub + " differs from base sampleOffset=0");
                if (sub == 0) {
                    TestHelper.dumpDiff(basePrints, 0, subPrints.length, subPrints, 0);
                    fail("There should be no difference between base and sampleOffset=0");
                }
            }
        }

    }

    @Test
    void sanityCheckPersistence() throws IOException {
        Path data = Files.createTempFile("printhandler", "dat");
        long[] fingerprints = new long[]{256L, 3571164810L, 724341878L};

        try (DataOutputStream of = new DataOutputStream(IOUtils.buffer(FileUtils.openOutputStream(data.toFile(), false)))) {
            for (long fingerprint: fingerprints) {
                of.writeInt(Integer.reverseBytes((int) fingerprint)); // To keep it compatible with the c version(?)
            }
        }

        long[] loaded;
        try (FileInputStream is = new FileInputStream(data.toFile())) {
            byte[] buffer = new byte[fingerprints.length*4];
            IOUtils.readFully(is, buffer);
            loaded = PrintHandler.bytesToRawPrints(buffer);
        }
        System.out.println("Data in " + data);
        //Files.delete(data);

        if (!Arrays.equals(fingerprints, loaded)) {
            TestHelper.dumpDiff(fingerprints, loaded);
            fail("Loaded fingerprints should be equal to stored");
        }
    }

    @Test
    void sanityCheckPersistence2() throws IOException {
        Path data = Files.createTempFile("printhandler", "dat");
        final int S_PRINTS = 32768;
        final int E_PRINTS = 32770;

        long[] fingerprints = new long[E_PRINTS-S_PRINTS];
        try (DataOutputStream of = new DataOutputStream(IOUtils.buffer(FileUtils.openOutputStream(data.toFile(), false)))) {
            for (long fingerprint = S_PRINTS ; fingerprint < E_PRINTS ; fingerprint++) {
                fingerprints[(int) fingerprint-S_PRINTS] = fingerprint;
                of.writeInt(Integer.reverseBytes((int) fingerprint)); // To keep it compatible with the c version(?)
            }
        }

        long[] loaded;
        try (FileInputStream is = new FileInputStream(data.toFile())) {
            byte[] buffer = new byte[(E_PRINTS-S_PRINTS)*4];
            IOUtils.readFully(is, buffer);
            for (int i = 0 ; i < buffer.length ; i++) {
                System.out.print(Long.toBinaryString(Byte.toUnsignedLong(buffer[i])) + "-");
            }
            System.out.println();
            loaded = PrintHandler.bytesToRawPrints(buffer);
        }

        Files.delete(data);

        if (!Arrays.equals(fingerprints, loaded)) {
            TestHelper.dumpDiff(fingerprints, loaded);
            fail("Loaded fingerprints should be equal to stored");
        }
    }
    @Test
    void testPersistence() throws IOException {
        PrintHandler ph = new PrintHandler();
        Path soundFile = Path.of(TestHelper.getResource("last_xmas_chunk1.mp3"));
        final long[] base = ph.generatePrints(soundFile, 0);
        Path existingCache = ph.getRawPrintsPath(soundFile, 0);
        if (Files.exists(existingCache)) {
            Files.delete(existingCache);
        }
        final long[] firstCall = ph.getRawPrints(soundFile, 0, 0, -1, true);
        final long[] secondCall = ph.getRawPrints(soundFile, 0, 0, -1, true);

        if (!Arrays.equals(base, firstCall)) {
            TestHelper.dumpDiff(base, 0, firstCall.length, firstCall, 0);
            fail("There should be no difference between base and firstCall");
        }

        if (!Arrays.equals(base, secondCall)) {
            TestHelper.dumpDiff(base, 0, secondCall.length, secondCall, 0);
            fail("There should be no difference between base and secondCall");
        }
    }
}