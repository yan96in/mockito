package org.mockito.internal.creation.bytebuddy;

import com.blogspot.mydailyjava.weaklockfree.WeakConcurrentMap;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.InternalMockHandler;
import org.mockito.internal.configuration.plugins.Plugins;
import org.mockito.internal.creation.instance.Instantiator;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.mock.SerializableMode;
import org.mockito.plugins.MockMaker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.mockito.internal.util.StringJoiner.join;

public class InlineByteBuddyMockMaker implements MockMaker {

    private final Instrumentation instrumentation;

    private final InlineBytecodeGenerator bytecodeGenerator;

    private final WeakConcurrentMap<Object, MockMethodInterceptor> mocks = new WeakConcurrentMap.WithInlinedExpunction<Object, MockMethodInterceptor>();

    public InlineByteBuddyMockMaker() {
        try {
            instrumentation = ByteBuddyAgent.install();
            File boot = File.createTempFile("mockitoboot", "jar");
            boot.deleteOnExit();
            JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(boot));
            try {
                outputStream.putNextEntry(new JarEntry(MockMethodDispatcher.class.getName().replace('.', '/') + ".class"));
                outputStream.write(ClassFileLocator.ForClassLoader.read(MockMethodDispatcher.class).resolve());
                outputStream.closeEntry();
            } finally {
                outputStream.close();
            }
            instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(boot));
            bytecodeGenerator = new InlineBytecodeGenerator(instrumentation, mocks);
        } catch (IOException exception) {
            throw new MockitoException("Cannot apply self-instrumentation on current VM", exception);
        }
    }

    @Override
    public <T> T createMock(MockCreationSettings<T> settings, MockHandler handler) {
        Class<? extends T> type = bytecodeGenerator.generateMockClass(mockWithFeaturesFrom(settings));

        Instantiator instantiator = Plugins.getInstantiatorProvider().getInstantiator(settings);
        try {
            T instance = instantiator.newInstance(type);
            mocks.put(instance, new MockMethodInterceptor(asInternalMockHandler(handler), settings));
            return instance;
        } catch (org.mockito.internal.creation.instance.InstantiationException e) {
            throw new MockitoException("Unable to create mock instance of type '" + type.getSimpleName() + "'", e);
        }
    }

    private static <T> MockFeatures<T> mockWithFeaturesFrom(MockCreationSettings<T> settings) {
        return MockFeatures.withMockFeatures(
                settings.getTypeToMock(),
                settings.getExtraInterfaces(),
                settings.getSerializableMode() == SerializableMode.ACROSS_CLASSLOADERS
        );
    }

    private static InternalMockHandler<?> asInternalMockHandler(MockHandler handler) {
        if (!(handler instanceof InternalMockHandler)) {
            throw new MockitoException(join(
                    "At the moment you cannot provide own implementations of MockHandler.",
                    "Please see the javadocs for the MockMaker interface.",
                    ""
            ));
        }
        return (InternalMockHandler<?>) handler;
    }

    @Override
    public MockHandler getHandler(Object mock) {
        MockMethodInterceptor interceptor = mocks.get(mock);
        if (interceptor == null) {
            return null;
        } else {
            return interceptor.handler;
        }
    }

    @Override
    public void resetMock(Object mock, MockHandler newHandler, MockCreationSettings settings) {
        mocks.put(mock, new MockMethodInterceptor(asInternalMockHandler(newHandler), settings));
    }

    @Override
    public TypeMockability isTypeMockable(final Class<?> type) {
        return new TypeMockability() {
            @Override
            public boolean mockable() {
                return instrumentation.isRetransformClassesSupported() && instrumentation.isModifiableClass(type);
            }

            @Override
            public String nonMockableReason() {
                if (instrumentation.isRetransformClassesSupported()) {
                    return "VM does not support redefinition of " + type;
                } else {
                    return "Current VM does not not support retransformation";
                }
            }
        };
    }
}
