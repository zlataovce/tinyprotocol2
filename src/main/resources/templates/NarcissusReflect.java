import {narcissusPackage}.Narcissus;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public final class Reflect {
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
        }
        return Narcissus.allocateInstance(clazz);
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
                Narcissus.setField(instance, field, value);
            } else {
                field.set(instance, value);
            }
        } catch (Throwable ignored) {
        }
    }
}
