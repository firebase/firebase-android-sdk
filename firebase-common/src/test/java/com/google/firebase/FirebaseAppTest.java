// Copyright 2018 Google LLC
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

package com.google.firebase;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.common.testutil.Assert.assertThrows;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.internal.InternalTokenProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class FirebaseAppTest {
  private final FirebaseApp firebaseApp =
      new FirebaseApp(
          RuntimeEnvironment.application.getApplicationContext(),
          "myapp",
          new FirebaseOptions.Builder().setApplicationId("id").build());

  private final InternalTokenProvider mockAuthProvider = mock(InternalTokenProvider.class);

  @Test
  public void getToken_whenNoProviderIsSet_shouldThrow() {
    Task<GetTokenResult> token = firebaseApp.getToken(true);
    assertThat(token.isComplete()).isTrue();
    assertThat(token.isSuccessful()).isFalse();
    assertThat(token.getException()).isInstanceOf(FirebaseApiNotAvailableException.class);
  }

  @Test
  public void getUid_whenNoProviderIsSet_shouldThrow() {
    assertThrows(FirebaseApiNotAvailableException.class, firebaseApp::getUid);
  }

  @Test
  public void getToken_whenProviderIsSet_shouldDelegateToIt() {
    firebaseApp.setTokenProvider(mockAuthProvider);
    Task<GetTokenResult> task = new TaskCompletionSource<GetTokenResult>().getTask();

    when(mockAuthProvider.getAccessToken(anyBoolean())).thenReturn(task);

    assertThat(firebaseApp.getToken(true)).isSameAs(task);

    verify(mockAuthProvider, times(1)).getAccessToken(true);
  }

  @Test
  public void getUid_whenProviderIsSet_shouldDelegateToIt()
      throws FirebaseApiNotAvailableException {
    firebaseApp.setTokenProvider(mockAuthProvider);

    String uid = "myUid";

    when(mockAuthProvider.getUid()).thenReturn(uid);

    assertThat(firebaseApp.getUid()).isSameAs(uid);

    verify(mockAuthProvider, times(1)).getUid();
  }
}
