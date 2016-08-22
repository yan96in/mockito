package org.mockito.stubbing;

import org.mockito.invocation.Invocation;

/**
 *
 */
public interface Stubbing {

    Invocation getInvocation();

    boolean wasUsed();
}
