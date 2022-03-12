/*
 * This file is part of tinyprotocol2, licensed under the MIT License.
 *
 * Copyright (c) 2022 Matouš Kučera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package {utilsPackage};

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class MappingUtils {
    // [packet name, [mapping string, [obfuscated mapping, protocolVersions]]]
    private static final Map<String, Map<String, Map<String, List<Integer>>>> CACHE = Collections.synchronizedMap(new HashMap<>());
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private MappingUtils() {
    }

    public static Map<String, List<Integer>> unwrapMappings(String packetName, String mapping) {
        LOCK.readLock().lock();
        try {
            final Map<String, Map<String, List<Integer>>> cacheResult = CACHE.get(packetName);
            if (cacheResult != null) {
                final Map<String, List<Integer>> cacheResult1 = cacheResult.get(mapping);
                if (cacheResult1 != null) {
                    return cacheResult1;
                }
            }
        } finally {
            LOCK.readLock().unlock();
        }
        LOCK.writeLock().lock();
        try {
            final Map<String, List<Integer>> mappings = new HashMap<>();
            final String[] parts = mapping.split("\\+");
            for (int i = 0; i < parts.length; i++) {
                final String[] sides = parts[i].split("=");
                mappings.computeIfAbsent(sides[0].replace('/', '.'), key -> new ArrayList<>())
                        .addAll(Arrays.stream(sides[1].split(",")).map(Integer::parseInt).collect(Collectors.toList()));
            }
            // finalizing protocol lists
            final Iterator<Map.Entry<String, List<Integer>>> mappingIterator = mappings.entrySet().iterator();
            while (mappingIterator.hasNext()) {
                final Map.Entry<String, List<Integer>> entry = mappingIterator.next();
                entry.setValue(Collections.unmodifiableList(entry.getValue()));
            }
            return CACHE.computeIfAbsent(packetName, key -> Collections.synchronizedMap(new HashMap<>())).put(mapping, Collections.unmodifiableMap(mappings));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public static String findMapping(Map<String, List<Integer>> unwrapped, int ver) {
        return unwrapped.entrySet().stream()
                .filter(e -> e.getValue().contains(ver))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static String findMapping(Class<?> clazz, int ver) {
        return findMapping(unwrapMappings(clazz.getSimpleName(), clazz.getAnnotation({utilsPackage}.Reobfuscate.class).value()), ver);
    }

    public static String findMapping(Class<?> clazz, String mapping, int ver) {
        return findMapping(unwrapMappings(clazz.getSimpleName(), mapping), ver);
    }

    public static String findMapping(String packetName, Field field, int ver) {
        return findMapping(unwrapMappings(packetName, field.getAnnotation({utilsPackage}.Reobfuscate.class).value()), ver);
    }
}
