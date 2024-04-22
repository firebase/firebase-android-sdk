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

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.AnyThread;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.cloudmessaging.CloudMessage;
import com.google.android.gms.cloudmessaging.Rpc;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.heartbeatinfo.HeartBeatInfo.HeartBeat;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

/** Rpc based on Google Play Service. */
class GmsRpc {
  static final String TAG = FirebaseMessaging.TAG;

  /** Normal response from GMS */
  private static final String EXTRA_REGISTRATION_ID = "registration_id";

  /** Extra used to indicate that the application has been unregistered. */
  private static final String EXTRA_UNREGISTERED = "unregistered";

  /** Returned by GMS in case of error. */
  private static final String EXTRA_ERROR = "error";

  /**
   * The device cannot read the response, or there was a server error. Application should retry the
   * request later using exponential backoff and retry (on each subsequent failure increase delay
   * before retrying).
   */
  static final String ERROR_SERVICE_NOT_AVAILABLE = "SERVICE_NOT_AVAILABLE";

  /** Another server error besides ERROR_SERVICE_NOT_AVAILABLE that we retry on. */
  static final String ERROR_INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

  /** Heartbeat tag for firebase iid. */
  static final String FIREBASE_IID_HEARTBEAT_TAG = "fire-iid";

  /**
   * Some parts of our backends respond with the camelCase version of this error, so for safety we
   * check for both.
   */
  // TODO(b/147609748): remove this when we stop returning PascalCase errors
  static final String ERROR_INTERNAL_SERVER_ERROR_ALT = "InternalServerError";

  private static final String EXTRA_TOPIC = "gcm.topic";
  private static final String TOPIC_PREFIX = "/topics/";

  // LINT.IfChange
  /** InstanceId should be reset. Can be a duplicate, or deleted. */
  static final String ERROR_INSTANCE_ID_RESET = "INSTANCE_ID_RESET";
  // LINT.ThenChange(//depot/google3/firebase/instance_id/client/cpp/src/android/instance_id.cc)

  // --- List of parameters sent to the /register3 servlet

  /** Internal parameter used to indicate a 'subtype'. Will not be stored in DB for Nacho. */
  private static final String EXTRA_SUBTYPE = "subtype";
  /** Extra used to indicate which senders (Google API project IDs) can send messages to the app */
  private static final String EXTRA_SENDER = "sender";

  private static final String EXTRA_SCOPE = "scope";

  /** Extra sent to http endpoint to indicate delete request */
  private static final String EXTRA_DELETE = "delete";

  /** Currently we only support the (gdpr) 'delete' operation */
  private static final String EXTRA_IID_OPERATION = "iid-operation";

  /** key id - sha of public key truncated to 8 bytes, with 0x9 prefix */
  private static final String PARAM_INSTANCE_ID = "appid";

  /** key id - user agent string published by firebase-common */
  private static final String PARAM_USER_AGENT = "Firebase-Client";

  /** key id - heartbeat code published by firebase-common */
  private static final String PARAM_HEARTBEAT_CODE = "Firebase-Client-Log-Type";

  /** Version of the client library. String like: "fcm-112233" */
  private static final String PARAM_CLIENT_VER = "cliv";
  /** gmp_app_id (application identifier in firebase). String */
  private static final String PARAM_GMP_APP_ID = "gmp_app_id";
  /** version of the gms package. Integer.toString() */
  private static final String PARAM_GMS_VER = "gmsv";
  /** android build version. Integer.toString() */
  private static final String PARAM_OS_VER = "osv";
  /** package version code. Integer.toString() */
  private static final String PARAM_APP_VER_CODE = "app_ver";
  /** package version name. Integer.toString() */
  private static final String PARAM_APP_VER_NAME = "app_ver_name";

  private static final String PARAM_FIS_AUTH_TOKEN = "Goog-Firebase-Installations-Auth";
  /** hashed value of developer chosen (nick)name of Firebase Core SDK (a.k.a. FirebaseApp) */
  private static final String PARAM_FIREBASE_APP_NAME_HASH = "firebase-app-name-hash";

  // --- End of the params for /register3

  /**
   * Value included in a GCM message from IID, indicating a full identity reset. This means a reset
   * of all subtypes (different IIDs requested by the same app). This is typically sent after an app
   * is restored.
   */
  static final String CMD_RST_FULL = "RST_FULL";

