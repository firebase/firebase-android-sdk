// Copyright 2026 Google LLC
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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.cloudmessaging.NotificationCallback;
import com.google.android.gms.cloudmessaging.internal.ICloudMessageListenerService;
import com.google.android.gms.cloudmessaging.internal.ICompletionCallback;
import com.google.android.gms.cloudmessaging.internal.INotificationCallback;
import com.google.firebase.messaging.testing.TestImageServer;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowNotificationManager;

/**
 * Tests the listener service Google Play services binds to, end to end: the AIDL call in, the bind
 * to the developer's service, the callback out.
 *
 * <p>SDK coverage is chosen per test rather than by branching on {@link
 * android.os.Build.VERSION#SDK_INT}. The tests that touch a notification channel run from O
 * upwards; the rest do not vary by version, so they pin to O.
 */
@RunWith(AndroidJUnit4.class)
@Config(manifest = Config.NONE)
public class FirebaseMessagingListenerServiceRoboTest {

  private static final long BIND_TIMEOUT_S = 5;
  private static final String APP_CHANNEL = "test_channel";

  @Rule public final TestImageServer testImageServer = new TestImageServer();

  private Context context;
  private ServiceController<FirebaseMessagingListenerService> controller;
  private FirebaseMessagingListenerService service;
  private ICloudMessageListenerService binder;
  private INotificationCallback mockNotificationCallback;
  private ICompletionCallback mockCompletionCallback;

  private Notification notification;

  // Fake service to be resolved by ServiceStarter
  public static class FakeFirebaseMessagingService extends FirebaseMessagingService {
    static NotificationResponse nextResponse = NotificationResponse.suppress();
    static boolean onNotificationReceivedCalled = false;
    static boolean onBackgroundSyncReceivedCalled = false;
    static RemoteMessage lastMessage;

    /** Name of the thread the last callback ran on. */
    static String lastThreadName;

    interface Delegate {
      NotificationResponse onNotificationReceived(
          RemoteMessage message, FakeFirebaseMessagingService service);
    }

    interface SyncDelegate {
      void onBackgroundSyncReceived(RemoteMessage message, FakeFirebaseMessagingService service);
    }

    static Delegate delegate;
    static SyncDelegate syncDelegate;

    static void reset() {
      nextResponse = NotificationResponse.suppress();
      onNotificationReceivedCalled = false;
      onBackgroundSyncReceivedCalled = false;
      lastMessage = null;
      lastThreadName = null;
      delegate = null;
      syncDelegate = null;
    }

    @Override
    public NotificationResponse onNotificationReceived(@NonNull RemoteMessage message) {
      onNotificationReceivedCalled = true;
      lastMessage = message;
      lastThreadName = Thread.currentThread().getName();
      if (delegate != null) {
        return delegate.onNotificationReceived(message, this);
      }
      return nextResponse;
    }

    @Override
    public void onBackgroundSyncReceived(@NonNull RemoteMessage message) {
      onBackgroundSyncReceivedCalled = true;
      lastMessage = message;
      lastThreadName = Thread.currentThread().getName();
      if (syncDelegate != null) {
        syncDelegate.onBackgroundSyncReceived(message, this);
      }
    }

    /**
     * Runs the default {@link FirebaseMessagingService#onNotificationReceived} implementation, as
     * an app that left the callback unimplemented or that calls {@code super} from its override
     * would.
     */
    NotificationResponse callSuperOnNotificationReceived(RemoteMessage message) {
      return super.onNotificationReceived(message);
    }

    /**
     * Runs the default {@link FirebaseMessagingService#onBackgroundSyncReceived} implementation, as
     * an app that left the callback unimplemented or that calls {@code super} from its override
     * would.
     */
    void callSuperOnBackgroundSyncReceived(RemoteMessage message) {
      super.onBackgroundSyncReceived(message);
    }
  }

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    setTargetSdkVersion(VERSION_CODES.O);
    FakeFirebaseMessagingService.reset();

    notification = buildNotification(APP_CHANNEL);
    mockNotificationCallback = mock(INotificationCallback.class);
    mockCompletionCallback = mock(ICompletionCallback.class);

    startListenerService();
    publishDeveloperService();

