// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard.CriticalSection;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class WorkInitializerTest {

  private final EventStore mockStore = mock(EventStore.class);
  private final WorkScheduler mockScheduler = mock(WorkScheduler.class);

  private final WorkInitializer initializer =
      new WorkInitializer(Runnable::run, mockStore, mockScheduler, CriticalSection::execute);

  @Test
  public void test() {
    TransportContext ctx1 =
        TransportContext.builder().setBackendName("backend1").setExtras(null).build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();

    when(mockStore.loadActiveContexts()).thenReturn(Arrays.asList(ctx1, ctx2));

    initializer.ensureContextsScheduled();

    verify(mockScheduler, times(1)).schedule(ctx1, 1);
    verify(mockScheduler, times(1)).schedule(ctx2, 1);
  }
}
