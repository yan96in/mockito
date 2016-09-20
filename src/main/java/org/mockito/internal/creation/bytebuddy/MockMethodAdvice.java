package org.mockito.internal.creation.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class MockMethodAdvice extends MockMethodDispatcher {

    private final WeakConcurrentMap<Object, MockMethodInterceptor> interceptors;

    public MockMethodAdvice(WeakConcurrentMap<Object, MockMethodInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Advice.OnMethodEnter(skipOn = Callable.class)
    private static Callable<?> enter(@Advice.This Object mock,
                                     @Advice.Origin Class<?> origin,
                                     @Advice.Origin String signature,
                                     @Advice.BoxedArguments Object[] arguments) throws Throwable {
        return MockMethodDispatcher.get().handle(mock, origin, signature, arguments);
    }

    @Advice.OnMethodExit
    private static void exit(@Advice.Enter Callable<?> mocked, @Advice.BoxedReturn(readOnly = false) Object returned) throws Throwable {
        if (mocked != null) {
            returned = mocked.call();
        }
    }

    @RuntimeType
    public static Object interceptAbstract(@This Object mock,
                                           @StubValue Object stubValue,
                                           @Origin Method origin,
                                           @AllArguments Object[] arguments) throws Throwable {
        return MockMethodDispatcher.get().handle(mock, origin, arguments, stubValue);
    }

    @Override
    public Callable<?> handle(Object mock, Class<?> origin, String signature, Object[] arguments) throws Throwable {
        MockMethodInterceptor interceptor = interceptors.get(mock);
        if (interceptor == null) {
            return null;
        }
        RecordingSuperMethod recordingSuperMethod = new RecordingSuperMethod();
        Object mocked = interceptor.doIntercept(mock,
                asMethod(origin, signature), // TODO: Should be cached!
                arguments,
                recordingSuperMethod);
        return recordingSuperMethod.represent(mocked);
    }

    private static Method asMethod(Class<?> type, String signatue) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.toString().equals(signatue)) {
                return method;
            }
        }
        throw new IllegalStateException("Cannot find " + signatue + " on " + type);
    }

    @Override
    public Object handle(Object mock, Method origin, Object[] arguments, Object fallback) throws Throwable {
        MockMethodInterceptor interceptor = interceptors.get(mock);
        if (interceptor == null) {
            return fallback;
        } else {
            return interceptor.doIntercept(mock,
                    origin,
                    arguments,
                    RecordingSuperMethod.IsIllegal.INSTANCE);
        }
    }

    static class RecordingSuperMethod implements InterceptedInvocation.SuperMethod {

        private boolean invoked;

        @Override
        public boolean isInvokable() {
            return true;
        }

        @Override
        public Void invoke() throws Throwable {
            invoked = true;
            return null;
        }

        Callable<?> represent(Object mocked) {
            if (invoked) {
                return null;
            } else {
                return new SkipReturn(mocked);
            }
        }
    }

    static class SkipReturn implements Callable<Object> {

        private final Object returned;

        SkipReturn(Object returned) {
            this.returned = returned;
        }

        @Override
        public Object call() {
            return returned;
        }
    }

    static class ForHashCode {

        @Advice.OnMethodEnter(skipOn = Advice.DefaultValueOrTrue.class)
        private static boolean enter() {
            return true;
        }

        @Advice.OnMethodExit
        private static void enter(@Advice.This Object self, @Advice.Return int hashCode) {
            hashCode = System.identityHashCode(self);
        }
    }

    static class ForEquals {

        @Advice.OnMethodEnter(skipOn = Advice.DefaultValueOrTrue.class)
        private static boolean enter() {
            return true;
        }

        @Advice.OnMethodExit
        private static void enter(@Advice.Return boolean equals, @Advice.This Object self, @Advice.Argument(0) Object other) {
            equals = self == other;
        }
    }
}
