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
package com.google.firebase.messaging;

import static com.google.firebase.messaging.FcmExecutors.newFileIOExecutor;
import static com.google.firebase.messaging.FcmExecutors.newInitExecutor;
import static com.google.firebase.messaging.FcmExecutors.newTaskExecutor;
import static com.google.firebase.messaging.FcmExecutors.newTopicsSyncExecutor;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.GuardedBy;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.TransportFactory;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.concurrent.NamedThreadFactory;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.DataCollectionDefaultChange;
import com.google.firebase.FirebaseApp;
import com.google.firebase.events.EventHandler;
import com.google.firebase.events.Subscriber;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.internal.FirebaseInstanceIdInternal;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Top level <a href="https://firebase.google.com/docs/cloud-messaging/">Firebase Cloud
 * Messaging</a> singleton that provides methods for subscribing to topics and sending upstream
 * messages.
 *
 * <p>In order to receive messages, declare an implementation of <br>
 * {@link FirebaseMessagingService} in the app manifest. To process messages, override base class
 * methods to handle any events required by the application.
 */
public class FirebaseMessaging {

  static final String TAG = "FirebaseMessaging";

  // Usually we would use the constant in GoogleApiAvailability but we don't depend on 'base'
  // so we duplicate the constant to avoid one more dependency.
  static final String GMS_PACKAGE = "com.google.android.gms";
  private static final String SEND_INTENT_ACTION = "com.google.android.gcm.intent.SEND";
  private static final String EXTRA_DUMMY_P_INTENT = "app";

  /**
   * Specifies scope used in obtaining a registration token when calling {@code
   * FirebaseInstanceId.getToken()}
   *
   * @deprecated Use {@link #getToken} instead
   */
  @Deprecated public static final String INSTANCE_ID_SCOPE = "FCM";

  private static final long MIN_DELAY_SEC = 30;
  private static final long MAX_DELAY_SEC = TimeUnit.HOURS.toSeconds(8);

  private static final String SUBTYPE_DEFAULT = "";

  /** This should only be accessed through {@link #getStore}. */
  @GuardedBy("FirebaseMessaging.class")
  private static Store store;

  private final FirebaseApp firebaseApp;
  @Nullable private final FirebaseInstanceIdInternal iid;
  private final Context context;
  private final GmsRpc gmsRpc;
  private final RequestDeduplicator requestDeduplicator;
  private final AutoInit autoInit;
  private final Executor initExecutor;
  private final Executor fileExecutor;
  private final Task<TopicsSubscriber> topicsSubscriberTask;
  private final Metadata metadata;

  @GuardedBy("this")
  private boolean syncScheduledOrRunning = false;

  private final Application.ActivityLifecycleCallbacks lifecycleCallbacks;

  @VisibleForTesting static Provider<TransportFactory> transportFactory = () -> null;

  @GuardedBy("FirebaseMessaging.class")
  @VisibleForTesting
  static ScheduledExecutorService syncExecutor;

  @NonNull
  public static synchronized FirebaseMessaging getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  @NonNull
  private static synchronized Store getStore(Context context) {
    if (store == null) {
      store = new Store(context);
    }
    return store;
  }

  @VisibleForTesting
  static synchronized void clearStoreForTest() {
    store = null;
  }

  /** @hide */
  @Keep
  @NonNull
  static synchronized FirebaseMessaging getInstance(@NonNull FirebaseApp firebaseApp) {
    FirebaseMessaging firebaseMessaging = firebaseApp.get(FirebaseMessaging.class);
    Preconditions.checkNotNull(firebaseMessaging, "Firebase Messaging component is not present");
    return firebaseMessaging;
  }

  FirebaseMessaging(
      FirebaseApp firebaseApp,
      @Nullable FirebaseInstanceIdInternal iid,
      Provider<UserAgentPublisher> userAgentPublisher,
      Provider<HeartBeatInfo> heartBeatInfo,
      FirebaseInstallationsApi firebaseInstallationsApi,
      Provider<TransportFactory> transportFactory,
      Subscriber subscriber) {
    this(
        firebaseApp,
        iid,
        userAgentPublisher,
        heartBeatInfo,
        firebaseInstallationsApi,
        transportFactory,
        subscriber,
        /* metadata= */ new Metadata(firebaseApp.getApplicationContext()));
  }

