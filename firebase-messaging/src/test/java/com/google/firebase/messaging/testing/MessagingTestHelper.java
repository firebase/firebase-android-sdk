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
package com.google.firebase.messaging.testing;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.RemoteMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities used in Firebase Messaging tests. */
public class MessagingTestHelper {

  public static final String TAG = "MessagingTestHelper";

  private static final String GMS_PACKAGE_NAME = "com.google.android.gms";

  // Sender and key from project: FirebaseMessagingApiTests, gcm-eng@ has access
  public static final String GOOGLE_APP_ID = "1:635258614906:android:71ccab5d92c5d7c4";
  public static final String SENDER = "635258614906";
  public static final String KEY = "AIzaSyDSb51JiIcB6OJpwwMicseKRhhrOq1cS7g";
  public static final String PROJECT_ID = "ghmessagingapitests-dab42";

  public static final String HTTP_SERVER_PROD = "https://fcm.googleapis.com/fcm/send";
  public static final String HTTP_SERVER_STAGING = "https://jmt17.google.com/fcm/send";

  private static final int TIMEOUT = 10_000; // Server should never take longer than 10s
  private static final int NUM_NETWORK_RETRIES = 3;

  /** Message payload parameter for the unique ID. */
  public static final String KEY_UNIQUE_ID = "unique_id";

  public static void initializeFirebaseAndKickGcm(Context context) {
    kickGcm(context);

    FirebaseApp.clearInstancesForTest();
    // Should be able to use initializeDefaultApp to initialize from string resources, but
    // I couldn't get the resources to build properly into the API tests so create a
    // FirebaseOptions instance instead.
    FirebaseOptions options =
        new FirebaseOptions.Builder()
            .setApplicationId(MessagingTestHelper.GOOGLE_APP_ID)
            .setGcmSenderId(SENDER)
            .setApiKey(KEY)
            .setProjectId(PROJECT_ID)
            .build();
    FirebaseApp.initializeApp(context, options);
  }

  public static void kickGcm(Context context) {
    // Kick GCM in cases it's hung or disconnected
    Intent heartbeat = new Intent("com.google.android.gms.gcm.ACTION_HEARTBEAT_NOW");
    heartbeat.setPackage(GMS_PACKAGE_NAME);
    context.sendBroadcast(heartbeat);

    Intent reconnect = new Intent("com.google.android.intent.action.GCM_RECONNECT");
    reconnect.setPackage(GMS_PACKAGE_NAME);
    context.sendBroadcast(reconnect);
  }

  public static void clearIidState(Context context) {
    // Note that this doesn't delete the properties files so test runs will have the same IID
    context
        .getSharedPreferences("com.google.android.gms.appid", Context.MODE_PRIVATE)
        .edit()
        .clear()
        .commit();
  }

  public static String getTokenBlocking() throws ExecutionException, InterruptedException {
    int retry = 0;
    while (true) {
      try {
        return Tasks.await(FirebaseInstanceId.getInstance().getInstanceId()).getToken();
      } catch (ExecutionException e) {
        // Retry in case there may have been a server error, it may be transient.
        if (++retry > NUM_NETWORK_RETRIES) {
          throw e;
        }
        Log.e(TAG, "getInstanceId failed, retry " + retry, e);
        // Wait for minimum blackout period (2 + 1 seconds).
        // See {@link PushMessagingRegistrar#setBlackoutPeriod}.
        SystemClock.sleep(TimeUnit.SECONDS.toMillis(3));
      }
    }
  }

  public static String sendMessage(
      String server, Map<String, String> params, Map<String, String> data) throws IOException {
    int retry = 0;
    while (true) {
      try {
        return sendMessageWithoutRetry(server, params, data);
      } catch (IOException ioException) {
        // Retry if there was a server error, it may be transient.
        if (++retry > NUM_NETWORK_RETRIES) {
          throw ioException;
        }
        Log.e(TAG, "sendMessage failed, retry " + retry, ioException);
      }
    }
  }

