import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class MappingUtils {
    private static final Map<String, Map<String, Map<String, List<Integer>>>> CACHE = new ConcurrentHashMap<>();

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
            mappings.computeIfAbsent(sides[1].replace('/', '.'), key -> new ArrayList<>())
                    .addAll(Arrays.asList(sides[0].split(",")).stream().map(Integer::parseInt).collect(Collectors.toList()));
        }
        final Map<String, List<Integer>> finalizedMap = Collections.unmodifiableMap(mappings);
        CACHE.computeIfAbsent(packetName, key -> new ConcurrentHashMap<>()).put(mapping, finalizedMap);
        return finalizedMap;
    }

    public static String findMapping(Map<String, List<Integer>> unwrapped, int ver) {
        return unwrapped.entrySet().stream()
                .filter(e -> e.getValue().contains(ver))
                .findFirst()
                .map(Map.Entry::getKey)
                .get();
    }

    public static String findMapping(Class<?> clazz, int ver) {
        return findMapping(unwrapMappings(clazz.getSimpleName(), clazz.getAnnotation({utilPackage}.Reobfuscate.class).value()), ver);
    }

    public static String findMapping(String packetName, Field field, int ver) {
        return findMapping(unwrapMappings(packetName, field.getAnnotation({utilPackage}.Reobfuscate.class).value()), ver);
    }
}