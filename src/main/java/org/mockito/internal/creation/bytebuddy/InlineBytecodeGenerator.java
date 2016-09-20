package org.mockito.internal.creation.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentSet;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.scaffold.TypeValidation;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodDelegation;
import org.mockito.exceptions.base.MockitoException;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class InlineBytecodeGenerator implements BytecodeGenerator, ClassFileTransformer {

    private final Instrumentation instrumentation;

    private final ByteBuddy byteBuddy;

    private final WeakConcurrentSet<Class<?>> mocked;

    final MockMethodAdvice advice;

    private final BytecodeGenerator subclassEngine = new TypeCachingBytecodeGenerator(new SubclassBytecodeGenerator(true,
            MethodDelegation.to(MockMethodAdvice.class).filter(named("interceptAbstract"))), false);

    public InlineBytecodeGenerator(Instrumentation instrumentation, WeakConcurrentMap<Object, MockMethodInterceptor> mocks) {
        this.instrumentation = instrumentation;
        byteBuddy = new ByteBuddy()
                .with(TypeValidation.DISABLED)
                .with(Implementation.Context.Disabled.Factory.INSTANCE);
        mocked = new WeakConcurrentSet<Class<?>>(WeakConcurrentSet.Cleaner.INLINE);
        advice = new MockMethodAdvice(mocks);
        MockMethodDispatcher.set(advice);
        instrumentation.addTransformer(this, true);
    }

    @Override // TODO: think of better locking!
    public synchronized <T> Class<? extends T> mockClass(MockFeatures<T> features) {
        Set<Class<?>> types = new HashSet<Class<?>>();
        Class<?> type = features.mockedType;
        do {
            if (mocked.add(type)) {
                types.add(type);
            }
            addInterfaces(types, type.getInterfaces());
            type = type.getSuperclass();
        } while (type != null);
        if (!types.isEmpty()) {
            try {
                instrumentation.retransformClasses(types.toArray(new Class<?>[types.size()]));
            } catch (UnmodifiableClassException exception) {
                throw new MockitoException("Could not modify all classes " + types, exception);
            }
        }
        Class<? extends T> mockedType = features.mockedType;
        if (!features.interfaces.isEmpty() || features.crossClassLoaderSerializable || Modifier.isAbstract(features.mockedType.getModifiers())) {
            mockedType = subclassEngine.mockClass(features);
        }
        return mockedType;
    }

    private void addInterfaces(Set<Class<?>> types, Class<?>[] interfaces) {
        for (Class<?> type : interfaces) {
            if (mocked.add(type)) {
                types.add(type);
            }
            addInterfaces(types, type.getInterfaces());
        }
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
                        .visit(Advice.to(MockMethodAdvice.class).on(isVirtual().and(not(isBridge().or(isHashCode()).or(isEquals())))))
                        .visit(Advice.to(MockMethodAdvice.ForHashCode.class).on(isHashCode()))
                        .visit(Advice.to(MockMethodAdvice.ForEquals.class).on(isEquals()))
                        .make()
                        .getBytes();
            } catch (Throwable throwable) {
                return null;
            }
        }
    }
}
