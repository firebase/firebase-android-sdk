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

import static com.google.android.gms.common.util.Base64Utils.decodeUrlSafeNoPadding;
import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.common.testutil.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.content.LocalBroadcastManager;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.common.base.Defaults;
import com.google.firebase.FirebaseApp.IdTokenListener;
import com.google.firebase.FirebaseApp.IdTokenListenersCountChangedListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.components.EagerSdkVerifier;
import com.google.firebase.components.InitTracker;
import com.google.firebase.components.TestComponentOne;
import com.google.firebase.components.TestComponentTwo;
import com.google.firebase.components.TestUserAgentDependentComponent;
import com.google.firebase.internal.InternalTokenResult;
import com.google.firebase.platforminfo.UserAgentPublisher;
import com.google.firebase.testing.FirebaseAppRule;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link com.google.firebase.FirebaseApp}. */
// TODO(arondeak): uncomment lines when Firebase API targets are in integ.
@RunWith(AndroidJUnit4.class)
public class FirebaseAppTest {
  protected static final String GOOGLE_APP_ID = "1:855246033427:android:6e48bff8253f3f6e6e";
  protected static final String GOOGLE_API_KEY = "AIzaSyD3asb-2pEZVqMkmL6M9N6nHZRR_znhrh0";

  protected static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setApplicationId(GOOGLE_APP_ID)
          .setApiKey(GOOGLE_API_KEY)
          .build();

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  private final AtomicBoolean isDeviceProtectedStorage = new AtomicBoolean(false);
  private Context targetContext;
  private BackgroundDetector backgroundDetector;
  private LocalBroadcastManager localBroadcastManager;

  @Before
  public void setUp() {
    targetContext = InstrumentationRegistry.getTargetContext();
    backgroundDetector = BackgroundDetector.getInstance();
    // Used by Scion internally.
    // Scion.testOnlySetDefaultFactory(new ScionFactory(targetContext));
    localBroadcastManager = LocalBroadcastManager.getInstance(targetContext);
    // Force background detector state to foreground
    backgroundDetector.onActivityResumed(null);
  }

  @Test
  public void testBackgroundStateChangeCallbacks() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    firebaseApp.setAutomaticResourceManagementEnabled(true);
    final AtomicBoolean backgroundState = new AtomicBoolean();
    final AtomicInteger callbackCount = new AtomicInteger();
    firebaseApp.addBackgroundStateChangeListener(
        background -> {
          backgroundState.set(background);
          callbackCount.incrementAndGet();
        });
    assertThat(callbackCount.get()).isEqualTo(0);

    // App moves to the background.
    backgroundDetector.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    assertThat(callbackCount.get()).isEqualTo(1);
    assertThat(backgroundState.get()).isTrue();

