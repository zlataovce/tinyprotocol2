import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class Reflect {
    public static final Object UNSAFE;

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
        Object unsafe0 = null;
        try {
            final Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeField.setAccessible(true);
            unsafe0 = theUnsafeField.get(null);
        } catch (Throwable ignored) {
        }
        UNSAFE = unsafe0;
    }

    private Reflect() {
    }

    public static Class<?> getClassSafe(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
        }
        return null;
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
                if (UNSAFE != null) {
                    try {
                        return ((sun.misc.Unsafe) UNSAFE).allocateInstance(clazz);
                    } catch (Throwable ignored2) {
                    }
                }
            }
        }
        return null;
    }

    public static Object construct(Class<?> clazz, Object... args) {
        final Class<?>[] argClasses = Arrays.stream(args)
                .map(Object::getClass)
                .toArray(Class[]::new);
        Constructor<?> constructor = null;
        try {
            constructor = clazz.getConstructor(argClasses);
        } catch (Throwable ignored1) {
            Class<?> clazz1 = clazz;
            do {
                try {
                    constructor = clazz1.getDeclaredConstructor(argClasses);
                } catch (Throwable ignored2) {
                }
            } while ((clazz1 = clazz1.getSuperclass()) != null && clazz1 != Object.class && constructor == null);
        }
        if (constructor == null) {
            return null;
        }
        try {
            return constructor.newInstance(args);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Method getMethodSafe(Class<?> clazz, String name, Class<?>... args) {
        Method method = null;
        try {
            method = clazz.getMethod(name, args);
        } catch (Throwable ignored1) {
            Class<?> clazz1 = clazz;
            do {
                try {
                    method = clazz1.getDeclaredMethod(name, args);
                } catch (Throwable ignored2) {
                }
            } while ((clazz1 = clazz1.getSuperclass()) != null && clazz1 != Object.class && method == null);
        }
        return method;
    }

    public static Object fastInvoke(Method method, Object instance, Object... args) {
        try {
            return method.invoke(instance, args);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static Field getFieldSafe(Class<?> clazz, String name) {
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

    public static Object getField(Object instance, String name) {
        try {
            return getFieldSafe(instance.getClass(), name).get(instance);
        } catch (Throwable ignored) {
        }
        return null;
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
                        if (UNSAFE != null) {
                            try {
                                final sun.misc.Unsafe theUnsafe = (sun.misc.Unsafe) UNSAFE;

                                final Object ufo = instance != null ? instance : theUnsafe.staticFieldBase(field);
                                final long offset = instance != null ? theUnsafe.objectFieldOffset(field) : theUnsafe.staticFieldOffset(field);

                                theUnsafe.putObject(ufo, offset, value);
                            } catch (Throwable ignored3) {
                            }
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