  FirebaseMessaging(
      FirebaseApp firebaseApp,
      @Nullable FirebaseInstanceIdInternal iid,
      Provider<UserAgentPublisher> userAgentPublisher,
      Provider<HeartBeatInfo> heartBeatInfo,
      FirebaseInstallationsApi firebaseInstallationsApi,
      Provider<TransportFactory> transportFactory,
      Subscriber subscriber,
      Metadata metadata) {
    this(
        firebaseApp,
        iid,
        transportFactory,
        subscriber,
        metadata,
        new GmsRpc(
            firebaseApp, metadata, userAgentPublisher, heartBeatInfo, firebaseInstallationsApi),
        /* taskExecutor= */ newTaskExecutor(),
        /* initExecutor= */ newInitExecutor(),
        /* fileExecutor= */ newFileIOExecutor());
  }

  FirebaseMessaging(
      FirebaseApp firebaseApp,
      @Nullable FirebaseInstanceIdInternal iid,
      Provider<TransportFactory> transportFactory,
      Subscriber subscriber,
      Metadata metadata,
      GmsRpc gmsRpc,
      Executor taskExecutor,
      Executor initExecutor,
      Executor fileExecutor) {

    FirebaseMessaging.transportFactory = transportFactory;

    this.firebaseApp = firebaseApp;
    this.iid = iid;
    autoInit = new AutoInit(subscriber);
    context = firebaseApp.getApplicationContext();
    this.lifecycleCallbacks = new FcmLifecycleCallbacks();
    this.metadata = metadata;
    this.gmsRpc = gmsRpc;
    this.requestDeduplicator = new RequestDeduplicator(taskExecutor);
    this.initExecutor = initExecutor;
    this.fileExecutor = fileExecutor;

    Context appContext = firebaseApp.getApplicationContext();
    if (appContext instanceof Application) {
      Application app = (Application) appContext;
      app.registerActivityLifecycleCallbacks(lifecycleCallbacks);
    } else {
      Log.w(
          TAG,
          "Context "
              + appContext
              + " was not an application, can't register for lifecycle callbacks. Some"
              + " notification events may be dropped as a result.");
    }

    if (iid != null) {
      iid.addNewTokenListener(
          (String token) -> {
            invokeOnTokenRefresh(token);
          });
    }

    initExecutor.execute(
        () -> {
          if (isAutoInitEnabled()) {
            startSyncIfNecessary();
          }
        });

    topicsSubscriberTask =
        TopicsSubscriber.createInstance(
            this, metadata, gmsRpc, context, /* syncExecutor= */ newTopicsSyncExecutor());

    // During FCM instantiation, as part of the initial setup, we spin up a couple of background
    // threads to handle topic syncing and proxy notification configuration.
    topicsSubscriberTask.addOnSuccessListener(
        initExecutor,
        topicsSubscriber -> {
          // Topics operations relay on IID for token generation, thus the sync is also
          // subject to an auto-init check.
          if (isAutoInitEnabled()) {
            topicsSubscriber.startTopicsSyncIfNecessary();
          }
        });

    initExecutor.execute(
        () ->
            // Initializes proxy notification support for the app.
            ProxyNotificationInitializer.initialize(context));
  }

  /**
   * Determines whether FCM auto-initialization is enabled or disabled.
   *
   * @return true if auto-init is enabled and false if auto-init is disabled
   */
  public boolean isAutoInitEnabled() {
    return autoInit.isEnabled();
  }

