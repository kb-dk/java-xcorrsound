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
package dk.kb.util;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A list of integers, closely modelled after {@code ArrayList<Integer>} but backed by {@code int[]}.
 *
 * For high speed, it is recommended to use the primitive type methods rather than the {@code Object} based
 * methods from {@link java.util.List}. Note that the high speed methods throws less detailed Exceptions on errors
 * than their {@code Object} based counterparts.
 *
 * This class is not thread safe.
 *
 * // TODO: Add optimized overrides for most methods from the List interface
 */
public class IntList extends AbstractList<Integer> {

    private static final int DEFAULT_CAPACITY = 10;
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private int[] elements;
    private int size;

    public IntList() {
        this(DEFAULT_CAPACITY);
    }

    public IntList(int... elements) {
        this(Math.max(elements.length, DEFAULT_CAPACITY));
        System.arraycopy(elements, 0, this.elements, 0, elements.length);
    }

    public IntList(Collection<Integer> elements) {
        this(Math.max(elements.size(), DEFAULT_CAPACITY));
        
    }

    public IntList(int initialCapacity) {
        this.size = initialCapacity;
        elements = new int[initialCapacity];
    }

    /* Basic overrides below */

    @Override
    public Integer get(int index) {
        Objects.checkIndex(index, size);
        return elements[index];

    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean add(Integer element) {
        return addPrimitive(element);
    }

    @Override
    public Integer set(int index, Integer element) {
        Objects.checkIndex(index, size);
        return setPrimitive(index, element);
    }

    /* Non-overrides as their default implementation cannot be substantially improved */

    // public void forEach(Consumer<? super Integer> action)

    @Override
    public void clear() {
        super.clear();    // TODO: Implement this
    }



    /* Faster primitive type methods */

    @Override
    public int indexOf(Object o) { // No need for explicit primitive version as the unboxing is only done once
        if (!(o instanceof Integer)) {
            return -1;
        }
        final int wanted = (Integer)o;
        for (int i = 0 ; i < size ; i++) {
            if (elements[i] == wanted) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Stream<Integer> stream() {
        return Arrays.stream(elements, 0, size).boxed();
    }

    @Override
    public int lastIndexOf(Object o) {  // No need for explicit primitive version as the unboxing is only done once
        if (!(o instanceof Integer)) {
            return -1;
        }
        final int wanted = (Integer)o;
        for (int i = size-1 ; i >= 0 ; i--) {
            if (elements[i] == wanted) {
                return i;
            }
        }
        return -1;
    }

    public int getPrimitive(int index) {
        return elements[index];
    }

    public boolean addPrimitive(int element) {
        modCount++;
        if (size == elements.length) {
            grow();
        }
        elements[size++] = element;
        return true;
    }

    public int setPrimitive(int index, int element) {
        int oldValue = elements[index];
        elements[index] = element;
        return oldValue;
    }

    public IntStream streamPrimitives() {
        return Arrays.stream(elements, 0, size);
    }

    public void forEach(IntConsumer action) {
        for (int i = 0 ; i < size ; i++) {
            action.accept(elements[i]);
        }
    }

    /**
     * Exactly the same as {@link #forEach(IntConsumer)}. Duplicate methods to uphold the principle of "Primitive"
     * methods signalling direct operation on primitives (avoiding boxing).
     * @param action performed on each element.
     */
    public void forEachPrimitive(IntConsumer action) {
        forEach(action);
    }

    /**
     * Natural Integer order sort.
     */
    public void sort() {
        Arrays.sort(elements, 0, size);
    }

    /* Helper methods for primitive handling */

    private void grow() {
        grow(elements.length+1);
    }

    private void grow(int minCapacity) {
        elements = Arrays.copyOf(elements, newCapacity(minCapacity));
    }

    // Taken near verbatim from {@link ArrayList}
    private int newCapacity(int minCapacity) {
        // overflow-conscious code
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity <= 0) {
            if (elements.length == 0) {
                return Math.max(DEFAULT_CAPACITY, minCapacity);
            }
            if (minCapacity < 0) { // overflow
                throw new OutOfMemoryError();
            }
            return minCapacity;
        }
        return (newCapacity - MAX_ARRAY_SIZE <= 0)
            ? newCapacity
            : hugeCapacity(minCapacity);
    }
    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();
        return (minCapacity > MAX_ARRAY_SIZE)
            ? Integer.MAX_VALUE
            : MAX_ARRAY_SIZE;
    }


}
