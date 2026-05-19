package com.google.firebase.messaging;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.gms.cloudmessaging.CloudMessaging;
import com.google.android.gms.cloudmessaging.CloudMessagingClient;
import com.google.android.gms.cloudmessaging.RegisterRequest;
import com.google.android.gms.cloudmessaging.UnregisterRequest;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** A client for the CloudMessaging API to make FCM registration calls. */
public class GmsRegistrationClient {
  static final String MANIFEST_METADATA_FIREBASE_MESSAGING_INSTALLATION_ID_ENABLED =
      "firebase_messaging_installation_id_enabled";
  private static final int GMS_VERSION_Y2026W12 = 261200000;
  private final CloudMessagingClient client;
  private final FirebaseApp app;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final GmsRpc gmsRpc;
  private final Metadata metadata;

  GmsRegistrationClient(
      @NonNull Context context,
      @NonNull FirebaseApp app,
      @NonNull FirebaseInstallationsApi firebaseInstallations,
      @NonNull GmsRpc gmsRpc,
      @NonNull Metadata metadata) {
    this(context, app, firebaseInstallations, gmsRpc, CloudMessaging.getClient(context), metadata);
  }

  @VisibleForTesting
  GmsRegistrationClient(
      @NonNull Context context,
      @NonNull FirebaseApp app,
      @NonNull FirebaseInstallationsApi firebaseInstallations,
      @NonNull GmsRpc gmsRpc,
      @NonNull CloudMessagingClient client,
      @NonNull Metadata metadata) {
    this.client = client;
    this.app = app;
    this.firebaseInstallations = firebaseInstallations;
    this.gmsRpc = gmsRpc;
    this.metadata = metadata;
  }

  /** Checks whether the installed gmscore supports v1 registration. */
  private boolean haveV1RegistrationSupport() {
    return metadata.getGmsVersionCode() >= GMS_VERSION_Y2026W12;
  }

  /** Reads the Manifest metadata to check whether FCM V1 registration is enabled or not. */
  public boolean isV1RegistrationEnabled() {
    Context applicationContext = app.getApplicationContext();
    try {
      PackageManager packageManager = applicationContext.getPackageManager();
      if (packageManager != null) {
        ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                applicationContext.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(
                MANIFEST_METADATA_FIREBASE_MESSAGING_INSTALLATION_ID_ENABLED)) {
          return applicationInfo.metaData.getBoolean(
              MANIFEST_METADATA_FIREBASE_MESSAGING_INSTALLATION_ID_ENABLED);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // This shouldn't happen since it's this app's package, but fall through to default if so.
    }

    return false;
  }

  /**
   * Registers this app to receive push messages.
   *
   * @return The registration token for sending messages to this app instance.
   */
  @NonNull
  public Task<String> register() {
    boolean useV1 = isV1RegistrationEnabled();
    if (!useV1 || !haveV1RegistrationSupport()) {
      // Legacy registration flow.
      return gmsRpc.getToken(useV1);
    }

    // Proceeding with V1 registration.
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    ExecutorService executorService = FcmExecutors.newNetworkIOExecutor();
    executorService.execute(
        () -> {
          try {
            String installationId = Tasks.await(firebaseInstallations.getId());
            String registrationToken = Tasks.await(registerOverV1(installationId));

            // For V1 registration, the token received should be the same as the FID.
            if (!TextUtils.isEmpty(registrationToken)
                && registrationToken.endsWith(installationId)) {
              // The registration token will be in format projects/**/$fid. But the actual token
              // for sending messages to will be the FID. So returning the FID.
              taskCompletionSource.setResult(installationId);
            } else {
              taskCompletionSource.setException(
                  new ExecutionException(
                      new IllegalArgumentException("Unexpected Error: FID NOT matching!")));
            }
          } catch (ExecutionException | InterruptedException e) {
            taskCompletionSource.setException(e);
          }
        });

    return taskCompletionSource.getTask();
  }

  @NonNull
  private Task<String> registerOverV1(String installationId)
      throws ExecutionException, InterruptedException {
    String installationAuthToken = Tasks.await(firebaseInstallations.getToken(false)).getToken();
    String apiKey = app.getOptions().getApiKey();
    String gmpAppId = app.getOptions().getApplicationId();
    String senderId = Metadata.getDefaultSenderId(app);
    String sdkVersion = BuildConfig.VERSION_NAME;

    RegisterRequest request =
        new RegisterRequest(
            senderId, gmpAppId, apiKey, installationId, installationAuthToken, sdkVersion);
    return client.register(request);
  }

  /** Unregisters this app from receiving push messages. */
  @NonNull
  public Task<?> unregister() {
    boolean useV1 = isV1RegistrationEnabled();

    if (!useV1 || !haveV1RegistrationSupport()) {
      // Legacy un-registration flow.
      return gmsRpc.deleteToken(useV1);
    }

    // Proceeding with V1 un-registration.
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    ExecutorService executorService = FcmExecutors.newNetworkIOExecutor();
    executorService.execute(
        () -> {
          try {
            Tasks.await(unregisterOverV1());
            taskCompletionSource.setResult(null);
          } catch (ExecutionException | InterruptedException e) {
            taskCompletionSource.setException(e);
          }
        });

    return taskCompletionSource.getTask();
  }

  @NonNull
  @WorkerThread
  private Task<Void> unregisterOverV1() throws ExecutionException, InterruptedException {
    String installationId = Tasks.await(firebaseInstallations.getId());
    String installationAuthToken = Tasks.await(firebaseInstallations.getToken(false)).getToken();
    String apiKey = app.getOptions().getApiKey();
    String projectId = Metadata.getDefaultSenderId(app);

    UnregisterRequest request =
        new UnregisterRequest(projectId, apiKey, installationId, installationAuthToken);
    return client.unregister(request);
  }
}
