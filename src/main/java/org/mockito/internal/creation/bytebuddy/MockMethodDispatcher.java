package org.mockito.internal.creation.bytebuddy;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public class MockMethodDispatcher {

    private static final AtomicReference<MockMethodDispatcher> INSTANCE = new AtomicReference<MockMethodDispatcher>();

    public static MockMethodDispatcher get() {
        return INSTANCE.get();
    }

    public static void set(MockMethodDispatcher dispatcher) {
        if (!INSTANCE.compareAndSet(null, dispatcher)) {
            throw new IllegalStateException("Mockito dispatcher already set");
        }
    }

    public Callable<?> handle(Object mock, Class<?> origin, String signature, Object[] arguments) throws Throwable {
        return null;
    }
}
