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

package com.google.firebase.storage.network;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.StorageException;
import com.google.firebase.storage.network.connection.HttpURLConnectionFactory;
import com.google.firebase.storage.network.connection.HttpURLConnectionFactoryImpl;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** Encapsulates a single network request and response */
@SuppressWarnings("unused")
public abstract class NetworkRequest {
  private static final String TAG = "NetworkRequest";

  private static final String X_FIREBASE_GMPID = "x-firebase-gmpid";

  /* Do not change these values without changing corresponding logic on the SDK side*/
  public static final int INITIALIZATION_EXCEPTION = -1;
  public static final int NETWORK_UNAVAILABLE = -2;

  /*package*/ static final String GET = "GET";
  /*package*/ static final String DELETE = "DELETE";
  /*package*/ static final String POST = "POST";
  /*package*/ static final String PATCH = "PATCH";
  /*package*/ static final String PUT = "PUT";

  private static final int MAXIMUM_TOKEN_WAIT_TIME_MS = 30000;
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String CONTENT_LENGTH = "Content-Length";
  private static final String UTF_8 = "UTF-8";

  @NonNull static Uri sNetworkRequestUrl = Uri.parse("https://firebasestorage.googleapis.com/v0");

  // For test purposes only.
  /*package*/ static HttpURLConnectionFactory connectionFactory =
      new HttpURLConnectionFactoryImpl();
  protected final Uri mGsUri;
  protected Exception mException;

  private static String gmsCoreVersion;
  private Context context;
  private Map<String, List<String>> resultHeaders;
  private int resultCode;
  private String rawStringResponse;
  private int resultingContentLength;
  private InputStream resultInputStream;
  private HttpURLConnection connection;
  private Map<String, String> requestHeaders = new HashMap<>();

  public NetworkRequest(@NonNull Uri gsUri, @NonNull FirebaseApp app) {
    Preconditions.checkNotNull(gsUri);
    Preconditions.checkNotNull(app);
    this.mGsUri = gsUri;
    this.context = app.getApplicationContext();

    this.setCustomHeader(X_FIREBASE_GMPID, app.getOptions().getApplicationId());
  }

  @NonNull
  public static String getAuthority() {
    return sNetworkRequestUrl.getAuthority();
  }

  /**
   * Returns the target Url to use for this request
   *
   * @return Url for the target REST call in string form.
   */
  @NonNull
  public static Uri getDefaultURL(@NonNull Uri gsUri) {
    Preconditions.checkNotNull(gsUri);
    String pathWithoutBucket = getPathWithoutBucket(gsUri);
    Uri.Builder uriBuilder = sNetworkRequestUrl.buildUpon();
    uriBuilder.appendPath("b");
    uriBuilder.appendPath(gsUri.getAuthority());
    uriBuilder.appendPath("o");
    uriBuilder.appendPath(pathWithoutBucket);
    return uriBuilder.build();
  }

  /**
   * Returns the decoded path of the object but excludes the bucket name
   *
   * @param gsUri the "gs://" Uri of the blob.
   * @return the path in string form.
   */
  private static String getPathWithoutBucket(@NonNull Uri gsUri) {
    String path = gsUri.getPath();
    if (path == null) {
      return "";
    }
    return path.startsWith("/") ? path.substring(1) : path;
  }

  /**
   * Returns the decoded path of the object but excludes the bucket name
   *
   * @return the path in string form.
   */
  String getPathWithoutBucket() {
    return getPathWithoutBucket(mGsUri);
  }

  @NonNull
  protected abstract String getAction();

  /**
   * Returns the target Url to use for this request
   *
   * @return Url for the target REST call in string form.
   */
  @NonNull
  protected Uri getURL() {
    return getDefaultURL(mGsUri);
  }

  /**
   * Can be overridden to return a JSONObject to populate the request body.
   *
   * @return JSONObject of the REST body.
   */
  @Nullable
  protected JSONObject getOutputJSON() {
    return null;
  }

  /**
   * Can be overridden to return a byte array to populate the request body.
   *
   * @return byte[] of the REST body.
   */
  @Nullable
  protected byte[] getOutputRaw() {
    return null;
  }

  /**
   * There are cases where a large byte[] is sent for the body, but only a portion is actually sent
   * to the server.
   *
   * @return count of bytes to send from {@link #getOutputRaw()}.
   */
  protected int getOutputRawSize() {
    return 0;
  }

  /**
   * If overridden, returns the query parameters to send on the REST request.
   *
   * @return If applicable, query params as a Map.
   */
  @Nullable
  protected Map<String, String> getQueryParameters() {
    return null;
  }

  /** Resets the result of this request */
  public final void reset() {
    mException = null;
    resultCode = 0;
  }

  public void setCustomHeader(String key, String value) {
    requestHeaders.put(key, value);
  }