  /** Value included in a GCM message from IID, indicating an identity reset. */
  static final String CMD_RST = "RST";

  /** Value included in a GCM message from IID, indicating a token sync reset. */
  static final String CMD_SYNC = "SYNC";

  private static final String SCOPE_ALL = "*";

  private final FirebaseApp app;
  private final Metadata metadata;

  private final Rpc rpc;

  private final Provider<UserAgentPublisher> userAgentPublisher;

  private final Provider<HeartBeatInfo> heartbeatInfo;

  private final FirebaseInstallationsApi firebaseInstallations;

  GmsRpc(
      FirebaseApp app,
      Metadata metadata,
      Provider<UserAgentPublisher> userAgentPublisher,
      Provider<HeartBeatInfo> heartbeatInfo,
      FirebaseInstallationsApi firebaseInstallations) {
    this(
        app,
        metadata,
        new Rpc(app.getApplicationContext()),
        userAgentPublisher,
        heartbeatInfo,
        firebaseInstallations);
  }

  @VisibleForTesting
  GmsRpc(
      FirebaseApp app,
      Metadata metadata,
      Rpc rpc,
      Provider<UserAgentPublisher> userAgentPublisher,
      Provider<HeartBeatInfo> heartbeatInfo,
      FirebaseInstallationsApi firebaseInstallations) {
    this.app = app;
    this.metadata = metadata;
    this.rpc = rpc;
    this.userAgentPublisher = userAgentPublisher;
    this.heartbeatInfo = heartbeatInfo;
    this.firebaseInstallations = firebaseInstallations;
  }

  Task<String> getToken() {
    Task<Bundle> rpcTask =
        startRpc(Metadata.getDefaultSenderId(app), SCOPE_ALL, /* extras= */ new Bundle());
    return extractResponseWhenComplete(rpcTask);
  }

  Task<?> deleteToken() {
    Bundle extras = new Bundle();
    // Server looks at both delete and X-delete so don't need to include both
    extras.putString(EXTRA_DELETE, "1");

    Task<Bundle> rpcTask = startRpc(Metadata.getDefaultSenderId(app), SCOPE_ALL, extras);
    return extractResponseWhenComplete(rpcTask);
  }

  Task<?> subscribeToTopic(String cachedToken, String topic) {
    Bundle extras = new Bundle();
    // registration servlet expects this for topics
    extras.putString(EXTRA_TOPIC, TOPIC_PREFIX + topic);
    // Sends the request to registration servlet and throws on failure.
    // We do not cache the topic subscription requests and simply make the
    // server request each time.

    String to = cachedToken;
    String scope = TOPIC_PREFIX + topic;
    Task<Bundle> rpcTask = startRpc(to, scope, extras);
    return extractResponseWhenComplete(rpcTask);
  }

  Task<?> unsubscribeFromTopic(String cachedToken, String topic) {
    Bundle extras = new Bundle();
    // registration servlet expects this for topics
    extras.putString(EXTRA_TOPIC, TOPIC_PREFIX + topic);
    extras.putString(EXTRA_DELETE, "1");

    String to = cachedToken;
    String scope = TOPIC_PREFIX + topic;

    Task<Bundle> rpcTask = startRpc(to, scope, extras);
    return extractResponseWhenComplete(rpcTask);
  }

  Task<Void> setRetainProxiedNotifications(boolean retain) {
    return rpc.setRetainProxiedNotifications(retain);
  }

  Task<CloudMessage> getProxyNotificationData() {
    return rpc.getProxiedNotificationData();
  }

  private Task<Bundle> startRpc(String to, String scope, Bundle extras) {
    try {
      setDefaultAttributesToBundle(to, scope, extras);
    } catch (InterruptedException | ExecutionException e) {
      return Tasks.forException(e);
    }

    return rpc.send(extras);
  }