  @SuppressWarnings("UrlConnectionChecker")
  public static String sendMessageWithoutRetry(
      String server, Map<String, String> params, Map<String, String> data) throws IOException {
    URL url = new URL(server);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setDoOutput(true);
    con.setConnectTimeout(TIMEOUT);
    con.setReadTimeout(TIMEOUT);
    con.setRequestProperty("Authorization", "key=" + KEY);

    StringBuilder body = new StringBuilder();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      if (body.length() > 0) {
        body.append('&');
      }
      body.append(entry.getKey()).append('=').append(entry.getValue());
    }
    if (data != null) {
      for (Map.Entry<String, String> entry : data.entrySet()) {
        body.append("&data.").append(entry.getKey()).append('=').append(entry.getValue());
      }
    }
    con.getOutputStream().write(body.toString().getBytes());
    return getResponse(con);
  }

  public static JSONObject sendJSONMessage(
      final String server, final String to, Map<String, Object> params, Map<String, String> data)
      throws IOException, JSONException {
    int retry = 0;
    while (true) {
      try {
        return sendJSONMessageWithoutRetry(server, to, params, data);
      } catch (IOException ioException) {
        // Retry if there was a server error, it may be transient.
        if (++retry > NUM_NETWORK_RETRIES) {
          throw ioException;
        }
        Log.e(TAG, "sendJSONMessage failed, retry " + retry, ioException);
      }
    }
  }

  @SuppressWarnings("UrlConnectionChecker")
  public static JSONObject sendJSONMessageWithoutRetry(
      final String server, final String to, Map<String, Object> params, Map<String, String> data)
      throws IOException, JSONException {

    JSONObject json = (params != null) ? new JSONObject(params) : new JSONObject();
    json.put("to", to);
    if (data != null) {
      json.put("data", new JSONObject(data));
    }
    Log.d(TAG, String.format("Making json request to %s: %s", server, json.toString(2)));
    long startTime = System.nanoTime();

    URL url = new URL(server);
    HttpURLConnection con = (HttpURLConnection) url.openConnection();
    con.setDoOutput(true);
    con.setConnectTimeout(TIMEOUT);
    con.setReadTimeout(TIMEOUT);
    con.setRequestProperty("Authorization", "key=" + KEY);
    con.setRequestProperty("Content-Type", "application/json");

    con.getOutputStream().write(json.toString().getBytes());
    con.getOutputStream().close();

    String result = getResponse(con);

    Log.d(
        TAG,
        String.format(
            "finished json request in %.3f seconds",
            (System.nanoTime() - startTime) / 1000000000.0));
    return new JSONObject(result);
  }

  /**
   * Sends a message tagged with a unique ID.
   *
   * @return The unique ID for the sent message.
   */
  public static String sendMessageWithUniqueId(String token) throws IOException, JSONException {
    String uniqueId = generateUniqueId();
    Map<String, String> data = ImmutableMap.of(KEY_UNIQUE_ID, uniqueId);

    sendJSONMessage(HTTP_SERVER_PROD, token, /* params= */ Collections.emptyMap(), data);

    return uniqueId;
  }

  public static boolean containsUniqueId(RemoteMessage message, String uniqueId) {
    return uniqueId.equals(message.getData().get(KEY_UNIQUE_ID));
  }

  public static boolean containsUniqueId(Intent intent, String uniqueId) {
    return uniqueId.equals(intent.getStringExtra(KEY_UNIQUE_ID));
  }

  private static String getResponse(HttpURLConnection con) throws IOException {
    int responseCode = con.getResponseCode();
    Log.i(TAG, "Response code: " + responseCode);
    if (responseCode == 200) {
      InputStream is = con.getInputStream();
      String result = getString(is);
      Log.i(TAG, "Response: " + result);
      is.close();
      return result;
    }
    String errorStream = getString(con.getErrorStream());
    Log.e(TAG, "Error stream: " + errorStream);
    throw new IOException(
        "Response code: " + responseCode + " (expected 200), error stream: " + errorStream);
  }

  private static String getString(InputStream stream) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
    StringBuilder content = new StringBuilder();
    String newLine;
    do {
      newLine = reader.readLine();
      if (newLine != null) {
        content.append(newLine).append('\n');
      }
    } while (newLine != null);
    if (content.length() > 0) {
      // strip last newline
      content.setLength(content.length() - 1);
    }
    return content.toString();
  }

  public static String generateUniqueId() {
    return "id_" + Math.random() + "_" + System.currentTimeMillis();
  }
}
