package org.mockito.internal.creation.bytebuddy;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class MockMethodDispatcher {

    private static final ConcurrentMap<String, MockMethodDispatcher> INSTANCE = new ConcurrentHashMap<String, MockMethodDispatcher>();

    public static MockMethodDispatcher get(String identifier) {
        return INSTANCE.get(identifier);
    }

    public static void set(String identifier, MockMethodDispatcher dispatcher) {
        INSTANCE.putIfAbsent(identifier, dispatcher);
    }

    public abstract Callable<?> handle(Object instance, Method origin, Object[] arguments) throws Throwable;

    public abstract Object handle(Object instance, Method origin, Object[] arguments, Object fallback) throws Throwable;

    public abstract boolean isMock(Object instance);
}
