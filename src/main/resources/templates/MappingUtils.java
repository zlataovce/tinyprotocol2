import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public final class MappingUtils {
    // [packet name, [mapping string, protocolVersions]]
    private static final Map<String, Map<String, Map<String, List<Integer>>>> CACHE = Collections.synchronizedMap(new HashMap<>());

    private MappingUtils() {
    }

    public static Map<String, List<Integer>> unwrapMappings(String packetName, String mapping) {
        final Map<String, Map<String, List<Integer>>> cacheResult = CACHE.get(packetName);
        if (cacheResult != null) {
            final Map<String, List<Integer>> cacheResult1 = cacheResult.get(mapping);
            if (cacheResult1 != null) {
                return cacheResult1;
            }
        }
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
    }

    public static String findMapping(Map<String, List<Integer>> unwrapped, int ver) {
        return unwrapped.entrySet().stream()
                .filter(e -> e.getValue().contains(ver))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public static String findMapping(Class<?> clazz, int ver) {
        return findMapping(unwrapMappings(clazz.getSimpleName(), clazz.getAnnotation({utilPackage}.Reobfuscate.class).value()), ver);
    }

    public static String findMapping(Class<?> clazz, String mapping, int ver) {
        return findMapping(unwrapMappings(clazz.getSimpleName(), mapping), ver);
    }

    public static String findMapping(String packetName, Field field, int ver) {
        return findMapping(unwrapMappings(packetName, field.getAnnotation({utilPackage}.Reobfuscate.class).value()), ver);
    }
}