  /**
   * Enables or disables auto-initialization of Firebase Cloud Messaging.
   *
   * <p>When enabled, Firebase Cloud Messaging generates a registration token on app startup if
   * there is no valid one (see {@link #getToken()}) and periodically sends data to the Firebase
   * backend to validate the token. This setting is persisted across app restarts and overrides the
   * setting specified in your manifest.
   *
   * <p>By default, Firebase Cloud Messaging auto-initialization is enabled. If you need to change
   * the default, (for example, because you want to prompt the user before Firebase Cloud Messaging
   * generates/refreshes a registration token on app startup), add to your application’s manifest:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_messaging_auto_init_enabled" android:value="false" />
   * }</pre>
   *
   * @param enable Whether Firebase Cloud Messaging should auto-initialize.
   */
  public void setAutoInitEnabled(boolean enable) {
    autoInit.setEnabled(enable);
  }

  /**
   * Determines whether Firebase Cloud Messaging exports message delivery metrics to BigQuery.
   *
   * @return true if Firebase Cloud Messaging exports message delivery metrics to BigQuery.
   */
  @NonNull
  public boolean deliveryMetricsExportToBigQueryEnabled() {
    return MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled();
  }

  /**
   * Enables or disables Firebase Cloud Messaging message delivery metrics export to BigQuery.
   *
   * <p>By default, message delivery metrics are not exported to BigQuery. Use this method to enable
   * or disable the export at runtime. In addition, you can enable the export by adding to your
   * manifest. Note that the run-time method call will override the manifest value.
   *
   * <pre>{@code
   * <meta-data android:name= "delivery_metrics_exported_to_big_query_enabled"
   * android:value="true"/>
   * }</pre>
   *
   * @param enable Whether Firebase Cloud Messaging should export message delivery metrics to
   *     BigQuery.
   */
  public void setDeliveryMetricsExportToBigQuery(boolean enable) {
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(enable);
  }

  /**
   * Returns whether notification delegation is enabled or not.
   *
   * @return true if enabled, false otherwise.
   */
  public boolean isNotificationDelegationEnabled() {
    return ProxyNotificationInitializer.isProxyNotificationEnabled(context);
  }

  /**
   * Sets notification delegation to enabled or disabled.
   *
   * <p>By default, notification delegation is enabled. You can also the following manifest entry to
   * disable notification delegation:
   *
   * <pre>{@code
   * <meta-data android:name="firebase_messaging_notification_delegation_enabled"
   * android:value="false"/>
   *
   * }</pre>
   *
   * <p>Setting notification delegation using this method will override any value set in the
   * manifest.
   *
   * <p>Notification delegation is supported from Android Q and above. See {@link
   * android.app.NotificationManager#setNotificationDelegate(String)}
   *
   * @param enable Whether to enable or disable notification delegation.
   * @return A Task that completes when the notification delegation has been set.
   */
  @NonNull
  public Task<Void> setNotificationDelegationEnabled(boolean enable) {
    return ProxyNotificationInitializer.setEnableProxyNotification(initExecutor, context, enable);
  }

  /**
   * Returns the FCM registration token for this Firebase project.
   *
   * <p>This creates a Firebase Installations ID, if one does not exist, and sends information about
   * the application and the device where it's running to the Firebase backend. See {@link
   * #deleteToken} for information on deleting the token and the Firebase Installations ID.
   *
   * @return {@link Task} with the token.
   */
  @NonNull
  public Task<String> getToken() {
    if (iid != null) {
      return iid.getTokenTask();
    }
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    initExecutor.execute(
        () -> {
          try {
            taskCompletionSource.setResult(blockingGetToken());
          } catch (Exception e) {
            taskCompletionSource.setException(e);
          }
        });
    return taskCompletionSource.getTask();
  }

  /**
   * Deletes the FCM registration token for this Firebase project.
   *
   * <p>Note that if auto-init is enabled, a new token will be generated the next time the app is
   * started. Disable auto-init ({@link #setAutoInitEnabled}) to avoid this.
   *
   * <p>Note that this does not delete the Firebase Installations ID that may have been created when
   * generating the token. See {@code FirebaseInstallations.delete()} for deleting that.
   */
  @NonNull
  public Task<Void> deleteToken() {
    if (iid != null) {
      TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
      initExecutor.execute(
          () -> {
            try {
              iid.deleteToken(Metadata.getDefaultSenderId(firebaseApp), INSTANCE_ID_SCOPE);
              taskCompletionSource.setResult(null);
            } catch (Exception e) {
              taskCompletionSource.setException(e);
            }
          });
      return taskCompletionSource.getTask();
    }
    Store.Token token = getTokenWithoutTriggeringSync();
    if (token == null) {
      return Tasks.forResult(null);
    }
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    ExecutorService executorService = FcmExecutors.newNetworkIOExecutor();
    executorService.execute(
        () -> {
          try {
            Tasks.await(gmsRpc.deleteToken());
            getStore(context).deleteToken(getSubtype(), Metadata.getDefaultSenderId(firebaseApp));
            taskCompletionSource.setResult(null);
          } catch (Exception e) {
            taskCompletionSource.setException(e);
          }
        });
    return taskCompletionSource.getTask();
  }

