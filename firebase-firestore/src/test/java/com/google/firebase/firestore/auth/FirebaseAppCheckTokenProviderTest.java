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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.firestore.testutil.ImmediateDeferred;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.inject.Deferred;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
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

class TestListenersInteropAppCheckTokenProvider implements InteropAppCheckTokenProvider {
  public ArrayList<AppCheckTokenListener> listeners = new ArrayList<>();

  @NonNull
  @Override
  public Task<AppCheckTokenResult> getToken(boolean forceRefresh) {
    return null;
  }

  @Override
  public void addAppCheckTokenListener(@NonNull AppCheckTokenListener listener) {
    listeners.add(listener);
  }

  @Override
  public void removeAppCheckTokenListener(@NonNull AppCheckTokenListener listener) {
    listeners.remove(listener);
  }

  void triggerTokenChange(AppCheckTokenResult newToken) {
    for (AppCheckTokenListener listener : listeners) {
      listener.onAppCheckTokenChanged(newToken);
    }
  }
}

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseAppCheckTokenProviderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock InteropAppCheckTokenProvider mockInteropAppCheckTokenProvider;
  @Mock Listener<String> mockAppCheckTokenListener;
  @Mock AppCheckTokenResult mockAppCheckTokenResult;
  @Captor ArgumentCaptor<AppCheckTokenListener> idTokenListenerCaptor;

  Deferred<InteropAppCheckTokenProvider> getDeferredProvider(
      InteropAppCheckTokenProvider internal) {
    return new ImmediateDeferred<>(internal);
  }

  @Test
  public void setChangeListenerShouldBeCalledWhenIdTokenChanges() {
    TestListenersInteropAppCheckTokenProvider internalAppCheckTokenProvider =
        new TestListenersInteropAppCheckTokenProvider();
    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(internalAppCheckTokenProvider));
    AtomicReference<String> receivedToken = new AtomicReference<String>();
    Listener<String> appCheckTokenListener = receivedToken::set;
    firebaseAppCheckTokenProvider.setChangeListener(appCheckTokenListener);
    when(mockAppCheckTokenResult.getToken()).thenReturn("TestToken");
    internalAppCheckTokenProvider.triggerTokenChange(mockAppCheckTokenResult);
    assertThat(receivedToken.get()).isEqualTo("TestToken");
    when(mockAppCheckTokenResult.getToken()).thenReturn("NewTestToken");
    internalAppCheckTokenProvider.triggerTokenChange(mockAppCheckTokenResult);
    assertThat(receivedToken.get()).isEqualTo("NewTestToken");
  }

  @Test
  public void removeChangeListenerShouldStopNotifyingTheListener() {
    TestListenersInteropAppCheckTokenProvider internalAppCheckTokenProvider =
        new TestListenersInteropAppCheckTokenProvider();
    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(internalAppCheckTokenProvider));
    firebaseAppCheckTokenProvider.setChangeListener(mockAppCheckTokenListener);
    assertThat(internalAppCheckTokenProvider.listeners.size()).isEqualTo(1);
    firebaseAppCheckTokenProvider.removeChangeListener();
    assertThat(internalAppCheckTokenProvider.listeners).isEmpty();
  }

  @Test
  public void removeChangeListenerShouldNotThrowIfProviderIsNotAvailable() {
    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(null));
    firebaseAppCheckTokenProvider.removeChangeListener();
  }

  @Test
  public void removeChangeListenerShouldUnregisterTheIdTokenListener() {
    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(mockInteropAppCheckTokenProvider));
    verify(mockInteropAppCheckTokenProvider)
        .addAppCheckTokenListener(idTokenListenerCaptor.capture());
    firebaseAppCheckTokenProvider.removeChangeListener();
    verify(mockInteropAppCheckTokenProvider)
        .removeAppCheckTokenListener(idTokenListenerCaptor.getValue());
  }

  @Test
  public void getTokenShouldReturnAFailedTaskIfProviderIsNotAvailable() {
    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(null));
    Task<String> task = firebaseAppCheckTokenProvider.getToken();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isInstanceOf(FirebaseApiNotAvailableException.class);
  }

  @Test
  public void getTokenShouldReturnAFailedTaskIfAppCheckGetTokenFails() {
    Exception getTokenException = new Exception();
    when(mockInteropAppCheckTokenProvider.getToken(anyBoolean()))
        .thenReturn(Tasks.forException(getTokenException));

    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(mockInteropAppCheckTokenProvider));

    Task<String> task = firebaseAppCheckTokenProvider.getToken();
    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isSameInstanceAs(getTokenException);
  }

  @Test
  public void getTokenShouldReturnASuccessfulTaskIfAppCheckGetTokenSucceeds() {
    when(mockAppCheckTokenResult.getToken()).thenReturn("TestToken");
    when(mockInteropAppCheckTokenProvider.getToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockAppCheckTokenResult));

    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(mockInteropAppCheckTokenProvider));
    Task<String> task = firebaseAppCheckTokenProvider.getToken();

    assertThat(task.isComplete()).isTrue();
    assertThat(task.isSuccessful()).isTrue();
    assertThat(task.getResult()).isEqualTo("TestToken");
  }

  @Test
  public void getTokenShouldNotForceRefreshTheTokenIfInvalidateTokenIsNotCalled() {
    when(mockAppCheckTokenResult.getToken()).thenReturn("TestToken");
    when(mockInteropAppCheckTokenProvider.getToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockAppCheckTokenResult));

    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(mockInteropAppCheckTokenProvider));
    firebaseAppCheckTokenProvider.getToken();
    verify(mockInteropAppCheckTokenProvider).getToken(false);
  }

  @Test
  public void invalidateTokenShouldCauseGetTokenToForceRefresh() {
    when(mockAppCheckTokenResult.getToken()).thenReturn("TestToken");
    when(mockInteropAppCheckTokenProvider.getToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockAppCheckTokenResult));

    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(mockInteropAppCheckTokenProvider));
    firebaseAppCheckTokenProvider.invalidateToken();
    firebaseAppCheckTokenProvider.getToken();
    verify(mockInteropAppCheckTokenProvider).getToken(true);
  }

  @Test
  public void invalidateTokenShouldOnlyForceRefreshOnTheImmediatelyFollowingGetTokenInvocation() {
    when(mockAppCheckTokenResult.getToken()).thenReturn("TestToken");
    when(mockInteropAppCheckTokenProvider.getToken(anyBoolean()))
        .thenReturn(Tasks.forResult(mockAppCheckTokenResult));

    FirebaseAppCheckTokenProvider firebaseAppCheckTokenProvider =
        new FirebaseAppCheckTokenProvider(getDeferredProvider(mockInteropAppCheckTokenProvider));

    firebaseAppCheckTokenProvider.invalidateToken();
    firebaseAppCheckTokenProvider.getToken();
    firebaseAppCheckTokenProvider.getToken();

    verify(mockInteropAppCheckTokenProvider).getToken(true);
    verify(mockInteropAppCheckTokenProvider).getToken(false);
  }
}
