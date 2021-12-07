// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.monitoring;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.InOrder;

@RunWith(JUnit4.class)
public class DelegatingTracerTest {
  private final DelegatingTracer delegatingTracer = new DelegatingTracer();
  private final ExtendedTracer mockTracer = mock(ExtendedTracer.class);
  private final TraceHandle mockHandle = mock(TraceHandle.class);

  @Before
  public void setUp() {
    when(mockTracer.startTrace(anyString())).thenReturn(mockHandle);
  }

  @Test
  public void startTrace_whenNoDelegate_shouldBeANoop() {
    assertThat(delegatingTracer.startTrace("name")).isEqualTo(TraceHandle.NOOP);
  }

  @Test
  public void startTrace_whenDelegateSet_shouldDelegateToIt() {
    delegatingTracer.setTracer(mockTracer);
    try (TraceHandle handle = delegatingTracer.startTrace("name")) {
      handle.addAttribute("key", "value");
    }

    InOrder inOrder = inOrder(mockTracer, mockHandle);
    inOrder.verify(mockTracer, times(1)).startTrace("name");
    inOrder.verify(mockHandle, times(1)).addAttribute("key", "value");
    inOrder.verify(mockHandle, times(1)).close();
  }

  @Test
  public void recordTrace_whenDelegateSet_shouldDelegateToIt() {
    Instant start = Instant.now();
    Instant end = Instant.now();
    String traceName = "name";
    String attrName = "key";
    String attrValue = "value";

    delegatingTracer.setTracer(mockTracer);
    delegatingTracer.recordTrace(traceName, start, end, attrName, attrValue);

    verify(mockTracer, times(1)).recordTrace(traceName, start, end, attrName, attrValue);
  }
}
