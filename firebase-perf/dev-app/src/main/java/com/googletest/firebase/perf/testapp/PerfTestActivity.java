// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.googletest.firebase.perf.testapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.http.AndroidHttpClient;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.FirebasePerformance.HttpMethod;
import com.google.firebase.perf.metrics.AddTrace;
import com.google.firebase.perf.metrics.HttpMetric;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;
import okhttp3.CacheControl;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/** main activity for firebase-perf test. */
@SuppressWarnings("deprecation")
public class PerfTestActivity extends Activity {

  private static final String LOG_TAG = "FirebasePerfTestApp";

  private static final String DEVICE_NAME = getDeviceName();
  private static final FirebaseRemoteConfig firebaseRemoteConfigDefaultInstance =
      FirebaseRemoteConfig.getInstance();
  private static final FirebaseRemoteConfig firebaseRemoteConfigFireperfInstance =
      FirebaseApp.getInstance().get(RemoteConfigComponent.class).get("fireperf");

  private Random mRandom = new Random();
  private ArrayList<String> mHttpUrls = new ArrayList<>();
  private ArrayList<String> mHttpsUrls = new ArrayList<>();
  private String[] mTraceNames = {
    DEVICE_NAME + "-Trace1",
    DEVICE_NAME + "-Trace2",
    DEVICE_NAME + "-Trace3",
    DEVICE_NAME + "-Trace4",
    DEVICE_NAME + "-Trace5"
  };
  private final String[] metricNames = {
    DEVICE_NAME + "-Metric1",
    DEVICE_NAME + "-Metric2",
    DEVICE_NAME + "-Metric3",
    DEVICE_NAME + "-Metric4",
    DEVICE_NAME + "-Metric5"
  };

  private String[] mAttributeNames = {"Attr1", "Attr2", "Attr3", "Attr4", "Attr5"};

