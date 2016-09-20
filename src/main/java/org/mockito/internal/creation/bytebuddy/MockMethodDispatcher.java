package org.mockito.internal.creation.bytebuddy;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public abstract class MockMethodDispatcher {

    private static final AtomicReference<MockMethodDispatcher> INSTANCE = new AtomicReference<MockMethodDispatcher>();

    public static MockMethodDispatcher get() {
        return INSTANCE.get();
    }

    public static void set(MockMethodDispatcher dispatcher) {
        if (INSTANCE.getAndSet(dispatcher) != null) {
            System.err.println("Overriding previous dispatcher!"); // TODO
        }
    }

    public abstract Callable<?> handle(Object mock, Class<?> origin, String signature, Object[] arguments) throws Throwable;

    public abstract Object handle(Object mock, Method origin, Object[] arguments, Object fallback) throws Throwable;
}
