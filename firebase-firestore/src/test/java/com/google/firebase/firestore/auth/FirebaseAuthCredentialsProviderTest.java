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

package com.google.firebase.firestore.auth;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.auth.internal.IdTokenListener;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.testutil.DelayedDeferred;
import com.google.firebase.firestore.testutil.ImmediateDeferred;
import com.google.firebase.firestore.testutil.UnavailableDeferred;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.internal.InternalTokenResult;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseAuthCredentialsProviderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock InternalAuthProvider mockInternalAuthProvider;
  @Mock Listener<User> mockUserListener;
  @Mock InternalTokenResult mockInternalTokenResult;
  @Mock GetTokenResult mockGetTokenResult;
  @Mock GetTokenResult mockGetTokenResult2;
  @Captor ArgumentCaptor<IdTokenListener> idTokenListenerCaptor;

  @Test
  public void setChangeListenerShouldBeCalledWithUnauthenticatedIfProviderIsNotAvailable() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new UnavailableDeferred<>());

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);

    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
  }

  @Test
  public void setChangeListenerShouldBeCalledWithUnauthenticatedIfUidIsNull() {
    when(mockInternalAuthProvider.getUid()).thenReturn(null);
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);

    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
  }

  @Test
  public void setChangeListenerShouldBeCalledWithAuthenticatedUserIfUidIsNotNull() {
    when(mockInternalAuthProvider.getUid()).thenReturn("TestUID");
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);

    verify(mockUserListener).onValue(new User("TestUID"));
  }

  @Test
  public void
      setChangeListenerShouldBeCalledWithUnauthenticatedUserWhenProviderWithNullUidBecomesAvailable() {
    DelayedDeferred<InternalAuthProvider> delayedDeferredInternalAuthProvider =
        new DelayedDeferred<>();
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);
    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
    when(mockInternalAuthProvider.getUid()).thenReturn(null);
    delayedDeferredInternalAuthProvider.setInstance(mockInternalAuthProvider);

    verify(mockUserListener, times(2)).onValue(User.UNAUTHENTICATED);
  }

  @Test
  public void
      setChangeListenerShouldBeCalledWithAuthenticatedUserWhenProviderWithAuthenticatedUserBecomesAvailable() {
    DelayedDeferred<InternalAuthProvider> delayedDeferredInternalAuthProvider =
        new DelayedDeferred<>();
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);
    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
    when(mockInternalAuthProvider.getUid()).thenReturn("TestUID");
    delayedDeferredInternalAuthProvider.setInstance(mockInternalAuthProvider);

    verify(mockUserListener).onValue(new User("TestUID"));
  }

  @Test
  public void setChangeListenerShouldBeCalledWhenIdTokenChanges() {
    when(mockInternalAuthProvider.getUid()).thenReturn("TestUID1");
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    verify(mockInternalAuthProvider).addIdTokenListener(idTokenListenerCaptor.capture());

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);
    verify(mockUserListener).onValue(new User("TestUID1"));
    when(mockInternalAuthProvider.getUid()).thenReturn("TestUID2");
    idTokenListenerCaptor.getValue().onIdTokenChanged(mockInternalTokenResult);

    verify(mockUserListener).onValue(new User("TestUID2"));
  }

  @Test
  public void removeChangeListenerShouldStopNotifyingTheListener() {
    DelayedDeferred<InternalAuthProvider> delayedDeferredInternalAuthProvider =
        new DelayedDeferred<>();
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);
    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
    firebaseAuthCredentialsProvider.removeChangeListener();
    delayedDeferredInternalAuthProvider.setInstance(mockInternalAuthProvider);

    verifyNoMoreInteractions(mockUserListener);
  }

  @Test
  public void removeChangeListenerShouldNotThrowIfProviderIsNotAvailable() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new UnavailableDeferred<>());

    firebaseAuthCredentialsProvider.removeChangeListener();
  }

  @Test
  public void removeChangeListenerShouldUnregisterTheIdTokenListener() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    verify(mockInternalAuthProvider).addIdTokenListener(idTokenListenerCaptor.capture());

    firebaseAuthCredentialsProvider.removeChangeListener();

    verify(mockInternalAuthProvider).removeIdTokenListener(idTokenListenerCaptor.getValue());
  }

  @Test
  public void getTokenShouldReturnAFailedTaskIfProviderIsNotAvailable() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new UnavailableDeferred<>());

    Task<String> task = firebaseAuthCredentialsProvider.getToken();

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(FirebaseApiNotAvailableException.class);
  }

  @Test
  public void getTokenShouldReturnAFailedTaskIfGetAccessTokenFails() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    Exception getAccessTokenException = new Exception();
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forException(getAccessTokenException));

    Task<String> task = firebaseAuthCredentialsProvider.getToken();

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isSameInstanceAs(getAccessTokenException);
  }

  @Test
  public void getTokenShouldReturnASuccessfulTaskIfGetAccessTokenSucceeds() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    when(mockGetTokenResult.getToken()).thenReturn("TestToken");
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult));

    Task<String> task = firebaseAuthCredentialsProvider.getToken();

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("TestToken");
  }

  @Test
  public void getTokenShouldNotForceRefreshTheTokenIfInvalidateTokenIsNotCalled() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    when(mockGetTokenResult.getToken()).thenReturn("TestToken");
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult));

    firebaseAuthCredentialsProvider.getToken();

    verify(mockInternalAuthProvider).getAccessToken(false);
  }

  @Test
  public void getTokenShouldRecursivelyCallItselfIfTheTokenCounterChanges() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    verify(mockInternalAuthProvider).addIdTokenListener(idTokenListenerCaptor.capture());
    when(mockGetTokenResult2.getToken()).thenReturn("TestToken2");
    TaskCompletionSource<GetTokenResult> getAccessTokenTaskCompletionSource =
        new TaskCompletionSource<>();

    // This delicate dance ensures the following order of operations:
    // 1. getToken() calls internalAuthProvider.getAccessToken(), which returns an uncompleted task.
    // 2. IdTokenListener.onIdTokenChanged() is invoked, which increments tokenCounter.
    // 3. The uncompleted task is completed.
    // 4. The continuation added to the task is invoked and detects that tokenCounter changed, and
    //       calls getToken() again.
    // 5. The recursively-called getToken() calls calls internalAuthProvider.getAccessToken(), which
    //       returns a completed task with the token "TestToken2".
    // 6. The task returned from the original getToken() invocation completes.
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(getAccessTokenTaskCompletionSource.getTask());
    Task<String> task = firebaseAuthCredentialsProvider.getToken();
    verify(mockInternalAuthProvider).getAccessToken(anyBoolean());
    idTokenListenerCaptor.getValue().onIdTokenChanged(mockInternalTokenResult);
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult2));
    getAccessTokenTaskCompletionSource.setResult(mockGetTokenResult);

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("TestToken2");
  }

  @Test
  public void invalidateTokenShouldCauseGetTokenToForceRefresh() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    when(mockGetTokenResult.getToken()).thenReturn("TestToken");
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult));

    firebaseAuthCredentialsProvider.invalidateToken();
    firebaseAuthCredentialsProvider.getToken();

    verify(mockInternalAuthProvider).getAccessToken(true);
  }

  @Test
  public void invalidateTokenShouldOnlyForceRefreshOnTheImmediatelyFollowingGetTokenInvocation() {
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider =
        new FirebaseAuthCredentialsProvider(new ImmediateDeferred<>(mockInternalAuthProvider));
    when(mockGetTokenResult.getToken()).thenReturn("TestToken");
    when(mockInternalAuthProvider.getAccessToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockGetTokenResult));

    firebaseAuthCredentialsProvider.invalidateToken();
    firebaseAuthCredentialsProvider.getToken();
    firebaseAuthCredentialsProvider.getToken();

    verify(mockInternalAuthProvider).getAccessToken(true);
    verify(mockInternalAuthProvider).getAccessToken(false);
  }
}