  /**
   * Subscribes to {@code topic} in the background.
   *
   * <p>The subscribe operation is persisted and will be retried until successful.
   *
   * <p>This uses an FCM registration token to identify the app instance, generating one if it does
   * not exist (see {@link #getToken()}), which periodically sends data to the Firebase backend when
   * auto-init is enabled. To delete the data, delete the token ({@link #deleteToken}) and the
   * Firebase Installations ID ({@code FirebaseInstallations.delete()}). To stop the periodic
   * sending of data, disable auto-init ({@link #setAutoInitEnabled}).
   *
   * @param topic The name of the topic to subscribe. Must match the following regular expression:
   *     "[a-zA-Z0-9-_.~%]{1,900}".
   * @return A task that will be completed when the topic has been successfully subscribed to.
   */
  // TODO(b/261013992): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @NonNull
  public Task<Void> subscribeToTopic(@NonNull String topic) {
    return topicsSubscriberTask.onSuccessTask(
        topicsSubscriber -> topicsSubscriber.subscribeToTopic(topic));
  }

  /**
   * Unsubscribes from {@code topic} in the background.
   *
   * <p>The unsubscribe operation is persisted and will be retried until successful.
   *
   * @param topic The name of the topic to unsubscribe from. Must match the following regular
   *     expression: "[a-zA-Z0-9-_.~%]{1,900}".
   * @return A task that will be completed when the topic has been successfully unsubscribed from.
   */
  // TODO(b/261013992): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @NonNull
  public Task<Void> unsubscribeFromTopic(@NonNull String topic) {
    return topicsSubscriberTask.onSuccessTask(
        topicsSubscriber -> topicsSubscriber.unsubscribeFromTopic(topic));
  }

