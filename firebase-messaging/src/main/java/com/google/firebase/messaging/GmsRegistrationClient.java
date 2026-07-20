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

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.cloudmessaging.CloudMessaging;
import com.google.android.gms.cloudmessaging.CloudMessagingClient;
import com.google.android.gms.cloudmessaging.RegisterRequest;
import com.google.android.gms.cloudmessaging.UnregisterRequest;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/** A client for the CloudMessaging API to make FCM registration calls. */
class GmsRegistrationClient {
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
    this(app, firebaseInstallations, gmsRpc, CloudMessaging.getClient(context), metadata);
  }

  @VisibleForTesting
  GmsRegistrationClient(
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
    ExecutorService executorService = FcmExecutors.newNetworkIOExecutor();
    return firebaseInstallations
        .getId()
        .continueWithTask(
            executorService,
            idTask -> {
              if (!idTask.isSuccessful()) {
                return Tasks.forException(getException(idTask));
              }
              String installationId = idTask.getResult();
              return registerOverV1Async(installationId)
                  .continueWith(
                      executorService,
                      registerTask -> {
                        if (!registerTask.isSuccessful()) {
                          throw new ExecutionException(registerTask.getException());
                        }
                        String registrationName = registerTask.getResult();
                        // For V1 registration, the token received should be the same as the FID.
                        if (!TextUtils.isEmpty(registrationName)
                            && registrationName.endsWith(installationId)) {
                          // The registration token will be in format projects/**/$fid. But the
                          // actual token
                          // for sending messages to will be the FID. So returning the FID.
                          return installationId;
                        } else {
                          throw new ExecutionException(
                              new IllegalArgumentException("Unexpected Error: FID NOT matching!"));
                        }
                      });
            });
  }

  private Exception getException(Task<?> task) {
    return task.getException() != null
        ? task.getException()
        : new ExecutionException(new RuntimeException("Unexpected Error"));
  }

  @NonNull
  private Task<String> registerOverV1Async(String installationId) {
    return firebaseInstallations
        .getToken(false)
        .continueWithTask(
            FcmExecutors.newNetworkIOExecutor(),
            tokenTask -> {
              if (!tokenTask.isSuccessful()) {
                return Tasks.forException(getException(tokenTask));
              }
              String installationAuthToken = tokenTask.getResult().getToken();
              String apiKey = app.getOptions().getApiKey();
              String gmpAppId = app.getOptions().getApplicationId();
              String senderId = Metadata.getDefaultSenderId(app);
              String sdkVersion = BuildConfig.VERSION_NAME;

              RegisterRequest request =
                  new RegisterRequest(
                      senderId,
                      gmpAppId,
                      apiKey,
                      installationId,
                      installationAuthToken,
                      sdkVersion);
              return client.register(request);
            });
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
    ExecutorService executorService = FcmExecutors.newNetworkIOExecutor();
    return unregisterOverV1Async()
        .continueWithTask(
            executorService,
            task -> {
              if (!task.isSuccessful()) {
                return Tasks.forException(getException(task));
              }
              return Tasks.forResult(null);
            });
  }

  private Task<Void> unregisterOverV1Async() {
    ExecutorService executorService = FcmExecutors.newNetworkIOExecutor();
    return firebaseInstallations
        .getId()
        .continueWithTask(
            executorService,
            idTask -> {
              if (!idTask.isSuccessful()) {
                return Tasks.forException(getException(idTask));
              }
              String installationId = idTask.getResult();
              return firebaseInstallations
                  .getToken(false)
                  .continueWithTask(
                      executorService,
                      tokenTask -> {
                        if (!tokenTask.isSuccessful()) {
                          return Tasks.forException(getException(tokenTask));
                        }
                        String installationAuthToken = tokenTask.getResult().getToken();
                        String apiKey = app.getOptions().getApiKey();
                        String projectId = Metadata.getDefaultSenderId(app);

                        UnregisterRequest request =
                            new UnregisterRequest(
                                projectId, apiKey, installationId, installationAuthToken);
                        return client.unregister(request);
                      });
            });
  }
}
