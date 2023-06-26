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

package com.google.firebase.appdistribution.impl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UNKNOWN;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitAsyncOperations;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitTask;
import static com.google.firebase.appdistribution.impl.TestUtils.awaitTaskFailure;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.core.content.pm.ApplicationInfoBuilder;
import androidx.test.core.content.pm.PackageInfoBuilder;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.impl.FirebaseAppDistributionServiceImplTest.TestActivity;
import com.google.firebase.concurrent.TestOnlyExecutors;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(RobolectricTestRunner.class)
public class TesterSignInManagerTest {
  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_FID_1 = "cccccccccccccccccccccc";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String TEST_IAS_ARTIFACT_ID = "ias-artifact-id";
  private static final String IAS_ARTIFACT_ID_KEY = "com.android.vending.internal.apk.id";
  private static final long INSTALLED_VERSION_CODE = 2;

  public static final String TEST_URL =
      String.format(
          "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts"
              + "?appName=com.google.firebase.appdistribution.impl.test"
              + "&packageName=com.google.firebase.appdistribution.impl.test"
              + "&newRedirectScheme=true",
          TEST_APP_ID_1, TEST_FID_1);

  private TesterSignInManager testerSignInManager;
  private TestActivity activity;
  private ShadowActivity shadowActivity;
  private ShadowPackageManager shadowPackageManager;
  @Lightweight private ExecutorService lightweightExecutor = TestOnlyExecutors.lite();
  @Background private ExecutorService backgroundExecutor = TestOnlyExecutors.background();
  private SignInStorage signInStorage;

  @Mock private Provider<FirebaseInstallationsApi> mockFirebaseInstallationsProvider;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private InstallationTokenResult mockInstallationTokenResult;
  @Mock private FirebaseAppDistributionLifecycleNotifier mockLifecycleNotifier;
  @Mock private SignInResultActivity mockSignInResultActivity;
  @Mock private DevModeDetector devModeDetector;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    FirebaseApp.clearInstancesForTest();

