package dk.kb.xcorrsound.index;

import dk.kb.facade.XCorrSoundFacade;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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
}