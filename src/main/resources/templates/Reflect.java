import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class Reflect {
    static {
        try {
            final Method getModuleMethod = Class.class.getDeclaredMethod("getModule");
            final Object java_base = getModuleMethod.invoke(Field.class);
            final Object unnamed = getModuleMethod.invoke(Reflect.class);
            final Method addOpensMethod = getModuleMethod.getReturnType().getDeclaredMethod("addOpens", String.class, getModuleMethod.getReturnType());
            addOpensMethod.invoke(java_base, "java.lang.reflect", unnamed);
            addOpensMethod.invoke(java_base, "java.util", unnamed);
        } catch (Throwable ignored) {
        }
    }

    private Reflect() {
    }

    public static Class<?> getClassSafe(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
        }
        throw new RuntimeException("Could not retrieve class " + name);
    }

    public static Object construct(Class<?> clazz) {
        try {
            final Constructor<?> c = clazz.getConstructor();
            c.setAccessible(true);
            return c.newInstance();
        } catch (Throwable ignored) {
            try {
                return sun.reflect.ReflectionFactory.getReflectionFactory().newConstructorForSerialization(clazz, Object.class.getDeclaredConstructor()).newInstance();
            } catch (Throwable ignored1) {
                try {
                    final Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafeField.setAccessible(true);
                    return ((sun.misc.Unsafe) theUnsafeField.get(null)).allocateInstance(clazz);
                } catch (Throwable ignored2) {
                }
            }
        }
        throw new RuntimeException("Could not construct " + clazz.getName());
    }

    public static Field getField(Class<?> clazz, String name) {
        Field field = null;
        try {
            field = clazz.getField(name);
        } catch (Throwable ignored1) {
            Class<?> clazz1 = clazz;
            do {
                try {
                    field = clazz1.getDeclaredField(name);
                } catch (Throwable ignored2) {
                }
            } while ((clazz1 = clazz1.getSuperclass()) != null && clazz1 != Object.class && field == null);
        }
        return field;
    }

    public static void setField(Field field, Object instance, Object value) {
        try {
            field.setAccessible(true);
            if (Modifier.isFinal(field.getModifiers())) {
                final Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);

                try {
                    field.set(instance, value);
                } catch (Throwable ignored1) {
                    try {
                        final Method privateLookupInMethod = MethodHandles.class.getDeclaredMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
                        final MethodHandles.Lookup lookup = (MethodHandles.Lookup) privateLookupInMethod.invoke(null, Field.class, MethodHandles.lookup());
                        final Method findVarHandleMethod = MethodHandles.Lookup.class.getDeclaredMethod("findVarHandle", Class.class, String.class, Class.class);

                        final Object varHandle = findVarHandleMethod.invoke(lookup, Field.class, "modifiers", int.class);
                        final Method setMethod = varHandle.getClass().getDeclaredMethod("set", Object[].class);

                        setMethod.invoke(varHandle, new Object[] {field, field.getModifiers() & ~Modifier.FINAL});

                        field.set(instance, value);
                    } catch (Throwable ignored2) {
                        try {
                            final Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                            theUnsafeField.setAccessible(true);
                            final sun.misc.Unsafe theUnsafe = (sun.misc.Unsafe) theUnsafeField.get(null);

                            final Object ufo = instance != null ? instance : theUnsafe.staticFieldBase(field);
                            final long offset = instance != null ? theUnsafe.objectFieldOffset(field) : theUnsafe.staticFieldOffset(field);

                            theUnsafe.putObject(ufo, offset, value);
                        } catch (Throwable ignored3) {
                        }
                    }
                }
            } else {
                field.set(instance, value);
            }
        } catch (Throwable ignored) {
        }
    }
}