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

import androidx.annotation.NonNull;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.truth.Truth;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.auth.FirebaseAuthCredentialsProvider;
import com.google.firebase.firestore.util.Listener;
import com.google.firebase.inject.Deferred;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.robolectric.annotation.Config;
import org.robolectric.RobolectricTestRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.Mockito;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirebaseAuthCredentialsProviderTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS);

  @Mock InternalAuthProvider mockInternalAuthProvider;
  @Mock Listener<User> mockUserListener;
  @Captor ArgumentCaptor<Deferred.DeferredHandler<InternalAuthProvider>> handlerCaptor;

  @Test
  public void setChangeListenerShouldBeCalledWithUnauthenticatedIfProviderIsNotAvailable() {
    UnavailableDeferredInternalAuthProvider unavailableDeferredInternalAuthProvider = new UnavailableDeferredInternalAuthProvider();
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider = new FirebaseAuthCredentialsProvider(unavailableDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);

    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
  }

  @Test
  public void setChangeListenerShouldBeCalledWithUnauthenticatedIfUidIsNull() {
    when(mockInternalAuthProvider.getUid()).thenReturn(null);
    ImmediateDeferredInternalAuthProvider immediateDeferredInternalAuthProvider = new ImmediateDeferredInternalAuthProvider(mockInternalAuthProvider);
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider = new FirebaseAuthCredentialsProvider(immediateDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);

    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
  }

  @Test
  public void setChangeListenerShouldBeCalledWithAuthenticatedUserIfUidIsNotNull() {
    when(mockInternalAuthProvider.getUid()).thenReturn("TestUID");
    ImmediateDeferredInternalAuthProvider immediateDeferredInternalAuthProvider = new ImmediateDeferredInternalAuthProvider(mockInternalAuthProvider);
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider = new FirebaseAuthCredentialsProvider(immediateDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);

    verify(mockUserListener).onValue(new User("TestUID"));
  }

  @Test
  public void setChangeListenerShouldBeCalledWithUnauthenticatedUserWhenProviderWithNullUidBecomesAvailable() {
    DelayedDeferredInternalAuthProvider delayedDeferredInternalAuthProvider = new DelayedDeferredInternalAuthProvider();
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider = new FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);
    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
    when(mockInternalAuthProvider.getUid()).thenReturn(null);
    delayedDeferredInternalAuthProvider.setInternalAuthProvider(mockInternalAuthProvider);

    verify(mockUserListener, times(2)).onValue(User.UNAUTHENTICATED);
  }

  @Test
  public void setChangeListenerShouldBeCalledWithAuthenticatedUserWhenProviderWithAuthenticatedUserBecomesAvailable() {
    DelayedDeferredInternalAuthProvider delayedDeferredInternalAuthProvider = new DelayedDeferredInternalAuthProvider();
    FirebaseAuthCredentialsProvider firebaseAuthCredentialsProvider = new FirebaseAuthCredentialsProvider(delayedDeferredInternalAuthProvider);

    firebaseAuthCredentialsProvider.setChangeListener(mockUserListener);
    verify(mockUserListener).onValue(User.UNAUTHENTICATED);
    when(mockInternalAuthProvider.getUid()).thenReturn("TestUID");
    delayedDeferredInternalAuthProvider.setInternalAuthProvider(mockInternalAuthProvider);

    verify(mockUserListener).onValue(new User("TestUID"));
  }

  private static final class UnavailableDeferredInternalAuthProvider implements Deferred<InternalAuthProvider> {

    @Override
    public void whenAvailable(@NonNull DeferredHandler<InternalAuthProvider> handler) {
    }

    @Override
    public InternalAuthProvider get() {
      return null;
    }
  }

  private static final class ImmediateDeferredInternalAuthProvider implements Deferred<InternalAuthProvider> {

    private final InternalAuthProvider internalAuthProvider;

    ImmediateDeferredInternalAuthProvider(InternalAuthProvider internalAuthProvider) {
      this.internalAuthProvider = internalAuthProvider;
    }

    @Override
    public void whenAvailable(@NonNull DeferredHandler<InternalAuthProvider> handler) {
      handler.handle(this);
    }

    @Override
    public InternalAuthProvider get() {
      return internalAuthProvider;
    }
  }

  private static final class DelayedDeferredInternalAuthProvider implements Deferred<InternalAuthProvider> {

    private final Object lock = new Object();
    private InternalAuthProvider internalAuthProvider;
    private DeferredHandler<InternalAuthProvider> handler;

    @Override
    public void whenAvailable(@NonNull DeferredHandler<InternalAuthProvider> handler) {
      synchronized (lock) {
        assertThat(internalAuthProvider).isNull();
        this.handler = handler;
      }
    }

    @Override
    public InternalAuthProvider get() {
      synchronized (lock) {
        return internalAuthProvider;
      }
    }

    void setInternalAuthProvider(InternalAuthProvider internalAuthProvider) {
      assertThat(internalAuthProvider).isNotNull();
      DeferredHandler<InternalAuthProvider> handler;
      synchronized (lock) {
        assertThat(this.handler).isNotNull();
        this.internalAuthProvider = internalAuthProvider;
        handler = this.handler;
      }
      handler.handle(this);
    }
  }

}