    // Report the connection from inside bindService() so that tests which do not care about the
    // timing do not have to drive the looper first. The tests that do care turn this back off.
    shadowApplication().setBindServiceCallsOnServiceConnectedDirectly(true);

    // Declare both services in the app's default process. Without this the package manager has no
    // record of the components and the same-process check cannot compare them, so registering them
    // is what makes these tests exercise the real comparison.
    declareServiceProcesses(context.getPackageName(), context.getPackageName());
  }

  /** Creates the service under test and takes the binder Google Play services would be handed. */
  private void startListenerService() {
    controller = Robolectric.buildService(FirebaseMessagingListenerService.class);
    service = controller.create().get();
    IBinder iBinder = service.onBind(new Intent(context, FirebaseMessagingListenerService.class));
    binder = ICloudMessageListenerService.Stub.asInterface(iBinder);
  }

  /**
   * Makes the developer's service both discoverable and bindable: {@link ServiceStarter} resolves
   * it by intent, and the listener then binds to it.
   */
  private void publishDeveloperService() {
    FakeFirebaseMessagingService fakeService =
        Robolectric.setupService(FakeFirebaseMessagingService.class);
    IBinder fakeServiceBinder =
        fakeService.onBind(new Intent(FirebaseMessagingService.ACTION_BIND_LISTENER));
    returnBinderOnBind(fakeServiceBinder);

    ResolveInfo resolveInfo = new ResolveInfo();
    resolveInfo.serviceInfo = serviceInfo(FakeFirebaseMessagingService.class.getName(), null);
    Intent messagingEventIntent = new Intent(ServiceStarter.ACTION_MESSAGING_EVENT);
    messagingEventIntent.setPackage(context.getPackageName());
    shadowOf(context.getPackageManager())
        .addResolveInfoForIntent(messagingEventIntent, resolveInfo);
  }

  /** Makes a bind to the developer's service hand back {@code binderToReturn}. */
  private void returnBinderOnBind(IBinder binderToReturn) {
    Intent bindIntent = new Intent(FirebaseMessagingService.ACTION_BIND_LISTENER);
    bindIntent.setClassName(context, FakeFirebaseMessagingService.class.getName());
    shadowApplication()
        .setComponentNameAndServiceForBindServiceForIntent(
            bindIntent,
            new ComponentName(context, FakeFirebaseMessagingService.class),
            binderToReturn);
  }

  private void setTargetSdkVersion(int version) throws Exception {
    int flags =
        PackageManager.GET_META_DATA
            | PackageManager.GET_SERVICES
            | PackageManager.GET_ACTIVITIES
            | PackageManager.GET_RECEIVERS;
    PackageManager pm = context.getPackageManager();
    PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), flags);
    packageInfo.applicationInfo.targetSdkVersion = version;
    shadowOf(pm).installPackage(packageInfo);
  }

  /** Declares the process that each of the two services runs in, as the merged manifest would. */
  private void declareServiceProcesses(String messagingServiceProcess, String listenerProcess) {
    shadowOf(context.getPackageManager())
        .addOrUpdateService(
            serviceInfo(FakeFirebaseMessagingService.class.getName(), messagingServiceProcess));
    shadowOf(context.getPackageManager())
        .addOrUpdateService(
            serviceInfo(FirebaseMessagingListenerService.class.getName(), listenerProcess));
  }

  private ServiceInfo serviceInfo(String className, String processName) {
    ServiceInfo info = new ServiceInfo();
    info.packageName = context.getPackageName();
    info.name = className;
    info.processName = processName;
    return info;
  }

  private ShadowApplication shadowApplication() {
    return shadowOf((Application) context);
  }

  private List<ServiceConnection> boundConnections() {
    return shadowApplication().getBoundServiceConnections();
  }

  private NotificationManager notificationManager() {
    return context.getSystemService(NotificationManager.class);
  }

  private ShadowNotificationManager shadowNotificationManager() {
    return shadowOf(notificationManager());
  }

  private void createChannel(String channelId) {
    notificationManager()
        .createNotificationChannel(
            new NotificationChannel(channelId, "Test", NotificationManager.IMPORTANCE_DEFAULT));
  }

  private Notification buildNotification(String channelId) {
    return new Notification.Builder(context, channelId)
        .setContentTitle("Title")
        .setContentText("Text")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .build();
  }

  private static Bundle payload(String messageId) {
    Bundle payload = new Bundle();
    payload.putString("google.message_id", messageId);
    return payload;
  }

  private static Answer<Void> countDown(CountDownLatch latch) {
    return invocation -> {
      latch.countDown();
      return null;
    };
  }

  /** Returns a latch released when the callback is answered, whether successfully or not. */
  private static CountDownLatch completionLatch(INotificationCallback callback)
      throws RemoteException {
    CountDownLatch latch = new CountDownLatch(1);
    countDownOnCompletion(callback, latch);
    return latch;
  }

  /** Returns a latch released when the callback is answered, whether successfully or not. */
  private static CountDownLatch completionLatch(ICompletionCallback callback)
      throws RemoteException {
    CountDownLatch latch = new CountDownLatch(1);
    doAnswer(countDown(latch)).when(callback).onSuccess();
    doAnswer(countDown(latch)).when(callback).onError(anyInt());
    return latch;
  }

  private static void countDownOnCompletion(INotificationCallback callback, CountDownLatch latch)
      throws RemoteException {
    doAnswer(countDown(latch)).when(callback).onSuccess();
    doAnswer(countDown(latch)).when(callback).onError(anyInt());
  }

  /**
   * Waits for the callback to be answered, draining the main looper while it waits. Work keeps
   * arriving there past a single drain: the connection is reported on it, and so is the unbind that
   * follows delivery.
   */
  private static void awaitCompletion(CountDownLatch latch) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BIND_TIMEOUT_S);
    while (!latch.await(10, TimeUnit.MILLISECONDS)) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(
            "Timed out after "
                + BIND_TIMEOUT_S
                + "s waiting for the callback to be answered. Still outstanding: "
                + latch.getCount());
      }
      idleMainLooper();
    }
  }

  private static void idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  /** Waits for bindService() to have been called, without letting the connection be reported. */
  private void awaitBindRequested() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BIND_TIMEOUT_S);
    while (boundConnections().isEmpty()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError(
            "Timed out after " + BIND_TIMEOUT_S + "s waiting for bindService() to be called");
      }
      Thread.sleep(10);
    }
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_delegatesToService() throws Exception {
    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("tag", 42, notification);

    // Ensure the channel exists so that this test does not also exercise auto-creation.
    createChannel(APP_CHANNEL);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    Bundle payload = payload("msg_delegates");
    payload.putString("title", "Hello");

    binder.onNotificationReceived(payload, mockNotificationCallback);

    // Idle looper to allow local bind to complete and trigger service connection
    idleMainLooper();

    // Wait for background thread to complete processing
    awaitCompletion(latch);
    idleMainLooper(); // Idle again for unbind runnables

    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isTrue();
    assertThat(FakeFirebaseMessagingService.lastMessage.getData().get("title")).isEqualTo("Hello");

    verify(mockNotificationCallback).onNotificationPosted(eq("tag"), eq(42), anyLong());
    verify(mockNotificationCallback).onSuccess();

    assertThat(shadowNotificationManager().getNotification("tag", 42)).isNotNull();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testOnNotificationReceived_channelMissing_doesNotReportPosted() throws Exception {
    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("tag", 42, notification);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_missing_channel"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    verify(mockNotificationCallback, never()).onNotificationPosted(eq("tag"), anyInt(), anyLong());
    verify(mockNotificationCallback).onSuccess();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testOnNotificationReceived_channelImportanceNone_doesNotReportPosted()
      throws Exception {
    notificationManager()
        .createNotificationChannel(
            new NotificationChannel(APP_CHANNEL, "Test", NotificationManager.IMPORTANCE_NONE));
    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("tag", 42, notification);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_blocked_channel"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    verify(mockNotificationCallback, never()).onNotificationPosted(eq("tag"), anyInt(), anyLong());
    verify(mockNotificationCallback).onSuccess();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testOnNotificationReceived_mixedChannels_reportsOnlyValidPost() throws Exception {
    createChannel(APP_CHANNEL);
    Notification missingChannelNotification = buildNotification("missing_channel");
    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("bad_tag", 41, missingChannelNotification)
            .add("good_tag", 42, notification);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_mixed_channels"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    verify(mockNotificationCallback, never())
        .onNotificationPosted(eq("bad_tag"), anyInt(), anyLong());
    verify(mockNotificationCallback).onNotificationPosted(eq("good_tag"), eq(42), anyLong());
    verify(mockNotificationCallback).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_unimplemented_fallsBackToBroadcast() throws Exception {
    // Runs the real default implementation, which is what an app that never overrode the callback,
    // or that calls super from its override, ends up executing.
    FakeFirebaseMessagingService.delegate =
        (message, svc) -> svc.callSuperOnNotificationReceived(message);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_unimplemented"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    verify(mockNotificationCallback).onError(eq(NotificationCallback.ERROR_UNIMPLEMENTED));
    verify(mockNotificationCallback, never()).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnBackgroundSyncReceived_delegatesToService() throws Exception {
    CountDownLatch latch = completionLatch(mockCompletionCallback);

    Bundle payload = payload("msg_sync_delegates");
    payload.putString("sync_type", "full");

    binder.onBackgroundSyncReceived(payload, mockCompletionCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    assertThat(FakeFirebaseMessagingService.onBackgroundSyncReceivedCalled).isTrue();
    assertThat(FakeFirebaseMessagingService.lastMessage.getData().get("sync_type"))
        .isEqualTo("full");

    verify(mockCompletionCallback).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnBackgroundSyncReceived_unimplemented_fallsBackToBroadcast() throws Exception {
    // Runs the real default implementation, which is what an app that never overrode the callback,
    // or that calls super from its override, ends up executing.
    FakeFirebaseMessagingService.syncDelegate =
        (message, svc) -> svc.callSuperOnBackgroundSyncReceived(message);

    CountDownLatch latch = completionLatch(mockCompletionCallback);

    binder.onBackgroundSyncReceived(payload("msg_sync_unimplemented"), mockCompletionCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    verify(mockCompletionCallback).onError(eq(NotificationCallback.ERROR_UNIMPLEMENTED));
    verify(mockCompletionCallback, never()).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_streaming() throws Exception {
    createChannel(APP_CHANNEL);

    FakeFirebaseMessagingService.delegate =
        (message, svc) -> {
          FcmNotificationManager manager = svc.getFcmNotificationManager(message);
          manager.postNow("tag1", 101, notification);
          manager.postNow("tag2", 102, notification);
          manager.cancelNow("tag2", 102);
          return manager.finished();
        };

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    Bundle payload = payload("msg_streaming");
    payload.putString("title", "Hello");
    binder.onNotificationReceived(payload, mockNotificationCallback);

    awaitCompletion(latch);
    idleMainLooper();

    verify(mockNotificationCallback).onNotificationPosted(eq("tag1"), eq(101), anyLong());
    verify(mockNotificationCallback).onNotificationPosted(eq("tag2"), eq(102), anyLong());
    verify(mockNotificationCallback).onNotificationCancelled(eq("tag2"), eq(102), anyLong());
    verify(mockNotificationCallback).onSuccess();

    assertThat(shadowNotificationManager().getNotification("tag1", 101)).isNotNull();
    assertThat(shadowNotificationManager().getNotification("tag2", 102)).isNull();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testPostNotification_fallback() throws Exception {
    createChannel(APP_CHANNEL);

    CountDownLatch postLatch = completionLatch(mockCompletionCallback);

    Bundle payload = new Bundle();
    payload.putString("gcm.n.tag", "tag_fallback");
    payload.putString("gcm.n.title", "Fallback Title");
    payload.putString("gcm.n.body", "Fallback Body");
    payload.putString("gcm.n.android_channel_id", APP_CHANNEL);

    binder.postNotification(payload, mockCompletionCallback);
    awaitCompletion(postLatch);
    idleMainLooper();

    assertThat(shadowNotificationManager().size()).isEqualTo(1);

    Notification posted = shadowNotificationManager().getNotification("tag_fallback", 0);
    assertThat(posted).isNotNull();
    assertThat(posted.getChannelId()).isEqualTo(APP_CHANNEL);
    verify(mockCompletionCallback).onSuccess();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testPostNotification_withImage() throws Exception {
    createChannel(APP_CHANNEL);

    Bitmap sourceBitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888);
    ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
    sourceBitmap.compress(Bitmap.CompressFormat.PNG, /* quality= */ 100, pngBytes);
    byte[] imageData = pngBytes.toByteArray();
    Bitmap expectedBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
    String url = testImageServer.serveByteArray("/image", imageData);

    CountDownLatch postLatch = completionLatch(mockCompletionCallback);

    Bundle payload = new Bundle();
    payload.putString("gcm.n.tag", "tag_image");
    payload.putString("gcm.n.title", "Image Title");
    payload.putString("gcm.n.body", "Image Body");
    payload.putString("gcm.n.image", url);
    payload.putString("gcm.n.android_channel_id", APP_CHANNEL);

    binder.postNotification(payload, mockCompletionCallback);
    awaitCompletion(postLatch);
    idleMainLooper();

    Notification posted = shadowNotificationManager().getNotification("tag_image", 0);
    assertThat(posted).isNotNull();
    assertThat(shadowOf(posted.getLargeIcon()).getBitmap().sameAs(expectedBitmap)).isTrue();
    assertThat(shadowOf(posted).getBigPicture().sameAs(expectedBitmap)).isTrue();
    verify(mockCompletionCallback).onSuccess();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testPostNotification_imageDownloadFail_stillPostsNotification() throws Exception {
    createChannel(APP_CHANNEL);

    String url = testImageServer.serveError("/error");

    CountDownLatch postLatch = completionLatch(mockCompletionCallback);

    Bundle payload = new Bundle();
    payload.putString("gcm.n.tag", "tag_image_error");
    payload.putString("gcm.n.title", "Error Image Title");
    payload.putString("gcm.n.body", "Error Image Body");
    payload.putString("gcm.n.image", url);
    payload.putString("gcm.n.android_channel_id", APP_CHANNEL);

    binder.postNotification(payload, mockCompletionCallback);
    awaitCompletion(postLatch);
    idleMainLooper();

    Notification posted = shadowNotificationManager().getNotification("tag_image_error", 0);
    assertThat(posted).isNotNull();
    assertThat(posted.getLargeIcon()).isNull();
    verify(mockCompletionCallback).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testCancelNotification_fallback() throws Exception {
    createChannel(APP_CHANNEL);

    notificationManager().notify("tag_cancel", 888, notification);
    assertThat(shadowNotificationManager().size()).isEqualTo(1);

    CountDownLatch cancelLatch = completionLatch(mockCompletionCallback);

    binder.cancelNotification("tag_cancel", 888, mockCompletionCallback);
    awaitCompletion(cancelLatch);
    idleMainLooper();

    assertThat(shadowNotificationManager().size()).isEqualTo(0);
    verify(mockCompletionCallback).onSuccess();
  }

  @Test
  @Config(minSdk = VERSION_CODES.O)
  public void testCreateNotificationChannel_fallback() throws Exception {
    assertThat(notificationManager().getNotificationChannel("fallback_channel_999")).isNull();

    CountDownLatch channelLatch = completionLatch(mockCompletionCallback);

    binder.createNotificationChannel(
        "fallback_channel_999", "Fallback Channel Name", mockCompletionCallback);
    awaitCompletion(channelLatch);
    idleMainLooper();

    NotificationChannel channel =
        notificationManager().getNotificationChannel("fallback_channel_999");
    assertThat(channel).isNotNull();
    assertThat(channel.getName().toString()).isEqualTo("Fallback Channel Name");
    verify(mockCompletionCallback).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_reusesCachedConnection() throws Exception {
    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("tag_cached", 1, notification);

    createChannel(APP_CHANNEL);

    CountDownLatch latch1 = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_cached_1"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch1);
    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isTrue();

    // Reset flag for second message
    FakeFirebaseMessagingService.onNotificationReceivedCalled = false;

    INotificationCallback callback2 = mock(INotificationCallback.class);
    CountDownLatch latch2 = completionLatch(callback2);

    binder.onNotificationReceived(payload("msg_cached_2"), callback2);

    // Second message completes using cached connection
    awaitCompletion(latch2);
    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isTrue();
    verify(callback2).onSuccess();

    // Verify service destroy tears down connection cleanly
    controller.destroy();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_differentProcess_doesNotBind() throws Exception {
    // The developer moved their service to a private process but left the listener behind.
    declareServiceProcesses(context.getPackageName() + ":push", context.getPackageName());
    service.resetConnectionForTesting();

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_other_process"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    // The message is reported as failed without ever reaching the developer's service, and no
    // binding is left behind.
    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isFalse();
    verify(mockNotificationCallback).onError(anyInt());
    verify(mockNotificationCallback, never()).onSuccess();
    assertThat(boundConnections()).isEmpty();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_sameCustomProcess_bindsToService() throws Exception {
    // Both services declare the same private process, which is the supported way to relocate
    // message handling. Delivery must still work.
    declareServiceProcesses(context.getPackageName() + ":push", context.getPackageName() + ":push");
    service.resetConnectionForTesting();

    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("tag_custom_process", 7, notification);

    createChannel(APP_CHANNEL);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_same_custom_process"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isTrue();
    verify(mockNotificationCallback).onSuccess();
    verify(mockNotificationCallback, never()).onError(anyInt());
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_undeclaredListenerProcess_doesNotBind() throws Exception {
    // Only the developer's service declares a process. The listener has none, which means it runs
    // in the application's default process, so the two still differ and must not be bound.
    declareServiceProcesses(context.getPackageName() + ":push", null);
    service.resetConnectionForTesting();

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_undeclared_process"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isFalse();
    verify(mockNotificationCallback).onError(anyInt());
    verify(mockNotificationCallback, never()).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnUnbind_releasesConnectionToMessagingService() throws Exception {
    FakeFirebaseMessagingService.nextResponse =
        NotificationResponse.create("unbind_tag", 77, notification);
    createChannel(APP_CHANNEL);

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_unbind"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    // Delivering the message left the listener bound to the developer's service.
    assertThat(boundConnections()).isNotEmpty();

    // Google Play services disconnects. Returning false declines the onRebind() callback, which
    // there is nothing to do in: the binder handed out by onBind() holds no per-binding state and
    // the connection to the developer's service is rebuilt on the next message.
    assertThat(service.onUnbind(new Intent(context, FirebaseMessagingListenerService.class)))
        .isFalse();
    idleMainLooper();

    // The binding to the developer's service is released rather than being left for onDestroy().
    assertThat(boundConnections()).isEmpty();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_appThrowsUnsupportedOperation_isNotReportedUnimplemented()
      throws Exception {
    // An implemented callback that happens to throw UnsupportedOperationException, for example from
    // an immutable collection. This must not be mistaken for a callback the app never implemented.
    FakeFirebaseMessagingService.delegate =
        (message, svc) -> {
          throw new UnsupportedOperationException("failure inside the app's implementation");
        };

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_app_threw_uoe"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isTrue();
    verify(mockNotificationCallback).onError(eq(0));
    verify(mockNotificationCallback, never()).onError(eq(NotificationCallback.ERROR_UNIMPLEMENTED));
    verify(mockNotificationCallback, never()).onSuccess();
  }

  @Test
  @Config(sdk = VERSION_CODES.O)
  public void
      testOnBackgroundSyncReceived_appThrowsUnsupportedOperation_isNotReportedUnimplemented()
          throws Exception {
    FakeFirebaseMessagingService.syncDelegate =
        (message, svc) -> {
          throw new UnsupportedOperationException("failure inside the app's implementation");
        };

    CountDownLatch latch = completionLatch(mockCompletionCallback);

    binder.onBackgroundSyncReceived(payload("msg_sync_app_threw_uoe"), mockCompletionCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    assertThat(FakeFirebaseMessagingService.onBackgroundSyncReceivedCalled).isTrue();
    verify(mockCompletionCallback).onError(eq(0));
    verify(mockCompletionCallback, never()).onError(eq(NotificationCallback.ERROR_UNIMPLEMENTED));
    verify(mockCompletionCallback, never()).onSuccess();
  }

  /**
   * The calling thread must be given back before the app runs. It comes from a two thread pool in
   * the cloud messaging client library that carries every message this app receives, and Google
   * Play services holds a wake lock attributed to this app until the message is answered.
   */
  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_runsAppOffTheCallingThread() throws Exception {
    String callingThread = Thread.currentThread().getName();
    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_off_thread"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);

    // Null would mean the app never ran at all, which isNotEqualTo would accept.
    assertThat(FakeFirebaseMessagingService.lastThreadName).isNotNull();
    assertThat(FakeFirebaseMessagingService.lastThreadName).isNotEqualTo(callingThread);
  }

  /**
   * Background syncs are handed over off the calling thread too, for the same reason. Which
   * executor they land on is a detail of {@link FcmExecutors} and is not asserted here.
   */
  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnBackgroundSyncReceived_runsAppOffTheCallingThread() throws Exception {
    String callingThread = Thread.currentThread().getName();
    CountDownLatch latch = completionLatch(mockCompletionCallback);

    binder.onBackgroundSyncReceived(payload("msg_sync_off_thread"), mockCompletionCallback);

    idleMainLooper();
    awaitCompletion(latch);

    assertThat(FakeFirebaseMessagingService.lastThreadName).isNotNull();
    assertThat(FakeFirebaseMessagingService.lastThreadName).isNotEqualTo(callingThread);
  }

  /**
   * A message that arrives before the connection is ready is delivered once it is, rather than
   * being dropped or waited on.
   */
  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_connectionReportedLater_stillDelivers() throws Exception {
    // Let the connection be reported through the main looper, as the platform does, instead of
    // from inside bindService().
    shadowApplication().setBindServiceCallsOnServiceConnectedDirectly(false);

    CountDownLatch delivered = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_late_connection"), mockNotificationCallback);

    awaitBindRequested();

    // Nothing has been handed to the app yet, and no thread is parked waiting for it.
    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isFalse();

    idleMainLooper();

    awaitCompletion(delivered);
    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isTrue();
    verify(mockNotificationCallback).onSuccess();
  }

  /**
   * A burst of messages arriving before the connection is ready binds once, not once per message. A
   * second {@code bindService()} would register a connection that could never be released.
   */
  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_burstDuringColdStart_bindsOnce() throws Exception {
    // Hold the connection back so that all three messages are handled while the bind is in flight.
    shadowApplication().setBindServiceCallsOnServiceConnectedDirectly(false);

    int messageCount = 3;
    CountDownLatch delivered = new CountDownLatch(messageCount);
    INotificationCallback[] callbacks = new INotificationCallback[messageCount];
    for (int i = 0; i < messageCount; i++) {
      callbacks[i] = mock(INotificationCallback.class);
      countDownOnCompletion(callbacks[i], delivered);
      binder.onNotificationReceived(payload("msg_burst_" + i), callbacks[i]);
    }

    awaitBindRequested();
    assertThat(boundConnections()).hasSize(1);

    idleMainLooper();

    awaitCompletion(delivered);
    for (INotificationCallback callback : callbacks) {
      verify(callback).onSuccess();
    }
    // Still a single connection: the later messages reused the one the first message established.
    assertThat(boundConnections()).hasSize(1);
  }

  /**
   * A binder that is not a {@link FirebaseMessagingService.LocalBinder} is rejected. The process
   * comparison fails open when the package manager cannot answer, so this backstop is required.
   */
  @Test
  @Config(sdk = VERSION_CODES.O)
  public void testOnNotificationReceived_unexpectedBinder_reportsErrorAndUnbinds()
      throws Exception {
    // Bind to something that is not the developer's service, as a cross-process bind would give us.
    returnBinderOnBind(new Binder());
    service.resetConnectionForTesting();

    CountDownLatch latch = completionLatch(mockNotificationCallback);

    binder.onNotificationReceived(payload("msg_bad_binder"), mockNotificationCallback);

    idleMainLooper();
    awaitCompletion(latch);
    idleMainLooper();

    assertThat(FakeFirebaseMessagingService.onNotificationReceivedCalled).isFalse();
    verify(mockNotificationCallback).onError(eq(0));
    verify(mockNotificationCallback, never()).onSuccess();
    // The binding was given back rather than kept as a connection that can never deliver.
    assertThat(boundConnections()).isEmpty();
  }
}