  public InputStream getStream() {
    return resultInputStream;
  }

  /** returns the resulting body in JSONObject form, if it could be parsed. */
  public JSONObject getResultBody() {
    JSONObject resultBody;
    if (!TextUtils.isEmpty(rawStringResponse)) {
      try {
        resultBody = new JSONObject(rawStringResponse);
      } catch (JSONException e) {
        Log.e(TAG, "error parsing result into JSON:" + rawStringResponse, e);

        resultBody = new JSONObject();
      }
    } else {
      resultBody = new JSONObject();
    }
    return resultBody;
  }

  @SuppressWarnings("deprecation")
  public void performRequestStart(String token) {
    if (mException != null) {
      resultCode = INITIALIZATION_EXCEPTION;
      return;
    }

    if (Log.isLoggable(TAG, Log.DEBUG)) {
      Log.d(TAG, "sending network request " + getAction() + " " + getURL());
    }
    ConnectivityManager connMgr =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    android.net.NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
    if (networkInfo == null || !networkInfo.isConnected()) {
      resultCode = NETWORK_UNAVAILABLE;
      mException = new SocketException("Network subsystem is unavailable");
      return;
    }

    try {
      connection = createConnection();
      connection.setRequestMethod(getAction());

      constructMessage(connection, token);
      parseResponse(connection);
      if (Log.isLoggable(TAG, Log.DEBUG)) {
        Log.d(TAG, "network request result " + resultCode);
      }
    } catch (IOException e) {
      Log.w(TAG, "error sending network request " + getAction() + " " + getURL(), e);

      mException = e;
      resultCode = NETWORK_UNAVAILABLE;
    }
  }

  public void performRequestEnd() {
    if (connection != null) {
      connection.disconnect();
    }
  }

  /** Sends the REST network request. */
  private final void performRequest(String token) {
    performRequestStart(token);
    try {
      processResponseStream();
    } catch (IOException e) {
      Log.w(TAG, "error sending network request " + getAction() + " " + getURL(), e);

      mException = e;
      resultCode = NETWORK_UNAVAILABLE;
    }
    performRequestEnd();
  }

  public void performRequest(@Nullable String authToken, @NonNull Context applicationContext) {
    if (!ensureNetworkAvailable(applicationContext)) {
      return;
    }
    performRequest(authToken);
  }

  @SuppressWarnings("deprecation")
  private boolean ensureNetworkAvailable(Context context) {
    ConnectivityManager connMgr =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    android.net.NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
    if (networkInfo == null || !networkInfo.isConnected()) {
      mException = new SocketException("Network subsystem is unavailable");
      resultCode = NETWORK_UNAVAILABLE;
      return false;
    }
    return true;
  }

  private HttpURLConnection createConnection() throws IOException {
    HttpURLConnection conn;
    URL url;

    Uri connectionUri = getURL();

    Map<String, String> queryParams = getQueryParameters();
    if (queryParams != null) {
      Uri.Builder uriBuilder = connectionUri.buildUpon();
      for (Map.Entry<String, String> param : queryParams.entrySet()) {
        uriBuilder.appendQueryParameter(param.getKey(), param.getValue());
      }
      connectionUri = uriBuilder.build();
    }

    conn = connectionFactory.createInstance(new URL(connectionUri.toString()));
    return conn;
  }

  @NonNull
  private static String getGmsCoreVersion(Context context) {
    if (gmsCoreVersion == null) {
      PackageManager packageManager = context.getPackageManager();
      try {
        PackageInfo info = packageManager.getPackageInfo("com.google.android.gms", 0);
        gmsCoreVersion = info.versionName;
      } catch (PackageManager.NameNotFoundException e) {
        Log.e(TAG, "Unable to find gmscore in package manager", e);
      }
      if (gmsCoreVersion == null) {
        gmsCoreVersion = "[No Gmscore]";
      }
    }
    return gmsCoreVersion;
  }