    // App moves to the foreground.
    backgroundDetector.onActivityResumed(null);
    assertThat(callbackCount.get()).isEqualTo(2);
    assertThat(backgroundState.get()).isFalse();
  }

  @Test
  public void testInitializeApp_shouldPublishUserAgentPublisherThatReturnsPublishedVersions() {
    String[] expectedUserAgent = {"firebase-common/16.0.5", "test-component/1.2.3"};
    Context mockContext = createForwardingMockContext();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);

    TestUserAgentDependentComponent userAgentDependant =
        firebaseApp.get(TestUserAgentDependentComponent.class);
    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");
    Arrays.sort(actualUserAgent);

    assertThat(actualUserAgent).asList().contains("test-component/1.2.3");
  }

  @Test
  public void testInitializeApp_shouldPublishVersionForFirebaseCommon() {
    Context mockContext = createForwardingMockContext();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);

    TestUserAgentDependentComponent userAgentDependant =
        firebaseApp.get(TestUserAgentDependentComponent.class);
    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");
    Arrays.sort(actualUserAgent);

    // After sorting the user agents are expected to be {"firebase-common/x.y.z",
    // "test-component/1.2.3"}
    assertThat(actualUserAgent[0]).contains("firebase-common");
  }

  @Test
  public void testRemovedBackgroundStateChangeCallbacksDontFire() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    final AtomicInteger callbackCount = new AtomicInteger();
    FirebaseApp.BackgroundStateChangeListener listener =
        background -> callbackCount.incrementAndGet();
    firebaseApp.addBackgroundStateChangeListener(listener);
    firebaseApp.removeBackgroundStateChangeListener(listener);

    // App moves to the background.
    backgroundDetector.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);

    // App moves to the foreground.
    backgroundDetector.onActivityResumed(null);

    assertThat(callbackCount.get()).isEqualTo(0);
  }

  @Test
  public void testBackgroundStateChangeCallbacksDontFire_AutomaticResourceManagementTurnedOff() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    firebaseApp.setAutomaticResourceManagementEnabled(true);
    final AtomicInteger callbackCount = new AtomicInteger();
    final AtomicBoolean backgroundState = new AtomicBoolean();
    FirebaseApp.BackgroundStateChangeListener listener =
        background -> {
          backgroundState.set(background);
          callbackCount.incrementAndGet();
        };
    firebaseApp.addBackgroundStateChangeListener(listener);

    // App moves to the background.
    backgroundDetector.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);

    assertThat(callbackCount.get()).isEqualTo(1);

    // Turning off automatic resource management fires foreground event, if the current state
    // is background.
    assertThat(backgroundDetector.isInBackground()).isTrue();
    firebaseApp.setAutomaticResourceManagementEnabled(false);
    assertThat(callbackCount.get()).isEqualTo(2);
    assertThat(backgroundState.get()).isFalse();

    // No more callbacks.
    backgroundDetector.onActivityResumed(null);
    backgroundDetector.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN);
    assertThat(callbackCount.get()).isEqualTo(2);
  }

  @Test
  public void testDefaultIdTokenListenersCountChangedListener() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    IdTokenListenersCountChangedListener listenersCountChangedListener =
        mock(IdTokenListenersCountChangedListener.class);

    // When registering, should fire
    firebaseApp.setIdTokenListenersCountChangedListener(listenersCountChangedListener);
    verify(listenersCountChangedListener).onListenerCountChanged(0);

    IdTokenListener listener =
        tokenResult -> {
          // do nothing
        };

    // On number changed, should fire
    firebaseApp.addIdTokenListener(listener);
    verify(listenersCountChangedListener).onListenerCountChanged(1);

    // On removal, should fire
    firebaseApp.removeIdTokenListener(listener);
    verify(listenersCountChangedListener, times(2)).onListenerCountChanged(0);
  }

  @Test
  public void testGetInstanceErrorMessageContainsProcessName() {
    try {
      FirebaseApp.getInstance();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e.getMessage()).contains(targetContext.getPackageName());
    }
  }

  @Test
  public void testGetInstancePersistedNotInitialized() {
    String name = "myApp";
    FirebaseApp.initializeApp(targetContext, OPTIONS, name);
    FirebaseApp.clearInstancesForTest();
    assertThrows(IllegalStateException.class, () -> FirebaseApp.getInstance(name));
  }

  @Test
  public void testRehydratingDeletedInstanceThrows() {
    final String name = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, name);
    firebaseApp.delete();
    FirebaseApp.clearInstancesForTest();
    assertThrows(IllegalStateException.class, () -> FirebaseApp.getInstance(name));
  }

  @Test
  public void testDeleteCallback() {
    String appName = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, appName);
    FirebaseAppLifecycleListener listener = mock(FirebaseAppLifecycleListener.class);
    firebaseApp.addLifecycleEventListener(listener);
    firebaseApp.delete();

    verify(listener).onDeleted(appName, OPTIONS);
    // Any further calls to delete are no-ops.
    reset(listener);
    firebaseApp.delete();
    verify(listener, never()).onDeleted(appName, OPTIONS);
  }

  @Test
  public void testGetApps() {
    FirebaseApp app1 = FirebaseApp.initializeApp(targetContext, OPTIONS, "app1");
    FirebaseApp app2 = FirebaseApp.initializeApp(targetContext, OPTIONS, "app2");
    assertThat(FirebaseApp.getApps(targetContext)).containsExactly(app1, app2);
  }

  @Test
  public void testInvokeAfterDeleteThrows() throws Exception {
    // delete and hidden methods shouldn't throw even after delete.
    Collection<String> allowedToCallAfterDelete =
        Arrays.asList(
            "addIdTokenChangeListener",
            "addBackgroundStateChangeListener",
            "delete",
            "equals",
            "getListeners",
            "getPersistenceKey",
            "hashCode",
            "isDefaultApp",
            "setIdTokenListenersCountChangedListener",
            "notifyIdTokenListeners",
            "removeIdTokenChangeListener",
            "removeBackgroundStateChangeListener",
            "setTokenProvider",
            "toString");
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    firebaseApp.delete();
    for (Method method : firebaseApp.getClass().getDeclaredMethods()) {
      int modifiers = method.getModifiers();
      if (Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers)) {
        try {
          if (!allowedToCallAfterDelete.contains(method.getName())) {
            invokePublicInstanceMethodWithDefaultValues(firebaseApp, method);
            fail("Method expected to throw, but didn't " + method.getName());
          }
        } catch (InvocationTargetException e) {
          if (!(e.getCause() instanceof IllegalStateException)
              || e.getCause().getMessage().equals("FirebaseApp was deleted.")) {
            fail(
                "Expected FirebaseApp#"
                    + method.getName()
                    + " to throw "
                    + "IllegalStateException with message \"FirebaseApp was deleted\", "
                    + "but instead got "
                    + e.getCause());
          }
        }
      }
    }
  }

  @Test
  public void testPersistenceKeyIsBijective() {
    String name = "myApp";
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, name);
    String persistenceKey = firebaseApp.getPersistenceKey();

    @SuppressWarnings("StringSplitter")
    String[] parts = persistenceKey.split("\\+");
    assertThat(new String(decodeUrlSafeNoPadding(parts[0]))).isEqualTo(name);
    assertThat(new String(decodeUrlSafeNoPadding(parts[1]))).isEqualTo(GOOGLE_APP_ID);
  }

  // Order of test cases matters.
  @Test(expected = IllegalStateException.class)
  public void testMissingInit() {
    FirebaseAuth.getInstance();
  }

  @Test
  public void testApiInitializedForNonDefaultApp() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    assertThat(firebaseApp.isDefaultApp()).isFalse();

    EagerSdkVerifier sdkVerifier = firebaseApp.get(EagerSdkVerifier.class);
    assertThat(sdkVerifier.isAuthInitialized()).isTrue();

    // Analytics is only initialized for the default app.
    assertThat(sdkVerifier.isAnalyticsInitialized()).isFalse();
  }

  @Test
  public void testApiInitForDefaultApp() {
    // Explicit initialization of FirebaseApp instance.
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext);
    assertThat(firebaseApp.isDefaultApp()).isTrue();

    EagerSdkVerifier sdkVerifier = firebaseApp.get(EagerSdkVerifier.class);
    assertThat(sdkVerifier.isAuthInitialized()).isTrue();
    assertThat(sdkVerifier.isAnalyticsInitialized()).isTrue();
  }

  @Test
  public void testInitializeDefaultAppIdempotent() {
    FirebaseApp.initializeApp(targetContext);
    FirebaseApp defaultApp = FirebaseApp.initializeApp(targetContext);
    assertThat(defaultApp).isNotNull();
  }

  @Test
  public void testInitializeFromContentProviderInSharedProcess() {
    // See http://b/30242033.
    FirebaseApp defaultApp = FirebaseApp.initializeApp(targetContext.getApplicationContext());
    assertThat(defaultApp).isNotNull();
  }

  @Test
  public void testInitializeApp_shouldDiscoverAndInitializeComponents() {
    Context mockContext = createForwardingMockContext();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);

    Context appContext = mockContext.getApplicationContext();

    assertThat(firebaseApp.get(Context.class)).isSameAs(appContext);
    assertThat(firebaseApp.get(FirebaseApp.class)).isSameAs(firebaseApp);

    TestComponentOne one = firebaseApp.get(TestComponentOne.class);
    assertThat(one).isNotNull();
    assertThat(one.getContext()).isSameAs(appContext);

    TestComponentTwo two = firebaseApp.get(TestComponentTwo.class);
    assertThat(two).isNotNull();
    assertThat(two.getApp()).isSameAs(firebaseApp);
    assertThat(two.getOptions()).isSameAs(firebaseApp.getOptions());
    assertThat(two.getOne()).isSameAs(one);
  }

  @Test
  public void testDirectBoot_shouldInitializeEagerComponentsOnDeviceUnlock() {
    Context mockContext = createForwardingMockContext();

    isDeviceProtectedStorage.set(true);
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);

    InitTracker tracker = firebaseApp.get(InitTracker.class);
    EagerSdkVerifier sdkVerifier = firebaseApp.get(EagerSdkVerifier.class);

    // APIs are not initialized.
    assertThat(tracker.isInitialized()).isFalse();

    assertThat(sdkVerifier.isAuthInitialized()).isFalse();
    assertThat(sdkVerifier.isAnalyticsInitialized()).isFalse();

    // User unlocks the device.
    isDeviceProtectedStorage.set(false);
    Intent userUnlockBroadcast = new Intent(Intent.ACTION_USER_UNLOCKED);
    localBroadcastManager.sendBroadcastSync(userUnlockBroadcast);

    // APIs are initialized.
    assertThat(tracker.isInitialized()).isTrue();

    assertThat(sdkVerifier.isAuthInitialized()).isTrue();
    assertThat(sdkVerifier.isAnalyticsInitialized()).isTrue();
  }

  public static class DefaultIdTokenListener implements IdTokenListener {
    private Map<IdTokenListener, Integer> calls;

    public DefaultIdTokenListener(Map<IdTokenListener, Integer> calls) {
      this.calls = calls;
    }

    @Override
    public void onIdTokenChanged(@NonNull InternalTokenResult tokenResult) {
      if (!calls.containsKey(this)) {
        calls.put(this, 0);
      }
      calls.put(this, calls.get(this) + 1);
    }
  };

  @Test
  public void testIdTokenListener() {
    Context mockContext = createForwardingMockContext();
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);
    Map<IdTokenListener, Integer> calls = new HashMap<>();
    IdTokenListener listener1 = new DefaultIdTokenListener(calls);
    IdTokenListener listener2 = new DefaultIdTokenListener(calls);
    firebaseApp.addIdTokenListener(listener1);
    firebaseApp.addIdTokenListener(listener2);
    firebaseApp.notifyIdTokenListeners(null);
    firebaseApp.removeIdTokenListener(listener2);
    firebaseApp.notifyIdTokenListeners(null);
    assertThat(calls.get(listener2)).isEqualTo(1);
    assertThat(calls.get(listener1)).isEqualTo(2);
  }

  /** Returns mock context that forwards calls to targetContext and localBroadcastManager. */
  private Context createForwardingMockContext() {
    final ContextWrapper applicationContextWrapper =
        new ContextWrapper(targetContext) {
          @Override
          public boolean isDeviceProtectedStorage() {
            return isDeviceProtectedStorage.get();
          }

          @Override
          public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
            localBroadcastManager.registerReceiver(receiver, filter);
            return null;
          }

          @Override
          public void unregisterReceiver(BroadcastReceiver receiver) {
            localBroadcastManager.unregisterReceiver(receiver);
          }
        };
    ContextWrapper contextWrapper =
        new ContextWrapper(targetContext) {
          @Override
          public Context getApplicationContext() {
            return applicationContextWrapper;
          }
        };

    return contextWrapper;
  }

  private static void invokePublicInstanceMethodWithDefaultValues(Object instance, Method method)
      throws InvocationTargetException, IllegalAccessException {
    List<Object> parameters = new ArrayList<>(method.getParameterTypes().length);
    for (Class<?> parameterType : method.getParameterTypes()) {
      parameters.add(Defaults.defaultValue(parameterType));
    }
    method.invoke(instance, parameters.toArray());
  }
}