    FirebaseApp firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    when(mockFirebaseInstallationsProvider.get()).thenReturn(mockFirebaseInstallations);
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(TEST_FID_1));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(mockInstallationTokenResult));
    when(mockInstallationTokenResult.getToken()).thenReturn(TEST_AUTH_TOKEN);
    when(devModeDetector.isDevModeEnabled()).thenReturn(false);

    signInStorage =
        spy(
            new SignInStorage(
                ApplicationProvider.getApplicationContext(), devModeDetector, backgroundExecutor));

    shadowPackageManager =
        shadowOf(ApplicationProvider.getApplicationContext().getPackageManager());

    ApplicationInfo applicationInfo =
        ApplicationInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .build();
    applicationInfo.metaData = new Bundle();
    applicationInfo.metaData.putString(IAS_ARTIFACT_ID_KEY, TEST_IAS_ARTIFACT_ID);
    PackageInfo packageInfo =
        PackageInfoBuilder.newBuilder()
            .setPackageName(ApplicationProvider.getApplicationContext().getPackageName())
            .setApplicationInfo(applicationInfo)
            .build();
    packageInfo.setLongVersionCode(INSTALLED_VERSION_CODE);
    shadowPackageManager.installPackage(packageInfo);

    activity = Robolectric.buildActivity(TestActivity.class).create().get();
    shadowActivity = shadowOf(activity);
    TestUtils.mockForegroundActivity(mockLifecycleNotifier, activity);

    testerSignInManager =
        new TesterSignInManager(
            firebaseApp.getApplicationContext(),
            firebaseApp.getOptions(),
            mockFirebaseInstallationsProvider,
            signInStorage,
            mockLifecycleNotifier,
            devModeDetector,
            lightweightExecutor);
  }

  @Test
  public void signInTester_alreadySignedIn_doesNothing()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    TestUtils.awaitTask(signInStorage.setSignInStatus(true));

    Task signInTask = testerSignInManager.signInTester();
    awaitTask(signInTask);

    assertThat(signInTask.isSuccessful()).isTrue();
    verifyNoInteractions(mockFirebaseInstallationsProvider);
    verifyNoInteractions(mockFirebaseInstallations);
  }

  @Test
  public void signInTester_whenCantGetFid_failsWithAuthenticationFailure() {
    Exception fisException = new Exception("fis exception");
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forException(fisException));

    Task signInTask = testerSignInManager.signInTester();

    awaitTaskFailure(
        signInTask, Status.AUTHENTICATION_FAILURE, "Failed to authenticate", fisException);
  }

  @Test
  public void signInTester_whenUnexpectedFailureInTask_failsWithUnknownError() {
    Exception unexpectedException = new Exception("unexpected exception");
    // Raise an unexpected exception in our handler passed to consumeForegroundActivity
    when(mockLifecycleNotifier.consumeForegroundActivity(any()))
        .thenAnswer(unused -> Tasks.forException(unexpectedException));

    Task signInTask = testerSignInManager.signInTester();

    awaitTaskFailure(signInTask, UNKNOWN, "Unknown", unexpectedException);
  }

  @Test
  public void signInTester_whenChromeAvailable_opensCustomTab() throws InterruptedException {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    Intent customTabIntent = new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    shadowPackageManager.addResolveInfoForIntent(customTabIntent, resolveInfo);

    testerSignInManager.signInTester();
    awaitAsyncOperations(backgroundExecutor);
    awaitAsyncOperations(lightweightExecutor);

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenChromeNotAvailable_opensBrowserIntent() throws InterruptedException {
    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.resolvePackageName = "garbage";
    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL));
    shadowPackageManager.addResolveInfoForIntent(browserIntent, resolveInfo);

    testerSignInManager.signInTester();
    awaitAsyncOperations(backgroundExecutor);
    awaitAsyncOperations(lightweightExecutor);

    verify(mockFirebaseInstallations, times(1)).getId();
    assertThat(shadowActivity.getNextStartedActivity().getData()).isEqualTo(Uri.parse(TEST_URL));
  }

  @Test
  public void signInTester_whenSignInCalledMultipleTimes_secondCallHasNoEffect()
      throws InterruptedException {
    testerSignInManager.signInTester();
    testerSignInManager.signInTester();

    awaitAsyncOperations(backgroundExecutor);
    awaitAsyncOperations(lightweightExecutor);

    verify(mockFirebaseInstallationsProvider, times(1)).get();
  }

  @Test
  public void signInTester_whenReturnFromSignIn_taskSucceeds()
      throws InterruptedException, FirebaseAppDistributionException, ExecutionException {
    Task signInTask = testerSignInManager.signInTester();
    awaitAsyncOperations(backgroundExecutor);
    awaitAsyncOperations(lightweightExecutor);

    // Simulate re-entering app after successful sign in, via SignInResultActivity
    testerSignInManager.onActivityCreated(mockSignInResultActivity);
    awaitTask(signInTask);

    assertTrue(signInTask.isSuccessful());
    assertThat(signInStorage.getSignInStatusBlocking()).isTrue();
  }

  @Test
  public void signInTester_whenStorageFailsToRecordSignInStatus_taskFails()
      throws InterruptedException {
    Exception expectedException = new RuntimeException("Error");
    doReturn(Tasks.forException(expectedException))
        .when(signInStorage)
        .setSignInStatus(anyBoolean());
    Task signInTask = testerSignInManager.signInTester();
    awaitAsyncOperations(backgroundExecutor);
    awaitAsyncOperations(lightweightExecutor);

    // Simulate re-entering app after successful sign in, via SignInResultActivity
    testerSignInManager.onActivityCreated(mockSignInResultActivity);

    awaitTaskFailure(signInTask, UNKNOWN, "Error storing tester sign in state", expectedException);
  }

  @Test
  public void signInTester_whenAppReenteredDuringSignIn_taskFails() throws InterruptedException {
    Task signInTask = testerSignInManager.signInTester();
    awaitAsyncOperations(backgroundExecutor);
    awaitAsyncOperations(lightweightExecutor);

    // Simulate re-entering app before completing sign in
    testerSignInManager.onActivityResumed(activity);

    awaitTaskFailure(signInTask, AUTHENTICATION_CANCELED, ErrorMessages.AUTHENTICATION_CANCELED);
  }

  @Test
  public void signInTester_devModeEnabled_immediatelySignsIn()
      throws FirebaseAppDistributionException, ExecutionException, InterruptedException {
    when(devModeDetector.isDevModeEnabled()).thenReturn(true);

    awaitTask(testerSignInManager.signInTester());

    assertThat(awaitTask(signInStorage.getSignInStatus())).isTrue();
    verifyNoInteractions(mockFirebaseInstallationsProvider);
    verifyNoInteractions(mockFirebaseInstallations);
  }
}
