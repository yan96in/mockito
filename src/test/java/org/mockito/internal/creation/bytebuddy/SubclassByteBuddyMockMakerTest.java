package org.mockito.internal.creation.bytebuddy;

import net.bytebuddy.ByteBuddy;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.InternalMockHandler;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.creation.bytebuddy.MockMethodInterceptor.MockAccess;
import org.mockito.internal.stubbing.InvocationContainer;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockMaker;
import org.mockito.stubbing.Answer;
import org.mockitoutil.ClassLoaders;
import org.objenesis.ObjenesisStd;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockitoutil.ClassLoaders.coverageTool;

public class SubclassByteBuddyMockMakerTest extends AbstractByteBuddyMockMakerTest {

    public SubclassByteBuddyMockMakerTest() {
        super(new SubclassByteBuddyMockMaker());
    }

    @Override
    protected Class<?> mockTypeOf(Class<?> type) {
        return type.getSuperclass();
    }
}
