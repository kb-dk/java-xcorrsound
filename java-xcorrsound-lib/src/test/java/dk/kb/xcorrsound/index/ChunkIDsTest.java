package dk.kb.xcorrsound.index;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
class ChunkIDsTest {

    @Test
    void testEdges() {
        final List<Integer> IDS = Arrays.asList(0, 63, 64, 127);

        ChunkIDs ids = new ChunkIDs(12);
        IDS.forEach(ids::extend); // Ensure enough room
        IDS.forEach(ids::set);

        List<Integer> extracted = ids.stream().boxed().collect(Collectors.toList());
        assertEquals(IDS.size(), extracted.size(), "The number of extracted IDs should match");
        assertEquals(IDS.toString(), extracted.toString(), "The extracted IDs should match marked");
    }
}