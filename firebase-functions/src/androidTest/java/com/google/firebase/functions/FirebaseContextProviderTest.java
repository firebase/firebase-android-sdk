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

package com.google.firebase.functions;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirebaseContextProviderTest {
  private static final String AUTH_TOKEN = "authToken";
  private static final String IID_TOKEN = "iidToken";
  private static final String APP_CHECK_TOKEN = "appCheckToken";

  private static final String APP_CHECK_LIMITED_USE_TOKEN = "appCheckLimitedUseToken";
  private static final String ERROR = "errorString";

  private static final InternalAuthProvider fixedAuthProvider =
      new TestInternalAuthProvider(() -> AUTH_TOKEN);
  private static final InternalAuthProvider anonymousAuthProvider =
      new TestInternalAuthProvider(
          () -> {
            throw new FirebaseNoSignedInUserException("not signed in");
          });
  private static final FirebaseInstanceIdInternal fixedIidProvider =
      new TestFirebaseInstanceIdInternal(IID_TOKEN);
  private static final InteropAppCheckTokenProvider fixedAppCheckProvider =
      new TestInteropAppCheckTokenProvider(APP_CHECK_TOKEN, APP_CHECK_LIMITED_USE_TOKEN);
  private static final InteropAppCheckTokenProvider errorAppCheckProvider =
      new TestInteropAppCheckTokenProvider(APP_CHECK_TOKEN, APP_CHECK_LIMITED_USE_TOKEN, ERROR);

  @Test
  public void getContext_whenAuthAndAppCheckAreNotAvailable_shouldContainOnlyIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            absentProvider(),
            providerOf(fixedIidProvider),
            absentDeferred(),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(false));
    assertThat(context.getAuthToken()).isNull();
    assertThat(context.getAppCheckToken()).isNull();
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
  }

  @Test
  public void getContext_whenOnlyAuthIsAvailable_shouldContainOnlyAuthTokenAndIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            providerOf(fixedAuthProvider),
            providerOf(fixedIidProvider),
            absentDeferred(),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(false));
    assertThat(context.getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(context.getAppCheckToken()).isNull();
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
  }

  @Test
  public void getContext_whenOnlyAppCheckIsAvailable_shouldContainOnlyAppCheckTokenAndIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            absentProvider(),
            providerOf(fixedIidProvider),
            deferredOf(fixedAppCheckProvider),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(false));
    assertThat(context.getAuthToken()).isNull();
    assertThat(context.getAppCheckToken()).isEqualTo(APP_CHECK_TOKEN);
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
  }

  @Test
  public void getContext_whenOnlyAuthIsAvailableAndNotSignedIn_shouldContainOnlyIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            providerOf(anonymousAuthProvider),
            providerOf(fixedIidProvider),
            absentDeferred(),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(false));
    assertThat(context.getAuthToken()).isNull();
    assertThat(context.getAppCheckToken()).isNull();
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
  }

  @Test
  public void getContext_whenOnlyAppCheckIsAvailableAndHasError_shouldContainOnlyIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            absentProvider(),
            providerOf(fixedIidProvider),
            deferredOf(errorAppCheckProvider),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(false));
    assertThat(context.getAuthToken()).isNull();
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
    assertThat(context.getAppCheckToken()).isNull();
  }

  @Test
  public void getContext_facLimitedUse_whenOnlyAppCheckIsAvailableAndHasError_shouldContainOnlyIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            absentProvider(),
            providerOf(fixedIidProvider),
            deferredOf(errorAppCheckProvider),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(true));
    assertThat(context.getAuthToken()).isNull();
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
    assertThat(context.getAppCheckToken()).isNull();
  }

  @Test
  public void getContext_facLimitedUse_whenOnlyAppCheckIsAvailable_shouldContainToken()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            absentProvider(),
            providerOf(fixedIidProvider),
            deferredOf(fixedAppCheckProvider),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(true));
    assertThat(context.getAuthToken()).isNull();
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
    assertThat(context.getAppCheckToken()).isEqualTo(APP_CHECK_LIMITED_USE_TOKEN);
  }

  @Test
  public void getContext_whenAuthAndAppCheckAreAvailable_shouldContainAuthAppCheckTokensAndIid()
      throws ExecutionException, InterruptedException {
    FirebaseContextProvider contextProvider =
        new FirebaseContextProvider(
            providerOf(fixedAuthProvider),
            providerOf(fixedIidProvider),
            deferredOf(fixedAppCheckProvider),
            TestOnlyExecutors.lite());

    HttpsCallableContext context = Tasks.await(contextProvider.getContext(false));
    assertThat(context.getAuthToken()).isEqualTo(AUTH_TOKEN);
    assertThat(context.getAppCheckToken()).isEqualTo(APP_CHECK_TOKEN);
    assertThat(context.getInstanceIdToken()).isEqualTo(IID_TOKEN);
  }

  private static <T> Provider<T> absentProvider() {
    return () -> null;
  }

  private static <T> Deferred<T> absentDeferred() {
    return handler -> {};
  }

  private static <T> Provider<T> providerOf(T value) {
    return () -> value;
  }

  private static <T> Deferred<T> deferredOf(T value) {
    return handler -> handler.handle(() -> value);
  }
}