  /**
   * Sends {@code message} upstream to your app server.
   *
   * <p>When there is an active connection the message will be sent immediately, otherwise the
   * message will be queued up to the time to live (TTL) set in the message.
   *
   * @deprecated FCM upstream messaging is deprecated and will be decommissioned in June 2024. Learn
   *     more in the <a href="https://firebase.google.com/support/faq#fcm-23-deprecation">FAQ about
   *     FCM features deprecated in June 2023</a>.
   */
  @Deprecated
  public void send(@NonNull RemoteMessage message) {
    if (TextUtils.isEmpty(message.getTo())) {
      throw new IllegalArgumentException("Missing 'to'");
    }

    Intent intent = new Intent(SEND_INTENT_ACTION);

    // dummy pending-intent for package-name verification
    // (fill in the package, to prevent the intent from being used)
    Intent dummyIntent = new Intent();
    dummyIntent.setPackage("com.google.example.invalidpackage");
    intent.putExtra(
        EXTRA_DUMMY_P_INTENT,
        PendingIntent.getBroadcast(
            context,
            0,
            dummyIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

    intent.setPackage(GMS_PACKAGE);
    message.populateSendMessageIntent(intent);

    // Signature permission required.
    context.sendOrderedBroadcast(
        intent, "com.google.android.gtalkservice.permission.GTALK_SERVICE");
  }

  /** @hide */
  Task<TopicsSubscriber> getTopicsSubscriberTask() {
    return topicsSubscriberTask;
  }

  /** @hide */
  @Nullable
  public static TransportFactory getTransportFactory() {
    return transportFactory.get();
  }

  /** @hide */
  static void clearTransportFactoryForTest() {
    transportFactory = () -> null;
  }

  /** Checks if Gmscore is present. */
  @VisibleForTesting
  boolean isGmsCorePresent() {
    return metadata.isGmscorePresent();
  }

  Context getApplicationContext() {
    return context;
  }

  synchronized void setSyncScheduledOrRunning(boolean value) {
    syncScheduledOrRunning = value;
  }

  synchronized void syncWithDelaySecondsInternal(long delaySeconds) {
    // retryDelaySeconds is the backoff time to use if the task fails
    long retryDelaySeconds = Math.min(Math.max(MIN_DELAY_SEC, delaySeconds * 2), MAX_DELAY_SEC);
    SyncTask syncTask = new SyncTask(this, retryDelaySeconds);
    enqueueTaskWithDelaySeconds(syncTask, delaySeconds);
    syncScheduledOrRunning = true;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  // TODO(b/258424124): Migrate to go/firebase-android-executors
  @SuppressLint("ThreadPoolCreation")
  void enqueueTaskWithDelaySeconds(Runnable task, long delaySeconds) {
    synchronized (FirebaseMessaging.class) {
      if (syncExecutor == null) {
        syncExecutor = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("TAG"));
      }
      syncExecutor.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }
  }

  private void startSyncIfNecessary() {
    if (iid != null) {
      // This calls FirebaseInstanceId.startSync() if necessary, ignore the result since it isn't
      // needed here.
      iid.getToken();
      return;
    }
    Store.Token token = getTokenWithoutTriggeringSync();
    // Start a sync if we don't have a token, the token needs refresh, or there is a pending topic
    // operation
    if (tokenNeedsRefresh(token)) {
      startSync();
    }
  }

  private synchronized void startSync() {
    if (!syncScheduledOrRunning) {
      syncWithDelaySecondsInternal(0 /* start sync task now */);
    }
  }

  /** Get the token from the cache without triggering a sync if it's not present. */
  @Nullable
  @VisibleForTesting
  Store.Token getTokenWithoutTriggeringSync() {
    return getStore(context).getToken(getSubtype(), Metadata.getDefaultSenderId(firebaseApp));
  }

  /**
   * Returns the cached token, if valid. Otherwise makes a request to the server to get a new token.
   */
  String blockingGetToken() throws IOException {
    if (iid != null) {
      try {
        return Tasks.await(iid.getTokenTask());
      } catch (ExecutionException | InterruptedException e) {
        throw new IOException(e);
      }
    }
    Store.Token cachedToken = getTokenWithoutTriggeringSync();
    if (!tokenNeedsRefresh(cachedToken)) {
      return cachedToken.token;
    }

    String senderId = Metadata.getDefaultSenderId(firebaseApp);
    Task<String> tokenTask =
        requestDeduplicator.getOrStartGetTokenRequest(
            senderId,
            () ->
                gmsRpc
                    .getToken()
                    .onSuccessTask(
                        fileExecutor,
                        token -> {
                          getStore(context)
                              .saveToken(
                                  getSubtype(), senderId, token, metadata.getAppVersionCode());
                          if (cachedToken == null || !token.equals(cachedToken.token)) {
                            invokeOnTokenRefresh(token);
                          }
                          return Tasks.forResult(token);
                        }));
    try {
      return Tasks.await(tokenTask);
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  private String getSubtype() {
    // Use SUBTYPE_DEFAULT for the default app to maintain backwards compatibility for when the
    // token for all FirebaseApps was stored under SUBTYPE_DEFAULT.
    return FirebaseApp.DEFAULT_APP_NAME.equals(firebaseApp.getName())
        ? SUBTYPE_DEFAULT
        : firebaseApp.getPersistenceKey();
  }

  @VisibleForTesting
  boolean tokenNeedsRefresh(@Nullable Store.Token token) {
    return token == null || token.needsRefresh(metadata.getAppVersionCode());
  }

  private void invokeOnTokenRefresh(String token) {
    // onNewToken() is only invoked for the default app as there is no parameter to identify which
    // app the token is for. We could add a new method onNewToken(FirebaseApp app, String token) or
    // the like to handle multiple apps better.
    if (FirebaseApp.DEFAULT_APP_NAME.equals(firebaseApp.getName())) {
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "Invoking onNewToken for app: " + firebaseApp.getName());
      }
      Intent messagingIntent = new Intent(FirebaseMessagingService.ACTION_NEW_TOKEN);
      messagingIntent.putExtra(FirebaseMessagingService.EXTRA_TOKEN, token);
      // Previously this sent to the FIIDReceiver, which forwarded to the service.
      // Send directly to service using the old FIIDReceiver mechanism to keep the change simple.
      new FcmBroadcastProcessor(context).process(messagingIntent);
    }
  }

  private class AutoInit {

    private static final String MANIFEST_METADATA_AUTO_INIT_ENABLED =
        "firebase_messaging_auto_init_enabled";

    private static final String FCM_PREFERENCES = "com.google.firebase.messaging";
    private static final String AUTO_INIT_PREF = "auto_init";

    private final Subscriber subscriber;

    @GuardedBy("this")
    private boolean initialized;

    @GuardedBy("this")
    @Nullable
    private EventHandler<DataCollectionDefaultChange> dataCollectionDefaultChangeEventHandler;

    @GuardedBy("this")
    @Nullable
    private Boolean autoInitEnabled;

    AutoInit(Subscriber subscriber) {
      this.subscriber = subscriber;
    }

    synchronized void initialize() {
      if (initialized) {
        return;
      }
      autoInitEnabled = readEnabled();
      if (autoInitEnabled == null) {
        // FCM auto-init not set specifically but FCM library is included. Will use the value from
        // the Firebase-wide flag. Subscribe for changes to that flag so that we can start syncing
        // if it's enabled.
        dataCollectionDefaultChangeEventHandler =
            event -> {
              if (isEnabled()) {
                startSyncIfNecessary();
              }
            };
        subscriber.subscribe(
            DataCollectionDefaultChange.class, dataCollectionDefaultChangeEventHandler);
      }
      initialized = true;
    }

    synchronized boolean isEnabled() {
      initialize();
      return autoInitEnabled != null
          ? autoInitEnabled
          : firebaseApp.isDataCollectionDefaultEnabled();
    }

    synchronized void setEnabled(boolean enable) {
      initialize();
      if (dataCollectionDefaultChangeEventHandler != null) {
        subscriber.unsubscribe(
            DataCollectionDefaultChange.class, dataCollectionDefaultChangeEventHandler);
        dataCollectionDefaultChangeEventHandler = null;
      }
      SharedPreferences.Editor preferencesEditor =
          firebaseApp
              .getApplicationContext()
              .getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE)
              .edit();
      preferencesEditor.putBoolean(AUTO_INIT_PREF, enable);
      preferencesEditor.apply();
      if (enable) {
        startSyncIfNecessary();
      }
      autoInitEnabled = enable;
    }

    @Nullable
    private Boolean readEnabled() {
      Context applicationContext = firebaseApp.getApplicationContext();
      SharedPreferences preferences =
          applicationContext.getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE);

      // Value set at runtime overrides anything else.
      if (preferences.contains(AUTO_INIT_PREF)) {
        return preferences.getBoolean(AUTO_INIT_PREF, false);
      }

      // Check if there's metadata in the manifest setting the auto-init state.
      try {
        PackageManager packageManager = applicationContext.getPackageManager();
        if (packageManager != null) {
          ApplicationInfo applicationInfo =
              packageManager.getApplicationInfo(
                  applicationContext.getPackageName(), PackageManager.GET_META_DATA);
          if (applicationInfo != null
              && applicationInfo.metaData != null
              && applicationInfo.metaData.containsKey(MANIFEST_METADATA_AUTO_INIT_ENABLED)) {
            return applicationInfo.metaData.getBoolean(MANIFEST_METADATA_AUTO_INIT_ENABLED);
          }
        }
      } catch (PackageManager.NameNotFoundException e) {
        // This shouldn't happen since it's this app's package, but fall through to default if so.
      }

      // No auto-init value set specifically for FCM.
      return null;
    }
  }
}
