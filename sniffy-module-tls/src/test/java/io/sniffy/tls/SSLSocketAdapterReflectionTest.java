package io.sniffy.tls;

import org.junit.Test;

import javax.net.ssl.SSLSocket;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class SSLSocketAdapterReflectionTest {

    private static Collection<Method> getDeclaredMethods(Class<?> clazz) {
        Set<Method> declaredMethods = new HashSet<>(Arrays.asList(clazz.getDeclaredMethods()));
        Class<?> superclass = clazz.getSuperclass();
        if (null != superclass && superclass.getName().startsWith("io.sniffy")) {
            declaredMethods.addAll(getDeclaredMethods(superclass));
        }
        return declaredMethods;
    }

    @Test
    public void testAllMethodsDelegated() {

        Arrays.stream(SSLSocket.class.getDeclaredMethods())
                .filter(m -> !Modifier.isFinal(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers()))
                .forEach(m -> {
                    if (getDeclaredMethods(SSLSocketAdapter.class).stream().noneMatch(w ->
                            m.getName().equals(w.getName()) &&
                                    Arrays.equals(m.getParameterTypes(), w.getParameterTypes())
                    )) {
                        fail("Method " + m + " is not wrapped by Sniffy");
                    }
                });

    }

}