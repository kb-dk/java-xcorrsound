package dk.kb.xcorrsound.index;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
class SoundTest {
    static final Logger log = LoggerFactory.getLogger(SoundTest.class);

    @Test
    void testMapLoad() {
        String MAP = "/home/te/projects/java-xcorrsound/samples/index/tv2radio_2007-11-01.ismir.index.map";
        if (!Files.exists(Path.of(MAP))) {
            log.info("Cannot run unit test as test map file is not available: " + MAP);
            return;
        }
        int ENTRIES = 381;
        List<Sound> sounds = Sound.loadSoundMaps(List.of(Path.of(MAP)));
        assertEquals(ENTRIES, sounds.size(), "There should be the expected number of map entries");
        System.out.println(sounds);
    }

    @Test
    void testGlobMap() {
        String MAP_GLOB = "/home/te/projects/java-xcorrsound/samples/index/*.map";
        List<Path> maps = dk.kb.util.Resolver.resolveGlob(MAP_GLOB);
        if (maps.isEmpty()) {
            log.info("Cannot run unit test testGlobMap as no map files are available at " + MAP_GLOB);
            return;
        }
        List<Sound> sounds = Sound.loadSoundMaps(maps);
        assertFalse(sounds.isEmpty(), "There should be at least one sound");
    }
}