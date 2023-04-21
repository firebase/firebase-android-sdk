// Copyright 2020 Google LLC
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

package com.google.firebase.storage.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseException;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link Util}. */
@RunWith(RobolectricTestRunner.class)
public class UtilTest {
  public static final String TOKEN = "token";
  public static final FirebaseException ERROR = new FirebaseException("error");

  // Tasks.await() cannot be invoked on the main thread, so we need a real executor.
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  @Mock InteropAppCheckTokenProvider mockAppCheckTokenProvider;
  @Mock AppCheckTokenResult mockAppCheckTokenResult;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetCurrentAppCheckToken_resultContainsNoError_returnsToken() throws Exception {
    when(mockAppCheckTokenResult.getToken()).thenReturn(TOKEN);
    when(mockAppCheckTokenResult.getError()).thenReturn(null);
    when(mockAppCheckTokenProvider.getToken(/* forceRefresh= */ false))
        .thenReturn(Tasks.forResult(mockAppCheckTokenResult));

    Future<String> tokenFuture =
        executorService.submit(() -> Util.getCurrentAppCheckToken(mockAppCheckTokenProvider));

    assertThat(tokenFuture.get()).isEqualTo(TOKEN);
  }

  @Test
  public void testGetCurrentAppCheckToken_resultContainsError_returnsToken() throws Exception {
    when(mockAppCheckTokenResult.getToken()).thenReturn(TOKEN);
    when(mockAppCheckTokenResult.getError()).thenReturn(ERROR);
    when(mockAppCheckTokenProvider.getToken(/* forceRefresh= */ false))
        .thenReturn(Tasks.forResult(mockAppCheckTokenResult));

    Future<String> tokenFuture =
        executorService.submit(() -> Util.getCurrentAppCheckToken(mockAppCheckTokenProvider));

    assertThat(tokenFuture.get()).isEqualTo(TOKEN);
  }

  @Test
  public void testGetCurrentAppCheckToken_nullAppCheckProvider_returnsNull() {
    String token = Util.getCurrentAppCheckToken(/* appCheckProvider= */ null);

    assertThat(token).isNull();
  }

  @Test
  public void testGetCurrentAppCheckToken_taskFails_returnsNull() throws Exception {
    when(mockAppCheckTokenProvider.getToken(/* forceRefresh= */ false))
        .thenReturn(Tasks.forException(new Exception()));

    Future<String> tokenFuture =
        executorService.submit(() -> Util.getCurrentAppCheckToken(mockAppCheckTokenProvider));

    assertThat(tokenFuture.get()).isNull();
  }
}
