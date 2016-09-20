package org.mockito.internal.creation.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.Implementation;
import org.mockito.exceptions.base.MockitoException;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.isVirtual;

public class InlineBytecodeGenerator implements MockEngine, ClassFileTransformer {

    private final Instrumentation instrumentation;

    private final ByteBuddy byteBuddy;

    private final Set<Class<?>> mocked;

    final MockMethodAdvice advice;

    public InlineBytecodeGenerator(Instrumentation instrumentation, WeakConcurrentMap<Object, MockMethodInterceptor> mocks) {
        this.instrumentation = instrumentation;
        byteBuddy = new ByteBuddy()
                .with(TypeValidation.DISABLED)
                .with(Implementation.Context.Disabled.Factory.INSTANCE);
        mocked = new HashSet<Class<?>>();
        advice = new MockMethodAdvice(mocks);
        MockMethodDispatcher.set(advice);
        instrumentation.addTransformer(this, true);
    }

    @Override // TODO: think of better locking!
    public synchronized <T> Class<? extends T> generateMockClass(MockFeatures<T> features) {
        // TODO: Does not work for abstract types, serializable types, additional interfaces!
        Class<?> type = features.mockedType;
        Set<Class<?>> types = new HashSet<Class<?>>();
        do {
            if (mocked.add(type)) {
                types.add(type);
            }
            type = type.getSuperclass();
        } while (type != null);
        try {
            instrumentation.retransformClasses(types.toArray(new Class<?>[types.size()]));
        } catch (UnmodifiableClassException exception) {
            throw new MockitoException("Could not modify class", exception);
        }
        return features.mockedType;
    }

    @Override
    public byte[] transform(ClassLoader loader,
                            String className,
                            Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) throws IllegalClassFormatException {
        if (classBeingRedefined == null || !mocked.contains(classBeingRedefined)) {
            return null;
        } else {
            try {
                return byteBuddy.redefine(classBeingRedefined, ClassFileLocator.Simple.of(classBeingRedefined.getName(), classfileBuffer))
                        .visit(Advice.to(MockMethodAdvice.class).on(isVirtual()))
                        .make()
                        .getBytes();
            } catch (Throwable throwable) {
                return null;
            }
        }
    }
}
