package io.sniffy.util;

import sun.misc.Unsafe;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.concurrent.locks.Lock;

public class ReflectionUtil {

    public final static Unsafe UNSAFE;

    static {
        Unsafe unsafe = null;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            // TODO: what do we do with drunken sailor?
        }
        UNSAFE = unsafe;
    }

    /**
     * FakeAccessibleObject class has similar layout as {@link AccessibleObject} and can be used for calculating offsets
     */
    private static class FakeAccessibleObject implements AnnotatedElement {

        /**
         * The Permission object that is used to check whether a client
         * has sufficient privilege to defeat Java language access
         * control checks.
         */
        static final private java.security.Permission ACCESS_PERMISSION =
                new ReflectPermission("suppressAccessChecks");

        // Indicates whether language-level access checks are overridden
        // by this object. Initializes to "false". This field is used by
        // Field, Method, and Constructor.
        //
        // NOTE: for security purposes, this field must not be visible
        // outside this package.
        boolean override;

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }

        // Reflection factory used by subclasses for creating field,
        // method, and constructor accessors. Note that this is called
        // very early in the bootstrapping process.
        static final Object reflectionFactory = new Object();

        volatile Object securityCheckCache;

    }

    public static boolean setAccessible(AccessibleObject ao) {

        if (JVMUtil.getVersion() >= 16) {

            try {
                long overrideOffset = UNSAFE.objectFieldOffset(FakeAccessibleObject.class.getDeclaredField("override"));
                UNSAFE.putBoolean(ao, overrideOffset, true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }

            if (ao.isAccessible()) {
                return true;
            }
            return false;
        }

        ao.setAccessible(true);
        return true;

    }

    public static <T, V> boolean setField(String className, T instance, String fieldName, V value) {
        return setField(className, instance, fieldName, value, null);
    }

    public static <T, V> boolean setField(String className, T instance, String fieldName, V value, String lockFieldName) {
        try {
            //noinspection unchecked
            return setField((Class<T>) Class.forName(className), instance, fieldName, value, lockFieldName);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static <T, V> boolean setField(Class<T> clazz, T instance, String fieldName, V value) {
        return setField(clazz, instance, fieldName, value, null);
    }

    public static <T, V> boolean setFields(Class<T> clazz, T instance, Class<V> valueClass, V value) {
        boolean fieldsFound = false;
        boolean result = true;
        for (Field field: clazz.getDeclaredFields()) {
            if (field.getType().equals(valueClass)) {
                fieldsFound = true;
                result = result && setField(clazz, instance, field.getName(), value, null);
            }
        }
        return fieldsFound && result;
    }

    public static <T, V> boolean setField(Class<T> clazz, T instance, String fieldName, V value, String lockFieldName) {

        //noinspection TryWithIdenticalCatches
        try {
            Field instanceField = clazz.getDeclaredField(fieldName);

            /*if (JVMUtil.getVersion() >= 16) {
                long fieldOffset = null == instance ? UNSAFE.staticFieldOffset(instanceField) : UNSAFE.objectFieldOffset(instanceField);
                // TODO: acquire lock
                // TODO: use putvolatile if required
                if (instanceField.getType() == Boolean.TYPE && value instanceof Boolean) {
                    UNSAFE.putBoolean(instance, fieldOffset, (Boolean) value);
                } else if (instanceField.getType() == Integer.TYPE && value instanceof Number) {
                    UNSAFE.putInt(instance, fieldOffset, ((Number) value).intValue());
                } else if (instanceField.getType() == Long.TYPE && value instanceof Number) {
                    UNSAFE.putLong(instance, fieldOffset, ((Number) value).longValue());
                } else if (instanceField.getType() == Short.TYPE && value instanceof Number) {
                    UNSAFE.putShort(instance, fieldOffset, ((Number) value).shortValue());
                } else if (instanceField.getType() == Byte.TYPE && value instanceof Number) {
                    UNSAFE.putByte(instance, fieldOffset, ((Number) value).byteValue());
                } else if (instanceField.getType() == Double.TYPE && value instanceof Number) {
                    UNSAFE.putDouble(instance, fieldOffset, ((Number) value).doubleValue());
                } else if (instanceField.getType() == Float.TYPE && value instanceof Number) {
                    UNSAFE.putFloat(instance, fieldOffset, ((Number) value).floatValue());
                } else if (instanceField.getType() == Character.TYPE && value instanceof Character) {
                    UNSAFE.putChar(instance, fieldOffset, (Character) value);
                }
                UNSAFE.putObject(instance == null ? UNSAFE.staticFieldBase(instanceField) : instance, fieldOffset, value);
                return true;
            }*/

            if (!instanceField.isAccessible()) {
                //instanceField.setAccessible(true);
                setAccessible(instanceField);
            }

            Field modifiersField = getModifiersField();
            //modifiersField.setAccessible(true);
            setAccessible(modifiersField);

            modifiersField.setInt(instanceField, instanceField.getModifiers() & ~Modifier.FINAL);

            if (null != lockFieldName) {

                Field lockField = clazz.getDeclaredField(lockFieldName);

                if (!lockField.isAccessible()) {
                    //lockField.setAccessible(true);
                    setAccessible(lockField);
                }

                Object lockObject = lockField.get(instance);

                if (lockObject instanceof Lock) {

                    Lock lock = (Lock) lockObject;

                    try {
                        lock.lock();
                        instanceField.set(instance, value);
                        return true;
                    } finally {
                        lock.unlock();
                    }

                } else {

                    //noinspection SynchronizationOnLocalVariableOrMethodParameter
                    synchronized (lockObject) {
                        instanceField.set(instance, value);
                        return true;
                    }

                }

            } else {
                instanceField.set(instance, value);
                return true;
            }

        } catch (NoSuchFieldException e) {
            return false;
        } catch (IllegalAccessException e) {
            return false;
        }

    }

    public static <T, V> V getField(String className, T instance, String fieldName) throws IllegalAccessException, NoSuchFieldException, ClassNotFoundException {
        return getField(className, instance, fieldName, null);
    }

    public static <T, V> V getField(String className, T instance, String fieldName, String lockFieldName) throws ClassNotFoundException, NoSuchFieldException, IllegalAccessException {
        //noinspection unchecked
        return getField((Class<T>) Class.forName(className), instance, fieldName, lockFieldName);
    }

    public static <T, V> V getField(Class<T> clazz, T instance, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Object field = getField(clazz, instance, fieldName, null);
        return (V) field;
    }

    public static <T, V> V getField(Class<T> clazz, T instance, String fieldName, String lockFieldName) throws NoSuchFieldException, IllegalAccessException {

        Field instanceField = clazz.getDeclaredField(fieldName);

        /*if (JVMUtil.getVersion() >= 16) {
            long fieldOffset = null == instance ? UNSAFE.staticFieldOffset(instanceField) : UNSAFE.objectFieldOffset(instanceField);

            // TODO: acquire lock
            // TODO: use getvolatile if required
            if (instanceField.getType() == Boolean.TYPE) {
                UNSAFE.getBoolean(instance, fieldOffset);
            } else if (instanceField.getType() == Integer.TYPE) {
                UNSAFE.getInt(instance, fieldOffset);
            } else if (instanceField.getType() == Long.TYPE) {
                UNSAFE.getLong(instance, fieldOffset);
            } else if (instanceField.getType() == Short.TYPE) {
                UNSAFE.getShort(instance, fieldOffset);
            } else if (instanceField.getType() == Byte.TYPE) {
                UNSAFE.getByte(instance, fieldOffset);
            } else if (instanceField.getType() == Double.TYPE) {
                UNSAFE.getDouble(instance, fieldOffset);
            } else if (instanceField.getType() == Float.TYPE) {
                UNSAFE.getFloat(instance, fieldOffset);
            } else if (instanceField.getType() == Character.TYPE) {
                UNSAFE.getChar(instance, fieldOffset);
            }

            //noinspection unchecked
            return (V) UNSAFE.getObject(null == instance ? UNSAFE.staticFieldOffset(instanceField) : instance, fieldOffset);

        }*/

        if (!instanceField.isAccessible()) {
            //instanceField.setAccessible(true);
            setAccessible(instanceField);
        }

        Field modifiersField = getModifiersField();
        //modifiersField.setAccessible(true);
        setAccessible(modifiersField);
        modifiersField.setInt(instanceField, instanceField.getModifiers() & ~Modifier.FINAL);

        if (null != lockFieldName) {

            Field lockField = clazz.getDeclaredField(lockFieldName);

            if (!lockField.isAccessible()) {
                lockField.setAccessible(true);
            }

            Object lockObject = lockField.get(instance);

            if (lockObject instanceof Lock) {

                Lock lock = (Lock) lockObject;

                try {
                    lock.lock();
                    //noinspection unchecked
                    return (V) instanceField.get(instance);
                } finally {
                    lock.unlock();
                }

            } else {

                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (lockObject) {
                    //noinspection unchecked
                    return (V) instanceField.get(instance);
                }

            }

        } else {
            //noinspection unchecked
            return (V) instanceField.get(instance);
        }

    }

    private static Field getModifiersField() throws NoSuchFieldException {
        try {
            return Field.class.getDeclaredField("modifiers");
        } catch (NoSuchFieldException e) {
            try {
                Method getDeclaredFields0 = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
                //getDeclaredFields0.setAccessible(true);
                setAccessible(getDeclaredFields0);
                Field[] fields = (Field[]) getDeclaredFields0.invoke(Field.class, false);
                for (Field field : fields) {
                    if ("modifiers".equals(field.getName())) {
                        return field;
                    }
                }
            } catch (Exception ex) {
                ExceptionUtil.addSuppressed(e, ex);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    public static <R, T> R invokeMethod(
            Class<T> clazz, T instance,
            String methodName,
            @SuppressWarnings("unused") Class<R> returnClass
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (R) invokeMethod(clazz, instance, methodName);
    }

    public static Object invokeMethod(
            Class<?> clazz, Object instance, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return method(clazz, methodName).invoke(instance);
    }

    @SuppressWarnings("unchecked")
    public static <R, T, P1> R invokeMethod(
            Class<T> clazz, T instance,
            String methodName,
            Class<P1> argument1Type, P1 argument1,
            @SuppressWarnings("unused") Class<R> returnClass
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (R) invokeMethod(clazz, instance, methodName, argument1Type, argument1);
    }

    public static <P1> Object invokeMethod(
            Class<?> clazz, Object instance,
            String methodName,
            Class<P1> argument1Type, Object argument1
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = method(clazz, methodName, argument1Type);
        return method.invoke(instance, argument1);
    }

    @SuppressWarnings("unchecked")
    public static <R, T, P1, P2> R invokeMethod(
            Class<T> clazz, T instance,
            String methodName,
            Class<P1> argument1Type, P1 argument1,
            Class<P2> argument2Type, P2 argument2,
            @SuppressWarnings("unused") Class<R> returnClass
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (R) invokeMethod(clazz, instance, methodName, argument1Type, argument1, argument2Type, argument2);
    }

    public static <P1, P2> Object invokeMethod(
            Class<?> clazz, Object instance,
            String methodName,
            Class<P1> argument1Type, Object argument1,
            Class<P2> argument2Type, Object argument2
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = method(clazz, methodName, argument1Type, argument2Type);
        return method.invoke(instance, argument1, argument2);
    }

    @SuppressWarnings("unchecked")
    public static <R, T, P1, P2, P3> R invokeMethod(
            Class<T> clazz, T instance,
            String methodName,
            Class<P1> argument1Type, P1 argument1,
            Class<P2> argument2Type, P2 argument2,
            Class<P3> argument3Type, P3 argument3,
            @SuppressWarnings("unused") Class<R> returnClass
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return (R) invokeMethod(clazz, instance, methodName, argument1Type, argument1, argument2Type, argument2, argument3Type, argument3);
    }

    public static <P1, P2, P3> Object invokeMethod(
            Class<?> clazz, Object instance,
            String methodName,
            Class<P1> argument1Type, Object argument1,
            Class<P2> argument2Type, Object argument2,
            Class<P3> argument3Type, Object argument3
    ) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = method(clazz, methodName, argument1Type, argument2Type, argument3Type);
        return method.invoke(instance, argument1, argument2, argument3);
    }

    public static Method method(Class<?> clazz, String methodName, Class<?>... argumentTypes) throws NoSuchMethodException {
        Method method = clazz.getDeclaredMethod(methodName, argumentTypes);
        //method.setAccessible(true);
        setAccessible(method);
        return method;
    }

}
