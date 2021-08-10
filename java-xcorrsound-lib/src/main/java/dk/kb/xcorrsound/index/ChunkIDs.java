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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Bitmap representation of matched chunks.
 *
 * The structure supports extension to hold any given positive chunkID.
 * It is not sparse, so a single entry with a chunkID of 2 billion will take up 512MB of RAM.
 *
 * This implementation is not thread safe.
 */
public class ChunkIDs {
    private long[] bitmap;
    private int numChunks;

    public ChunkIDs(int numChunks) {
        this.numChunks = numChunks;
        bitmap = new long[getNeededLongs(numChunks)];
    }

    /**
     * Ensure that there is room for at least the given number of chunks.
     * This will preserve registered chunk-IDs.
     * This might result in internal over allocation.
     * @param numChunks the number of chunks to keep track of.
     * @return true if the operation resulted in an internal change of structures.
     */
    public boolean extend(int numChunks) {
        int longs = getNeededLongs(numChunks);
        if (longs <= bitmap.length) { // Room enough
            return false;
        }

        // We over allocate with a factor 1.5 as one extension probably means that there will be more
        long[] newBitmap = new long[(int) (longs * 1.5)];
        System.arraycopy(bitmap, 0, newBitmap, 0, bitmap.length);
        bitmap = newBitmap;
        return true;
    }

    public void set(int chunkID) {
        final int o = chunkID >>> 6;
        final int shift = chunkID & 63;
        bitmap[o] = (bitmap[o] & ~(1L << shift)) | (1L << shift);
    }

    public long get(int chunkID) {
        final int o = chunkID >>> 6;
        final int shift = chunkID & 63;
        return (bitmap[o] >>> shift) & 1L;
    }

    public int cardinality() {
        int cardinality = 0;
        for (long entry: bitmap) {
            cardinality += Long.bitCount(entry);
        }
        return cardinality;
    }

    public long[] getBitmap() {
        return bitmap;
    }

    public IntStream stream() {
        final AtomicInteger pos = new AtomicInteger(-1);
        final int max = bitmap.length * 64;
        return IntStream.generate(() -> {
            while (pos.incrementAndGet() < max) {
                if (get(pos.get()) == 1) {
                    return pos.get();
                }
            }
            return -1;
        }).takeWhile(chunkID -> chunkID != -1);
    }

    private int getNeededLongs(int numChunks) {
        int longs = numChunks / 64;
        if (longs * 64 < numChunks || longs == 0) {
            ++longs;
        }
        return longs;
    }
}
