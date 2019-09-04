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

package com.google.android.datatransport.runtime;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.Priority;
import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TransportContextTest {
  private static final byte[] EXTRAS = "hello".getBytes(Charset.forName("UTF-8"));
  private static final String BACKEND_NAME = "bknd";

  private static final TransportContext CTX =
      TransportContext.builder()
          .setPriority(Priority.DEFAULT)
          .setExtras(EXTRAS)
          .setBackendName(BACKEND_NAME)
          .build();

  @Test
  public void withPriority_whenPriorityNotChanged_shouldReturnEqualContext() {
    assertThat(CTX.withPriority(Priority.DEFAULT)).isEqualTo(CTX);
  }

  @Test
  public void withPriority_whenPriorityChangedBackToOriginal_shouldReturnEqualContext() {
    assertThat(CTX.withPriority(Priority.VERY_LOW).withPriority(Priority.DEFAULT)).isEqualTo(CTX);
  }

  @Test
  public void withPriority_whenPriorityIsDifferent_shouldReturnContextWithPriorityChanged() {
    assertThat(CTX.withPriority(Priority.VERY_LOW))
        .isEqualTo(
            TransportContext.builder()
                .setPriority(Priority.VERY_LOW)
                .setExtras(EXTRAS)
                .setBackendName(BACKEND_NAME)
                .build());
  }
}
