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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 *
 */
public class TestHelper {
    private static final Logger log = LoggerFactory.getLogger(TestHelper.class);

    public static void dumpDiff(long[] snippetRaw, int snipStart, int snipEnd, long[] recordingRaw, int recordingStart) {
        int diffs = 0;
        int diffBits = 0;
        for (int i = 0 ; i < snipEnd-snipStart ; i++) {
            if (snippetRaw[snipStart+i] != recordingRaw[recordingStart+i]) {
                int nonMatching = Long.bitCount(snippetRaw[snipStart + i] ^ recordingRaw[recordingStart + i]);
                System.out.printf(
                        Locale.ROOT, "%3d: %s - %s (%2d)\n",
                        i, bin32(snippetRaw[snipStart + i]), bin32(recordingRaw[recordingStart + i]), nonMatching);
                diffBits += nonMatching;
            }
        }
        System.out.printf(Locale.ROOT, "Total diffs: %d/%d, bitDiffs= %d/%d, score=%f\n",
                          diffs, snipEnd-snipStart, diffBits, (snipEnd-snipStart)*32,
                          1.0-(diffBits*1.0/((snipEnd-snipStart)*32)));
    }

    private static String bin32(long value) {
         String binary = Long.toBinaryString(value);
         while (binary.length() < 32) {
             binary = "0" + binary;
         }
         return binary;
     }

    public static String getResource(String resource) {
        return Files.exists(Path.of(resource)) ? resource :
                Thread.currentThread().getContextClassLoader().getResource(resource).getFile();
    }

}
