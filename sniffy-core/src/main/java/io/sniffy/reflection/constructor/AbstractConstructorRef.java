package io.sniffy.reflection.constructor;

import io.sniffy.reflection.ResolvableRef;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;

public abstract class AbstractConstructorRef<C> implements ResolvableRef {

    protected final Constructor constructor;
    protected final MethodHandle methodHandle;
    protected final Throwable throwable;

    public AbstractConstructorRef(Constructor constructor, MethodHandle methodHandle, Throwable throwable) {
        this.constructor = constructor;
        this.methodHandle = methodHandle;
        this.throwable = throwable;
    }

    public boolean isResolved() {
        return null != methodHandle;
    }

    public MethodHandle getMethodHandle() {
        return methodHandle;
    }

    public Throwable getThrowable() {
        return throwable;
    }

}
