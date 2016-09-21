package org.mockito.internal.creation.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bind.annotation.*;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class MockMethodAdvice extends MockMethodDispatcher {

    private final WeakConcurrentMap<Object, MockMethodInterceptor> interceptors;

    public MockMethodAdvice(WeakConcurrentMap<Object, MockMethodInterceptor> interceptors) {
        this.interceptors = interceptors;
    }

    @Advice.OnMethodEnter(skipOn = Callable.class)
    private static Callable<?> enter(@Identifier String identifier,
                                     @Advice.This Object mock,
                                     @Advice.Origin Class<?> origin,
                                     @Advice.Origin String signature,
                                     @Advice.BoxedArguments Object[] arguments) throws Throwable {
        MockMethodDispatcher dispatcher = MockMethodDispatcher.get(identifier);
        if (dispatcher == null) {
            return null;
        } else {
            return dispatcher.handle(mock, origin, signature, arguments);
        }
    }

    @Advice.OnMethodExit
    private static void exit(@Advice.BoxedReturn(readOnly = false) Object returned,
                             @Advice.Enter Callable<?> mocked) throws Throwable {
        if (mocked != null) {
            returned = mocked.call();
        }
    }

    @RuntimeType
    public static Object intercept(@Identifier String identifier,
                                   @This Object mock,
                                   @StubValue Object stubValue,
                                   @SuperCall Callable<?> superCall,
                                   @Origin Method origin,
                                   @AllArguments Object[] arguments) throws Throwable {
        MockMethodDispatcher dispatcher = MockMethodDispatcher.get(identifier);
        if (dispatcher == null) {
            return stubValue;
        } else {
            return dispatcher.handle(mock, origin, arguments, superCall);
        }
    }

    @RuntimeType
    public static Object interceptAbstract(@Identifier String identifier,
                                           @This Object mock,
                                           @StubValue Object stubValue,
                                           @Origin Method origin,
                                           @AllArguments Object[] arguments) throws Throwable {
        MockMethodDispatcher dispatcher = MockMethodDispatcher.get(identifier);
        if (dispatcher == null) {
            return stubValue;
        } else {
            return dispatcher.handle(mock, origin, arguments, stubValue);
        }
    }

    @Override
    public Callable<?> handle(Object instance, Class<?> origin, String signature, Object[] arguments) throws Throwable {
        MockMethodInterceptor interceptor = interceptors.get(instance);
        if (interceptor == null) {
            return null;
        }
        RecordingSuperMethod recordingSuperMethod = new RecordingSuperMethod();
        Object mocked = interceptor.doIntercept(instance,
                asMethod(origin, signature), // TODO: Better lookup, should be cached!
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
    public Object handle(Object instance, Method origin, Object[] arguments, Object fallback) throws Throwable {
        MockMethodInterceptor interceptor = interceptors.get(instance);
        if (interceptor == null) {
            return fallback;
        } else {
            return interceptor.doIntercept(instance,
                    origin,
                    arguments,
                    RecordingSuperMethod.IsIllegal.INSTANCE);
        }
    }

    @Override
    public boolean isMock(Object instance) {
        return interceptors.containsKey(instance);
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
        private static boolean enter(@Identifier String id,
                                     @Advice.This Object self) {
            MockMethodDispatcher dispatcher = MockMethodDispatcher.get(id);
            return dispatcher != null && dispatcher.isMock(self);
        }

        @Advice.OnMethodExit
        private static void enter(@Advice.This Object self,
                                  @Advice.Return(readOnly = false) int hashCode,
                                  @Advice.Enter boolean skipped) {
            if (skipped) {
                hashCode = System.identityHashCode(self);
            }
        }
    }

    static class ForEquals {

        @Advice.OnMethodEnter(skipOn = Advice.DefaultValueOrTrue.class)
        private static boolean enter(@Identifier String identifier,
                                     @Advice.This Object self) {
            MockMethodDispatcher dispatcher = MockMethodDispatcher.get(identifier);
            return dispatcher != null && dispatcher.isMock(self);
        }

        @Advice.OnMethodExit
        private static void enter(@Advice.This Object self,
                                  @Advice.Argument(0) Object other,
                                  @Advice.Return(readOnly = false) boolean equals,
                                  @Advice.Enter boolean skipped) {
            if (skipped) {
                equals = self == other;
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Identifier {

    }
}