  @SuppressWarnings("TryFinallyCanBeTryWithResources")
  private void constructMessage(@NonNull HttpURLConnection conn, String token) throws IOException {
    Preconditions.checkNotNull(conn);

    if (!TextUtils.isEmpty(token)) {
      conn.setRequestProperty("Authorization", "Firebase " + token);
    } else {
      Log.w(TAG, "no auth token for request");
    }

    StringBuilder userAgent = new StringBuilder("Android/");
    String gmsCore = getGmsCoreVersion(context);
    if (!TextUtils.isEmpty(gmsCore)) {
      userAgent.append(gmsCore);
    }
    conn.setRequestProperty("X-Firebase-Storage-Version", userAgent.toString());

    Map<String, String> requestProperties = requestHeaders;
    for (Map.Entry<String, String> entry : requestProperties.entrySet()) {
      conn.setRequestProperty(entry.getKey(), entry.getValue());
    }

    JSONObject jsonObject = getOutputJSON();
    byte[] rawOutput;
    int rawSize;

    if (jsonObject != null) {
      rawOutput = jsonObject.toString().getBytes(UTF_8);
      rawSize = rawOutput.length;
    } else {
      rawOutput = getOutputRaw();
      rawSize = getOutputRawSize();
      if (rawSize == 0 && rawOutput != null) {
        rawSize = rawOutput.length;
      }
    }

    if (rawOutput != null && rawOutput.length > 0) {
      if (jsonObject != null) {
        conn.setRequestProperty(CONTENT_TYPE, APPLICATION_JSON);
      }
      conn.setDoOutput(true);
      conn.setRequestProperty(CONTENT_LENGTH, Integer.toString(rawSize));
    } else {
      conn.setRequestProperty(CONTENT_LENGTH, "0");
    }

    conn.setUseCaches(false);
    conn.setDoInput(true);

    if (rawOutput != null && rawOutput.length > 0) {
      OutputStream outputStream = conn.getOutputStream();
      if (outputStream != null) {
        BufferedOutputStream bufferedStream = new BufferedOutputStream(outputStream);
        try {
          bufferedStream.write(rawOutput, 0, rawSize);
        } finally {
          bufferedStream.close();
        }
      } else {
        Log.e(TAG, "Unable to write to the http request!");
      }
    }
  }

  private void parseResponse(@NonNull HttpURLConnection conn) throws IOException {
    Preconditions.checkNotNull(conn);

    resultCode = conn.getResponseCode();
    resultHeaders = conn.getHeaderFields();
    resultingContentLength = conn.getContentLength();

    if (isResultSuccess()) {
      resultInputStream = conn.getInputStream();
    } else {
      resultInputStream = conn.getErrorStream();
    }
  }

  @SuppressWarnings("TryFinallyCanBeTryWithResources")
  private void parseResponse(@Nullable InputStream resultStream) throws IOException {
    StringBuilder sb = new StringBuilder();
    if (resultStream != null) {
      BufferedReader br = new BufferedReader(new InputStreamReader(resultStream, UTF_8));
      try {
        String input;
        while ((input = br.readLine()) != null) {
          sb.append(input);
        }
      } finally {
        br.close();
      }
    }
    rawStringResponse = sb.toString();

    if (!isResultSuccess()) {
      mException = new IOException(rawStringResponse);
    }
  }

  private void processResponseStream() throws IOException {
    if (isResultSuccess()) {
      parseSuccessulResponse(resultInputStream);
    } else {
      parseErrorResponse(resultInputStream);
    }
  }

  protected void parseSuccessulResponse(@Nullable InputStream resultStream) throws IOException {
    parseResponse(resultStream);
  }

  protected void parseErrorResponse(@Nullable InputStream resultStream) throws IOException {
    parseResponse(resultStream);
  }

  @Nullable
  public String getRawResult() {
    return rawStringResponse;
  }

  /**
   * If overridden, returns the headers to send on the REST request.
   *
   * @return Map of entries to use as Header entries.
   */
  @NonNull
  public Map<String, String> getResultHeaders() {
    return requestHeaders;
  }
  /**
   * If an error has occurred, returns the exception. Otherwise null.
   *
   * @return an Exception representing the reason the REST call failed.
   */
  @Nullable
  public Exception getException() {
    return mException;
  }

  /**
   * Returns the resulting headers from the REST call
   *
   * @return result headers in Map form.
   */
  @Nullable
  public Map<String, List<String>> getResultHeadersImpl() {
    return resultHeaders;
  }

  /**
   * The HTTP status code of the REST call.
   *
   * @return an {@link Integer} representing the HTTP result code.
   */
  public int getResultCode() {
    return resultCode;
  }

  /**
   * Returns true if the operation was successful.
   *
   * @return true if successful, false if an exception was thrown or the server returns a result
   *     code indicating an error.
   */
  public boolean isResultSuccess() {
    return resultCode >= 200 && resultCode < 300;
  }

  @Nullable
  public String getResultString(String key) {
    Map<String, List<String>> resultHeaders = getResultHeadersImpl();
    if (resultHeaders != null) {
      List<String> urlList = resultHeaders.get(key);
      if (urlList != null && urlList.size() > 0) {
        return urlList.get(0);
      }
    }
    return null;
  }

  public int getResultingContentLength() {
    return resultingContentLength;
  }

  public <TResult> void completeTask(TaskCompletionSource<TResult> source, TResult result) {
    Exception exception = getException();
    if (isResultSuccess() && exception == null) {
      source.setResult(result);
    } else {
      StorageException se = StorageException.fromExceptionAndHttpCode(exception, getResultCode());
      assert se != null;
      source.setException(se);
    }
  }
}
