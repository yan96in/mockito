package org.mockito.internal.creation.bytebuddy;

import org.junit.Test;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.handler.MockHandlerImpl;
import org.mockito.internal.stubbing.answers.DoesNothing;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.mock.MockCreationSettings;
import org.mockito.plugins.MockMaker;

public class InliningByteBuddyMockMakerTest /* extends AbstractByteBuddyMockMakerTest */ {

    /*
    public InliningByteBuddyMockMakerTest() {
        super(new InlineByteBuddyMockMaker());
    }
    */

    private final MockMaker mockMaker = new InlineByteBuddyMockMaker();

    @Test
    public void should_create_mock_from_class() throws Exception {
        MockCreationSettings<Sample> settings = settingsFor(Sample.class);
        Sample proxy = mockMaker.createMock(settings, new MockHandlerImpl<Sample>(settings));
        System.out.println(proxy.foo());
    }

    private static final class Sample {

        public final String foo() {
            return "foo";
        }
    }

    private static <T> MockCreationSettings<T> settingsFor(Class<T> type, Class<?>... extraInterfaces) {
        MockSettingsImpl<T> mockSettings = new MockSettingsImpl<T>();
        mockSettings.setTypeToMock(type);
        mockSettings.defaultAnswer(new Returns("bar"));
        if (extraInterfaces.length > 0) mockSettings.extraInterfaces(extraInterfaces);
        return mockSettings;
    }
}
