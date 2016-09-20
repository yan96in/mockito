package org.mockito.internal.creation.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.asm.Advice;
import org.mockito.invocation.MockHandler;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

public class MockMethodAdvice extends MockMethodDispatcher {

    private final WeakConcurrentMap<Object, MockMethodInterceptor> mocks;

    public MockMethodAdvice(WeakConcurrentMap<Object, MockMethodInterceptor> mocks) {
        this.mocks = mocks;
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

    private static Method find(Class<?> type, String signatue) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.toString().endsWith(signatue)) {
                return method;
            }
        }
        throw new IllegalStateException();
    }

    @Override
    public Callable<?> handle(Object mock, Class<?> origin, String signature, Object[] arguments) throws Throwable {
        MockMethodInterceptor interceptor = mocks.get(mock);
        if (interceptor == null) {
            return null;
        }
        SuperMethodCall superMethodCall = new SuperMethodCall();
        Object mocked = interceptor.doIntercept(mock,
                find(origin, signature),
                arguments,
                superMethodCall);
        return superMethodCall.represent(mocked);
    }

    static class SuperMethodCall implements InterceptedInvocation.SuperMethod {

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
}