  private static String base64UrlSafe(byte[] data) {
    return Base64.encodeToString(data, Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
  }

  private String getHashedFirebaseAppName() {
    String firebaseAppName = app.getName();
    String hashAlgo = "SHA-1";
    try {
      return base64UrlSafe(MessageDigest.getInstance(hashAlgo).digest(firebaseAppName.getBytes()));
    } catch (NoSuchAlgorithmException e) {
      return "[HASH-ERROR]";
    }
  }

  private void setDefaultAttributesToBundle(String to, String scope, Bundle extras)
      throws ExecutionException, InterruptedException { // Thrown by Tasks.await() on errors.
    extras.putString(EXTRA_SCOPE, scope);
    extras.putString(EXTRA_SENDER, to);
    // TODO(diorgini): old logic sets extra-subtype=to if subtype="". check if we can remove this
    extras.putString(EXTRA_SUBTYPE, to);

    // Populate metadata
    extras.putString(PARAM_GMP_APP_ID, app.getOptions().getApplicationId());
    extras.putString(PARAM_GMS_VER, Integer.toString(metadata.getGmsVersionCode()));
    extras.putString(PARAM_OS_VER, Integer.toString(Build.VERSION.SDK_INT));
    extras.putString(PARAM_APP_VER_CODE, metadata.getAppVersionCode());
    extras.putString(PARAM_APP_VER_NAME, metadata.getAppVersionName());
    extras.putString(PARAM_FIREBASE_APP_NAME_HASH, getHashedFirebaseAppName());

    try {
      String fisAuthToken = Tasks.await(firebaseInstallations.getToken(false)).getToken();
      if (!TextUtils.isEmpty(fisAuthToken)) {
        extras.putString(PARAM_FIS_AUTH_TOKEN, fisAuthToken);
      } else {
        Log.w(TAG, "FIS auth token is empty");
      }
    } catch (ExecutionException | InterruptedException e) {
      Log.e(TAG, "Failed to get FIS auth token", e);
    }
    // Call this after getting the FIS auth token to ensure that the ID goes with the auth token
    // (b/178162926).
    extras.putString(PARAM_INSTANCE_ID, Tasks.await(firebaseInstallations.getId()));

    String version = BuildConfig.VERSION_NAME;
    extras.putString(PARAM_CLIENT_VER, "fcm-" + version);
    HeartBeatInfo heartbeatInfoObject = heartbeatInfo.get();
    UserAgentPublisher userAgentPublisherObject = userAgentPublisher.get();
    if (heartbeatInfoObject != null && userAgentPublisherObject != null) {
      HeartBeat heartbeat = heartbeatInfoObject.getHeartBeatCode(FIREBASE_IID_HEARTBEAT_TAG);
      if (heartbeat != HeartBeat.NONE) {
        extras.putString(PARAM_HEARTBEAT_CODE, Integer.toString(heartbeat.getCode()));
        extras.putString(PARAM_USER_AGENT, userAgentPublisherObject.getUserAgent());
      }
    }
  }

  @AnyThread
  private String handleResponse(Bundle response) throws IOException {
    if (response == null) {
      // We didn't get a response at all
      throw new IOException(GmsRpc.ERROR_SERVICE_NOT_AVAILABLE);
    }

    String token = response.getString(EXTRA_REGISTRATION_ID);
    if (token != null) {
      return token;
    }
    // Successful DeleteToken requests return the app's package name (gets ignored by the caller)
    String unregisteredPackage = response.getString(EXTRA_UNREGISTERED);
    if (unregisteredPackage != null) {
      return unregisteredPackage;
    }

    String error = response.getString(EXTRA_ERROR);
    if (CMD_RST.equals(error)) {
      // Magic value that indicates the InstanceId used is invalid (perhaps a duplicate, or
      // wiped out due to GDPR) and client should generate a new one.
      throw new IOException(ERROR_INSTANCE_ID_RESET);
    } else if (error != null) {
      throw new IOException(error);
    }

    // We didn't get a valid response
    Log.w(TAG, "Unexpected response: " + response, new Throwable());
    throw new IOException(GmsRpc.ERROR_SERVICE_NOT_AVAILABLE);
  }

  private Task<String> extractResponseWhenComplete(Task<Bundle> rpcTask) {
    // direct executor is safe and more efficient here as handle response is always quick
    return rpcTask.continueWith(
        Runnable::run, task -> handleResponse(task.getResult(IOException.class)));
  }

  static boolean isErrorMessageForRetryableError(String errorMessage) {
    return ERROR_SERVICE_NOT_AVAILABLE.equals(errorMessage)
        || ERROR_INTERNAL_SERVER_ERROR.equals(errorMessage)
        || ERROR_INTERNAL_SERVER_ERROR_ALT.equals(errorMessage);
  }
}
