/*
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.progress;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceNumber {

    private static final AtomicInteger SEQUENCE_NUMBER = new AtomicInteger();

    public static int next() {
        return SEQUENCE_NUMBER.getAndIncrement();
    }
}