  @Override
  @AddTrace(name = "onCreate", enabled = true)
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initViews();
    readUrlsFromFile();
    FirebaseRemoteConfigSettings configSettings =
        new FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0L).build();
    firebaseRemoteConfigDefaultInstance.setConfigSettingsAsync(configSettings);
  }

  @Override
  @AddTrace(name = "onStart", enabled = false)
  protected void onStart() {
    super.onStart();
  }

  @Override
  @AddTrace(name = "onResume")
  protected void onResume() {
    super.onResume();
  }

  @AddTrace(name = "initViews", enabled = true)
  private void initViews() {
    setContentView(R.layout.main_activity);
    findViewById(R.id.trace).setOnClickListener(mLogTrace);
    findViewById(R.id.traceWithLocalAttributes).setOnClickListener(mLogTraceWithLocalAttributes);
    findViewById(R.id.parcel_trace).setOnClickListener(parcelTrace);
    findViewById(R.id.traceWithCounterIncrementingBy1)
        .setOnClickListener(logTraceWithCounterIncrementingBy1);
    findViewById(R.id.traceWithCounterIncrementingByX)
        .setOnClickListener(logTraceWithCounterIncrementingByX);
    findViewById(R.id.traceStartedNotStopped).setOnClickListener(mLogTraceStartedNotStopped);
    findViewById(R.id.init).setOnClickListener(new DoInit());
    findViewById(R.id.test2).setOnClickListener(new TestDialog());
    findViewById(R.id.httpExecute1).setOnClickListener(new DoApacheHttpExecuteRequest());
    findViewById(R.id.httpExecute2).setOnClickListener(new DoApacheHttpExecuteRequestContext());
    findViewById(R.id.httpExecute3).setOnClickListener(new DoApacheHttpExecuteRequestHandler());
    findViewById(R.id.httpExecute4)
        .setOnClickListener(new DoApacheHttpExecuteRequestHandlerContext());
    findViewById(R.id.httpExecute5).setOnClickListener(new DoApacheHttpExecuteHostRequest());
    findViewById(R.id.httpExecute6).setOnClickListener(new DoApacheHttpExecuteHostRequestContext());
    findViewById(R.id.httpExecute7).setOnClickListener(new DoApacheHttpExecuteHostRequestHandler());
    findViewById(R.id.httpExecute8)
        .setOnClickListener(new DoApacheHttpExecuteHostRequestHandlerContext());
    findViewById(R.id.httpExecute9).setOnClickListener(new DoApacheHttpClientPost());
    findViewById(R.id.httpUrlConnection1)
        .setOnClickListener(new DoUrlConnectionOpenConnectionGetContent());
    findViewById(R.id.httpUrlConnection2)
        .setOnClickListener(new DoUrlConnectionOpenConnectionInputStream());
    findViewById(R.id.httpUrlConnection3)
        .setOnClickListener(new DoUrlConnectionOpenConnectionProxy());
    findViewById(R.id.httpUrlConnection4).setOnClickListener(new DoUrlConnectionOpenStream());
    findViewById(R.id.httpUrlConnection5).setOnClickListener(new DoUrlConnectionGetContent());
    findViewById(R.id.httpUrlConnectionPost)
        .setOnClickListener(new DoUrlConnectionOpenConnectionPost());
    findViewById(R.id.httpsUrlConnection1)
        .setOnClickListener(new DoHttpsUrlConnectionOpenConnectionGetContent());
    findViewById(R.id.httpsUrlConnection2)
        .setOnClickListener(new DoHttpsUrlConnectionOpenConnectionInputStream());
    findViewById(R.id.httpUrlConnectionInvalidUrl)
        .setOnClickListener(new DoUrlConnectionInvalidUrl());
    findViewById(R.id.okHttpExecute).setOnClickListener(new DoOkHttpExecute());
    findViewById(R.id.okHttpEnqueue).setOnClickListener(new DoOkHttpEnqueue());
    findViewById(R.id.okHttpPost).setOnClickListener(new DoOkHttpPost());
    findViewById(R.id.okHttpPostEnqueue).setOnClickListener(new DoOkHttpEnqueuePost());
    findViewById(R.id.okHttpExecuteError).setOnClickListener(new DoOkHttpExecuteError());
    findViewById(R.id.okHttpEnqueueError).setOnClickListener(new DoOkHttpEnqueueError());
    findViewById(R.id.okHttpGetWithCachePolicy).setOnClickListener(getWithOkHttpWithCachePolicy);
    findViewById(R.id.enable).setOnClickListener(new DoPerformanceEnable());
    findViewById(R.id.disable).setOnClickListener(new DoPerformanceDisable());
    findViewById(R.id.isPerfEnabled).setOnClickListener(new DoPerformanceIsEnabled());
    findViewById(R.id.manualnetwork).setOnClickListener(new DoManualNetwork());
    findViewById(R.id.fetchRemoteConfigDefaultInstance)
        .setOnClickListener(fetchRemoteConfigDefaultInstance);
    findViewById(R.id.fetchRemoteConfigFireperfInstance)
        .setOnClickListener(fetchRemoteConfigFireperfInstance);
    findViewById(R.id.activateDefaultRemoteConfig)
        .setOnClickListener(activateRemoteConfigDefaultNamespace);
    findViewById(R.id.activateFireperfRemoteConfig)
        .setOnClickListener(activateRemoteConfigFireperfNamespace);
    findViewById(R.id.getTraceSamplingRateFromRc)
        .setOnClickListener(logFirebaseRcTraceSamplingRate);
    findViewById(R.id.getNetworkSamplingRateFromRc)
        .setOnClickListener(logFirebaseRcNetworkSamplingRate);
    findViewById(R.id.getSessionSamplingRateFromRc)
        .setOnClickListener(logFirebaseRcSessionSamplingRate);
    findViewById(R.id.getAllFirebaseRCKeysDefaultNamespace)
        .setOnClickListener(logAllFirebaseRcKeysForDefaultNamespace);
    findViewById(R.id.getAllFirebaseRCKeysFireperfNamespace)
        .setOnClickListener(logAllFirebaseRcKeysForFireperfNamespace);
  }

  public void openFragmentActivity(View view) {
    startActivity(new Intent(this, FragmentActivity.class));
  }

  private void readUrlsFromFile() {
    try {
      InputStream inputStream = getResources().openRawResource(R.raw.urls);
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(inputStream, Charset.defaultCharset()));
      String url;
      while ((url = reader.readLine()) != null) {
        if (url.startsWith("https")) {
          mHttpsUrls.add(url);
        } else {
          mHttpUrls.add(url);
        }
      }
    } catch (IOException e) {
      Log.d(LOG_TAG, "IO Exception reading urls from file: " + e);
    }
  }

  private String getHttpUrl() {
    int random = mRandom.nextInt(mHttpUrls.size());
    return mHttpUrls.get(random);
  }

  private String getHttpsUrl() {
    int random = mRandom.nextInt(mHttpsUrls.size());
    return mHttpsUrls.get(random);
  }

  private static class DoInit implements View.OnClickListener {
    @Override
    public void onClick(View view) {}
  }

  private class TestDialog implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      AlertDialog.Builder builder = new AlertDialog.Builder(PerfTestActivity.this);
      builder.setMessage("Test Dialog.");
      builder.setCancelable(true);
      builder.setPositiveButton("Yes", (dialog, id) -> dialog.cancel());

      builder.setNegativeButton("No", (dialog, id) -> dialog.cancel());
      AlertDialog alert = builder.create();
      alert.show();
    }
  }

  class StringResponseHandler implements ResponseHandler<String> {
    @Override
    public String handleResponse(final HttpResponse response) throws IOException {
      final HttpEntity he = response.getEntity();
      if (he != null) {
        final InputStream is = he.getContent();
        final StringBuilder sb = new StringBuilder(1024 * 4);
        readStream(is, sb);
        is.close();
        he.consumeContent();
        return sb.toString();
      }
      return null;
    }
  }

  private void readStream(final InputStream is, final StringBuilder sb) throws IOException {
    final Reader reader = new InputStreamReader(is, "UTF-8");
    final BufferedReader br = new BufferedReader(reader);
    final char[] buf = new char[8192];
    int len;
    while ((len = br.read(buf)) > 0) {
      sb.append(buf, 0, len);
    }
  }

  private void printStreamContent(InputStream is) throws IOException {
    final StringBuilder sb = new StringBuilder(1024 * 4);
    readStream(is, sb);
    is.close();
    if (sb.length() > 128) {
      sb.setLength(128);
    }

    Log.d(LOG_TAG, sb.toString());
  }

  public static String getDeviceName() {
    String manufacturer = Build.MANUFACTURER;
    String model = Build.MODEL;
    if (model.startsWith(manufacturer)) {
      return model;
    }
    return manufacturer + "-" + model;
  }

  interface HttpClientExecutable {
    String callForResponse(AndroidHttpClient hc) throws IOException;
  }

  class HttpClientRunner implements Runnable {
    HttpClientExecutable httpClientExecutable;

    public HttpClientRunner(HttpClientExecutable httpClientExecutable) {
      this.httpClientExecutable = httpClientExecutable;
    }

    private HttpClientRunner() {}

    @Override
    public void run() {
      final AndroidHttpClient httpClient = AndroidHttpClient.newInstance("PerfTestApp/1.0");
      try {
        String result = httpClientExecutable.callForResponse(httpClient);
        if (result != null && result.length() > 128) {
          result = result.substring(0, 128);
          Log.d(LOG_TAG, result);
        }
      } catch (IOException e) {
        Log.e(LOG_TAG, "IOException: " + e.getMessage());
      } finally {
        httpClient.close();
      }
    }
  }

  private class DoApacheHttpExecuteRequest implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    final HttpResponse response = httpClient.execute(new HttpGet(getHttpUrl()));
                    return new StringResponseHandler().handleResponse(response);
                  }) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteRequestContext implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    final HttpContext context = new BasicHttpContext();
                    final HttpResponse response =
                        httpClient.execute(new HttpGet(getHttpUrl()), context);
                    return new StringResponseHandler().handleResponse(response);
                  }) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteRequestHandler implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient ->
                      httpClient.execute(
                          new HttpGet(getHttpUrl()), new StringResponseHandler())) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteRequestHandlerContext implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient ->
                      httpClient.execute(
                          new HttpGet(getHttpUrl()),
                          new StringResponseHandler(),
                          new BasicHttpContext())) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteHostRequest implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    URL url = new URL(getHttpUrl());
                    final HttpHost target = new HttpHost(url.getHost(), 80, "http");
                    final HttpRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
                    final HttpResponse hr = httpClient.execute(target, request);
                    return new StringResponseHandler().handleResponse(hr);
                  }) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteHostRequestContext implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    URL url = new URL(getHttpUrl());
                    final HttpHost target = new HttpHost(url.getHost(), 80, "http");
                    final HttpRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
                    final HttpContext context = new BasicHttpContext();
                    final HttpResponse hr = httpClient.execute(target, request, context);
                    return new StringResponseHandler().handleResponse(hr);
                  }) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteHostRequestHandler implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    URL url = new URL(getHttpUrl());
                    final HttpHost target = new HttpHost(url.getHost(), 80, "http");
                    final HttpRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
                    return httpClient.execute(target, request, new StringResponseHandler());
                  }) {})
          .start();
    }
  }

  private class DoApacheHttpExecuteHostRequestHandlerContext implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    URL url = new URL(getHttpUrl());
                    final HttpHost target = new HttpHost(url.getHost(), 80, "http");
                    final HttpRequest request = new BasicHttpEntityEnclosingRequest("GET", "/");
                    final HttpContext context = new BasicHttpContext();
                    return httpClient.execute(
                        target, request, new StringResponseHandler(), context);
                  }) {})
          .start();
    }
  }

  private class DoApacheHttpClientPost implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              new HttpClientRunner(
                  httpClient -> {
                    final HttpResponse response = httpClient.execute(new HttpPost(getHttpUrl()));
                    return new StringResponseHandler().handleResponse(response);
                  }) {})
          .start();
    }
  }

  // Instrumenting URL.openConnection() with getContent()
  private class DoUrlConnectionOpenConnectionGetContent implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpURLConnection conn = null;
                try {
                  final URL url = new URL(getHttpUrl());
                  conn = (HttpURLConnection) url.openConnection();

                  Log.d(LOG_TAG, "HTTP " + conn.getResponseCode());
                  Log.d(LOG_TAG, conn.getHeaderFields().toString());

                  final Object content = conn.getContent();
                  if (content instanceof String) {
                    String strContent = (String) content;
                    if (strContent != null && strContent.length() > 128) {
                      strContent = strContent.substring(0, 128);
                      Log.d(LOG_TAG, strContent);
                    }
                  } else {
                    Log.d(LOG_TAG, "Did not receive string from getContent");
                  }

                  conn.disconnect();

                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting URL.openConnection() with getInputStream()
  private class DoUrlConnectionOpenConnectionInputStream implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpURLConnection conn = null;
                try {
                  final URL url = new URL(getHttpUrl());
                  conn = (HttpURLConnection) url.openConnection();

                  if (conn != null) {
                    Log.d(LOG_TAG, "HTTP " + conn.getResponseCode());
                    Log.d(LOG_TAG, conn.getHeaderFields().toString());
                    printStreamContent(conn.getInputStream());

                    conn.disconnect();
                  }
                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting URL.openConnection(Proxy)
  private class DoUrlConnectionOpenConnectionProxy implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpURLConnection conn = null;
                try {
                  final URL url = new URL(getHttpUrl());
                  conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);

                  Log.d(LOG_TAG, "HTTP " + conn.getResponseCode());
                  Log.d(LOG_TAG, conn.getHeaderFields().toString());
                  printStreamContent(conn.getInputStream());

                  conn.disconnect();

                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting URL.openStream()
  private class DoUrlConnectionOpenStream implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                try {
                  final URL url = new URL(getHttpUrl());
                  printStreamContent(url.openStream());

                } catch (IOException e) {
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting URL.getContent()
  private class DoUrlConnectionGetContent implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                try {
                  final URL url = new URL(getHttpUrl());
                  final Object content = url.getContent();
                  if (content instanceof String) {
                    String strContent = String.valueOf(content);
                    if (strContent != null && strContent.length() > 128) {
                      strContent = strContent.substring(0, 128);
                      Log.d(LOG_TAG, strContent);
                    }
                  } else if (content instanceof InputStream) {
                    printStreamContent((InputStream) content);
                  } else {
                    Log.d(LOG_TAG, "Did not receive string from getContent");
                  }

                } catch (IOException e) {
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting URL.openConnection() with getContent() with POST
  private class DoUrlConnectionOpenConnectionPost implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpURLConnection conn = null;
                try {
                  final URL url = new URL(getHttpUrl());
                  conn = (HttpURLConnection) url.openConnection();
                  conn.setDoOutput(true); // setting output implicitly make a POST

                  Log.d(LOG_TAG, "HTTP " + conn.getResponseCode());
                  Log.d(LOG_TAG, conn.getHeaderFields().toString());

                  final Object content = conn.getContent();
                  if (content instanceof String) {
                    String strContent = (String) content;
                    if (strContent.length() > 128) {
                      strContent = strContent.substring(0, 128);
                    }
                    Log.d(LOG_TAG, strContent);
                  } else {
                    Log.d(LOG_TAG, "Did not receive string from getContent");
                  }
                  conn.disconnect();
                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting https URL.openConnection() with getContent()
  private class DoHttpsUrlConnectionOpenConnectionGetContent implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpsURLConnection conn = null;
                try {
                  final URL url = new URL(getHttpsUrl());
                  conn = (HttpsURLConnection) url.openConnection();

                  Log.d(LOG_TAG, "HTTP " + conn.getResponseCode());
                  Log.d(LOG_TAG, conn.getHeaderFields().toString());

                  if (conn != null) {
                    final Object content = conn.getContent();
                    if (content instanceof String) {
                      String strContent = (String) content;
                      if (strContent.length() > 128) {
                        strContent = strContent.substring(0, 128);
                      }
                      Log.d(LOG_TAG, strContent);
                    } else {
                      Log.d(LOG_TAG, "Did not receive string from getContent");
                    }
                    conn.disconnect();
                  }

                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Instrumenting https URL.openConnection() with getInputStream()
  private class DoHttpsUrlConnectionOpenConnectionInputStream implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpURLConnection conn = null;
                try {
                  final URL url = new URL(getHttpsUrl());
                  conn = (HttpsURLConnection) url.openConnection();

                  Log.d(LOG_TAG, "HTTP " + String.valueOf(conn.getResponseCode()));
                  Log.d(LOG_TAG, conn.getHeaderFields().toString());
                  printStreamContent(conn.getInputStream());

                  conn.disconnect();

                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  // Invalid URL with HttpURLConnection
  private class DoUrlConnectionInvalidUrl implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpURLConnection conn = null;
                try {
                  // Invalid url
                  final URL url = new URL("http://www.googtestle.com/");
                  conn = (HttpURLConnection) url.openConnection();
                  conn.setReadTimeout(1000);
                  conn.setConnectTimeout(1000);

                  Log.d(LOG_TAG, "HTTP " + conn.getResponseCode());
                  Log.d(LOG_TAG, conn.getHeaderFields().toString());
                  printStreamContent(conn.getInputStream());

                  conn.disconnect();

                } catch (IOException e) {
                  try {
                    BufferedInputStream bis = new BufferedInputStream(conn.getErrorStream());
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    int result = bis.read();
                    while (result != -1) {
                      buf.write((byte) result);
                      result = bis.read();
                    }
                    Log.e(LOG_TAG, buf.toString("UTF-8"));
                  } catch (IOException e1) {
                    Log.e(LOG_TAG, "IOException while reading error stream");
                  }
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  private class DoOkHttpExecute implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                try {
                  final OkHttpClient ohc = new OkHttpClient();
                  final Call call = ohc.newCall(new Request.Builder().url(getHttpsUrl()).build());
                  final Response response = call.execute();
                  ResponseBody body = response.body();
                  BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                  byte[] result = new byte[128];
                  bis.read(result);
                  Log.i(LOG_TAG, new String(result, "UTF-8"));
                } catch (IOException e) {
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  private class DoOkHttpEnqueue implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                final OkHttpClient ohc = new OkHttpClient();
                final Call call = ohc.newCall(new Request.Builder().url(getHttpsUrl()).build());
                call.enqueue(
                    new Callback() {
                      @Override
                      public void onFailure(Call call, IOException e) {
                        Log.e(LOG_TAG, "OkHttpClient failed", e);
                      }

                      @Override
                      public void onResponse(Call call, Response response) throws IOException {
                        ResponseBody body = response.body();
                        BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                        byte[] result = new byte[128];
                        bis.read(result);
                        Log.i(LOG_TAG, new String(result, "UTF-8"));
                      }
                    });
              })
          .start();
    }
  }

  private class DoOkHttpPost implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                try {
                  final OkHttpClient ohc = new OkHttpClient();
                  final Call call =
                      ohc.newCall(
                          new Request.Builder()
                              .url(getHttpsUrl())
                              .post(
                                  RequestBody.create(
                                      MediaType.parse("application/json; charset=utf-8"), ""))
                              .build());
                  final Response response = call.execute();
                  ResponseBody body = response.body();
                  BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                  byte[] result = new byte[128];
                  bis.read(result);
                  Log.i(LOG_TAG, new String(result, "UTF-8"));
                } catch (IOException e) {
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  private class DoOkHttpEnqueuePost implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                final OkHttpClient ohc = new OkHttpClient();
                final Call call =
                    ohc.newCall(
                        new Request.Builder()
                            .url(getHttpsUrl())
                            .post(
                                RequestBody.create(
                                    MediaType.parse("application/json; charset=utf-8"), ""))
                            .build());
                call.enqueue(
                    new Callback() {
                      @Override
                      public void onFailure(Call call, IOException e) {
                        Log.e(LOG_TAG, "OkHttpClient failed", e);
                      }

                      @Override
                      public void onResponse(Call call, Response response) throws IOException {
                        ResponseBody body = response.body();
                        BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                        byte[] result = new byte[128];
                        bis.read(result);
                        Log.e(LOG_TAG, new String(result, "UTF-8"));
                      }
                    });
              })
          .start();
    }
  }

  private static class DoOkHttpExecuteError implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                try {
                  final OkHttpClient ohc = new OkHttpClient();
                  // Invalid url
                  final Call call =
                      ohc.newCall(
                          new Request.Builder().url("http://www.googleerrorurl.com/").build());
                  final Response response = call.execute();
                  ResponseBody body = response.body();
                  BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                  byte[] result = new byte[128];
                  bis.read(result);
                  Log.i(LOG_TAG, new String(result, "UTF-8"));
                } catch (IOException e) {
                  Log.e(LOG_TAG, "IOException: " + e.getMessage());
                }
              })
          .start();
    }
  }

  private static class DoOkHttpEnqueueError implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                final OkHttpClient ohc = new OkHttpClient();
                // Invalid url
                final Call call =
                    ohc.newCall(
                        new Request.Builder().url("http://www.googleerrorurl.com/").build());
                call.enqueue(
                    new Callback() {
                      @Override
                      public void onFailure(Call call, IOException e) {
                        Log.e(LOG_TAG, "OkHttpClient failed", e);
                      }

                      @Override
                      public void onResponse(Call call, Response response) throws IOException {
                        ResponseBody body = response.body();
                        BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                        byte[] result = new byte[128];
                        bis.read(result);
                        Log.i(LOG_TAG, new String(result, "UTF-8"));
                      }
                    });
              })
          .start();
    }
  }

  private OnClickListener getWithOkHttpWithCachePolicy =
      v -> {
        Log.d(LOG_TAG, "Running GET to : https://api.github.com/users/octocat/orgs");

        new Thread(
                () -> {
                  final OkHttpClient ohc = new OkHttpClient();
                  // API that supports caching.
                  final Call call =
                      ohc.newCall(
                          new Request.Builder()
                              .cacheControl(
                                  new CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build())
                              .url("https://api.github.com/users/octocat/orgs")
                              .build());
                  call.enqueue(
                      new Callback() {
                        @Override
                        public void onFailure(Call call, IOException e) {
                          Log.e(LOG_TAG, "OkHttpClient failed", e);
                        }

                        @Override
                        public void onResponse(Call call, Response response) throws IOException {
                          ResponseBody body = response.body();
                          BufferedInputStream bis = new BufferedInputStream(body.byteStream());
                          byte[] result = new byte[128];
                          bis.read(result);
                          Log.d(LOG_TAG, "Github API response: " + new String(result, "UTF-8"));
                        }
                      });
                })
            .start();
      };

  private static class DoManualNetwork implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      new Thread(
              () -> {
                HttpMetric metric =
                    FirebasePerformance.getInstance()
                        .newHttpMetric("https://www.google.com", HttpMethod.POST);
                metric.putAttribute("dim1", "free");
                metric.putAttribute("dim2", "paid");
                Log.d("FirebasePerformance", "attributes: " + metric.getAttributes());
                Log.d("FirebasePerformance", "removing attribute dim1");
                metric.removeAttribute("dim1");
                Log.d("FirebasePerformance", "attribute for dim2: " + metric.getAttribute("dim2"));
                Log.d("FirebasePerformance", "all attributes: " + metric.getAttributes());
                metric.start();
                metric.setHttpResponseCode(200);
                metric.stop();
              })
          .start();
    }
  }

  private static class DoPerformanceEnable implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      FirebasePerformance.getInstance().setPerformanceCollectionEnabled(true);
    }
  }

  private static class DoPerformanceDisable implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      FirebasePerformance.getInstance().setPerformanceCollectionEnabled(false);
    }
  }

  private static class DoPerformanceIsEnabled implements View.OnClickListener {
    @Override
    public void onClick(View view) {
      Log.d(
          LOG_TAG,
          "isEnabled: " + FirebasePerformance.getInstance().isPerformanceCollectionEnabled());
    }
  }

  private final OnClickListener parcelTrace =
      v -> {
        String traceName = mTraceNames[mRandom.nextInt(mTraceNames.length)];
        Log.d(LOG_TAG, "Logging trace as " + traceName);
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();

        trace.incrementMetric("testMetric1", 1);
        trace.incrementMetric("testMetric2", 1);
        trace.incrementMetric("testMetric1", 1);
        try {
          Thread.sleep(mRandom.nextInt(10));
        } catch (InterruptedException ignored) {
          Log.d(LOG_TAG, "Failed to sleep");
        }
        Log.d(LOG_TAG, "Starting AnotherActivity");
        Intent i = new Intent(PerfTestActivity.this, AnotherActivity.class);
        i.putExtra("trace", trace);
        startActivity(i);
      };

  private OnClickListener mLogTrace =
      v -> {
        String traceName = mTraceNames[mRandom.nextInt(mTraceNames.length)];
        Log.d(LOG_TAG, "Logging trace as " + traceName);
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();
        try {
          Thread.sleep(mRandom.nextInt(10));
        } catch (InterruptedException ignored) {
          Log.d(LOG_TAG, "Failed to sleep");
        }
        trace.stop();
      };

  private OnClickListener mLogTraceWithLocalAttributes =
      v -> {
        String traceName = mTraceNames[mRandom.nextInt(mTraceNames.length)];
        Log.d(LOG_TAG, "Logging trace with local attribute as " + traceName);
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();
        trace.putAttribute(
            mAttributeNames[mRandom.nextInt(mAttributeNames.length)],
            String.valueOf(mRandom.nextInt(500)));
        try {
          Thread.sleep(mRandom.nextInt(10));
        } catch (InterruptedException ignored) {
          Log.d(LOG_TAG, "Failed to sleep");
        }
        trace.stop();
      };

  private final OnClickListener logTraceWithCounterIncrementingBy1 =
      v -> {
        String traceName = mTraceNames[mRandom.nextInt(mTraceNames.length)];
        Log.d(LOG_TAG, "Logging trace as " + traceName);
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();
        try {
          Thread.sleep(mRandom.nextInt(10));
        } catch (InterruptedException ignored) {
          Log.d(LOG_TAG, "Failed to sleep");
        }
        String metricName = metricNames[mRandom.nextInt(metricNames.length)];
        trace.incrementMetric(metricName, 1);
        int randomCount = mRandom.nextInt(10);
        for (int i = 0; i < randomCount; i++) {
          trace.incrementMetric(metricName, 1);
        }
        trace.stop();
      };

  private final OnClickListener logTraceWithCounterIncrementingByX =
      v -> {
        String traceName = mTraceNames[mRandom.nextInt(mTraceNames.length)];
        Log.d(LOG_TAG, "Logging trace as " + traceName);
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();
        try {
          Thread.sleep(mRandom.nextInt(10));
        } catch (InterruptedException ignored) {
          Log.d(LOG_TAG, "Failed to sleep");
        }
        String metricName = metricNames[mRandom.nextInt(metricNames.length)];
        trace.incrementMetric(metricName, mRandom.nextInt() + 1);
        trace.stop();
      };

  private OnClickListener mLogTraceStartedNotStopped =
      v -> {
        String traceName = "TraceStartedNotStopped";
        Log.d(LOG_TAG, "Logging trace as " + traceName);
        Trace trace = FirebasePerformance.getInstance().newTrace(traceName);
        trace.start();
        // trace.finalize();
      };

  private OnClickListener fetchRemoteConfigDefaultInstance =
      v -> {
        Log.d(LOG_TAG, "Triggering Firebase Remote Config fetch.");

        firebaseRemoteConfigDefaultInstance
            .fetch(0)
            .addOnCompleteListener(
                task -> Log.d(LOG_TAG, "FRC Fetch (default) completed successfully"));
      };

  private OnClickListener fetchRemoteConfigFireperfInstance =
      v -> {
        Log.d(LOG_TAG, "Triggering Firebase Remote Config fetch.");

        firebaseRemoteConfigFireperfInstance
            .fetch(0)
            .addOnCompleteListener(
                task -> Log.d(LOG_TAG, "FRC Fetch (fireperf) completed successfully"));
      };

  private OnClickListener activateRemoteConfigDefaultNamespace =
      v -> {
        Log.d(LOG_TAG, "Activating Firebase Remote Config fetched values for all namespaces");

        firebaseRemoteConfigDefaultInstance
            .activate()
            .addOnCompleteListener(
                task -> {
                  if (task.getResult()) {
                    Log.d(
                        LOG_TAG,
                        "Successfully activated remote config values for default namespace");
                  }
                });
      };

  private OnClickListener activateRemoteConfigFireperfNamespace =
      v -> {
        Log.d(
            LOG_TAG, "Activating Firebase Remote Config fetched values for the fireperf namespace");

        firebaseRemoteConfigFireperfInstance
            .activate()
            .addOnCompleteListener(
                task -> {
                  if (task.getResult()) {
                    Log.d(
                        LOG_TAG,
                        "Successfully activated remote config values for the fireperf namespace");
                  }
                });
      };

  private OnClickListener logFirebaseRcTraceSamplingRate =
      v ->
          Log.d(
              LOG_TAG,
              "FRC values - fpr_vc_trace_sampling_rate: "
                  + firebaseRemoteConfigFireperfInstance.getString("fpr_vc_trace_sampling_rate"));

  private OnClickListener logFirebaseRcNetworkSamplingRate =
      v ->
          Log.d(
              LOG_TAG,
              "FRC values - fpr_vc_network_request_sampling_rate: "
                  + firebaseRemoteConfigFireperfInstance.getString(
                      "fpr_vc_network_request_sampling_rate"));

  private OnClickListener logFirebaseRcSessionSamplingRate =
      v ->
          Log.d(
              LOG_TAG,
              "FRC values - fpr_vc_session_sampling_rate: "
                  + firebaseRemoteConfigFireperfInstance.getString("fpr_vc_session_sampling_rate"));

  private OnClickListener logAllFirebaseRcKeysForDefaultNamespace =
      v -> {
        Map<String, String> rcKeyValue = new HashMap<>();

        for (String rcKey : firebaseRemoteConfigDefaultInstance.getKeysByPrefix(null)) {
          rcKeyValue.put(rcKey, firebaseRemoteConfigDefaultInstance.getValue(rcKey).asString());
        }

        Log.d(LOG_TAG, "All FRC keys for default namespace: " + rcKeyValue);
      };

  private OnClickListener logAllFirebaseRcKeysForFireperfNamespace =
      v -> {
        Map<String, String> rcKeyValue = new HashMap<>();

        for (String rcKey : firebaseRemoteConfigFireperfInstance.getKeysByPrefix(null)) {
          rcKeyValue.put(rcKey, firebaseRemoteConfigFireperfInstance.getValue(rcKey).asString());
        }

        Log.d(LOG_TAG, "All FRC keys for fireperf namespace: " + rcKeyValue);
      };
}
