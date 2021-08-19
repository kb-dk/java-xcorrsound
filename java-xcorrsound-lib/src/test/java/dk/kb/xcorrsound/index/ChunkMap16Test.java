package dk.kb.xcorrsound.index;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
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
class ChunkMap16Test {

    @Test
    public void testMulti() {
        ChunkMap16 chunkMap = new ChunkMap16(3, 1);

        String rec1 = "abcabd";
        String rec2 = "adefghi";

        chunkMap.addRecording(new Sound.MemorySound(rec1, new long[]{1, 2, 3}), rec1.toCharArray());
        chunkMap.addRecording(new Sound.MemorySound(rec2, new long[]{4, 5, 6}), rec2.toCharArray());

        assertEquals(5, chunkMap.getNumChunks(),
                     "The number of chunks should be as expected");

        assertEquals(3, chunkMap.getMatchingChunksIDs('a').cardinality(),
                     "There should be the expected number of chunks containing fingerprint 'a'");
        assertEquals(1, chunkMap.getMatchingChunksIDs('e').cardinality(),
                     "There should be the expected number of chunks containing fingerprint 'e'");
        // 'i' should be in 2 chunks due to overlap
        assertEquals(2, chunkMap.getMatchingChunksIDs('i').cardinality(),
                     "There should be the expected number of chunks containing fingerprint 'i'");

        ChunkCounter counter = chunkMap.countMatches("bd".toCharArray()); // abca, abd, adef
        List<SoundHit> hits = counter.getTopMatches(100);

        assertEquals(3, hits.size(),
                     "There should be the expected number of hits");
        assertEquals(rec1, hits.get(0).getRecording().getID(),
                     "The top hit should be as expected");
    }
}