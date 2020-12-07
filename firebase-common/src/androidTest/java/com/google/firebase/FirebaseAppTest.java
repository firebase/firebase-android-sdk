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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.common.base.Defaults;
import com.google.firebase.components.EagerSdkVerifier;
import com.google.firebase.components.TestComponentOne;
import com.google.firebase.components.TestComponentTwo;
import com.google.firebase.components.TestUserAgentDependentComponent;
import com.google.firebase.platforminfo.UserAgentPublisher;
import com.google.firebase.testing.FirebaseAppRule;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

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

  private final AtomicBoolean isUserUnlocked = new AtomicBoolean(true);
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

    // After sorting the user agents are expected to be {"fire-android/", "fire-auth/x.y.z",
    // "fire-core/x.y.z", "test-component/1.2.3"}
    assertThat(actualUserAgent[0]).contains("android-installer");
    assertThat(actualUserAgent[1]).contains("android-min-sdk/14");
    assertThat(actualUserAgent[2]).contains("android-platform");
    assertThat(actualUserAgent[3]).contains("android-target-sdk");
    assertThat(actualUserAgent[4]).contains("device-brand");
    assertThat(actualUserAgent[5]).contains("device-model");
    assertThat(actualUserAgent[6]).contains("device-name");
    assertThat(actualUserAgent[7]).contains("fire-android");
    assertThat(actualUserAgent[8]).contains("fire-core");
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
            "addBackgroundStateChangeListener",
            "delete",
            "equals",
            "getListeners",
            "getPersistenceKey",
            "hashCode",
            "isDefaultApp",
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
    FirebaseApp.getInstance();
  }

  @Test
  public void testApiInitializedForNonDefaultApp() {
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext, OPTIONS, "myApp");
    assertThat(firebaseApp.isDefaultApp()).isFalse();

    EagerSdkVerifier sdkVerifier = firebaseApp.get(EagerSdkVerifier.class);
    assertThat(sdkVerifier.isEagerInitialized()).isTrue();
    assertThat(sdkVerifier.isEagerInDefaultAppInitialized()).isFalse();
    assertThat(sdkVerifier.isLazyInitialized()).isFalse();
  }

  @Test
  public void testApiInitForDefaultApp() {
    // Explicit initialization of FirebaseApp instance.
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(targetContext);
    assertThat(firebaseApp.isDefaultApp()).isTrue();

    EagerSdkVerifier sdkVerifier = firebaseApp.get(EagerSdkVerifier.class);
    assertThat(sdkVerifier.isEagerInitialized()).isTrue();
    assertThat(sdkVerifier.isEagerInDefaultAppInitialized()).isTrue();
    assertThat(sdkVerifier.isLazyInitialized()).isFalse();
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

    assertThat(firebaseApp.get(Context.class)).isSameInstanceAs(appContext);
    assertThat(firebaseApp.get(FirebaseApp.class)).isSameInstanceAs(firebaseApp);

    TestComponentOne one = firebaseApp.get(TestComponentOne.class);
    assertThat(one).isNotNull();
    assertThat(one.getContext()).isSameInstanceAs(appContext);

    TestComponentTwo two = firebaseApp.get(TestComponentTwo.class);
    assertThat(two).isNotNull();
    assertThat(two.getApp()).isSameInstanceAs(firebaseApp);
    assertThat(two.getOptions()).isSameInstanceAs(firebaseApp.getOptions());
    assertThat(two.getOne()).isSameInstanceAs(one);
  }

  @Test
  public void testDirectBoot_shouldInitializeEagerComponentsOnDeviceUnlock() {
    Context mockContext = createForwardingMockContext();

    isUserUnlocked.set(false);
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);

    EagerSdkVerifier sdkVerifier = firebaseApp.get(EagerSdkVerifier.class);

    assertThat(sdkVerifier.isEagerInitialized()).isFalse();
    assertThat(sdkVerifier.isEagerInDefaultAppInitialized()).isFalse();
    assertThat(sdkVerifier.isLazyInitialized()).isFalse();

    // User unlocks the device.
    isUserUnlocked.set(true);
    Intent userUnlockBroadcast = new Intent(Intent.ACTION_USER_UNLOCKED);
    localBroadcastManager.sendBroadcastSync(userUnlockBroadcast);

    assertThat(sdkVerifier.isEagerInitialized()).isTrue();
    assertThat(sdkVerifier.isEagerInDefaultAppInitialized()).isTrue();
    assertThat(sdkVerifier.isLazyInitialized()).isFalse();
  }

  @Test
  public void testDirectBoot_shouldPreserveDataCollectionAfterUnlock() {
    Context mockContext = createForwardingMockContext();

    isUserUnlocked.set(false);
    FirebaseApp firebaseApp = FirebaseApp.initializeApp(mockContext);
    assert (firebaseApp != null);
    firebaseApp.setDataCollectionDefaultEnabled(false);
    assertFalse(firebaseApp.isDataCollectionDefaultEnabled());
    // User unlocks the device.
    isUserUnlocked.set(true);
    Intent userUnlockBroadcast = new Intent(Intent.ACTION_USER_UNLOCKED);
    localBroadcastManager.sendBroadcastSync(userUnlockBroadcast);

    assertFalse(firebaseApp.isDataCollectionDefaultEnabled());
    firebaseApp.setDataCollectionDefaultEnabled(true);
    assertTrue(firebaseApp.isDataCollectionDefaultEnabled());
    firebaseApp.setDataCollectionDefaultEnabled(false);
    assertFalse(firebaseApp.isDataCollectionDefaultEnabled());
    // Because default is true.
    firebaseApp.setDataCollectionDefaultEnabled(null);
    assertTrue(firebaseApp.isDataCollectionDefaultEnabled());
  }

  /** Returns mock context that forwards calls to targetContext and localBroadcastManager. */
  private Context createForwardingMockContext() {
    final UserManager spyUserManager = spy(targetContext.getSystemService(UserManager.class));
    when(spyUserManager.isUserUnlocked())
        .thenAnswer((Answer<Boolean>) invocation -> isUserUnlocked.get());
    final ContextWrapper applicationContextWrapper =
        new ContextWrapper(targetContext) {
          @Override
          public Object getSystemService(String name) {
            Object original = super.getSystemService(name);
            if (original == null) {
              return null;
            }
            if (original instanceof UserManager) {
              return spyUserManager;
            }
            return original;
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
