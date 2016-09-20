package org.mockito.internal.creation.bytebuddy;

public interface MockEngine {

    <T> Class<? extends T> generateMockClass(MockFeatures<T> features);
}
