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

package com.google.firebase.testing.fireperf;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/** Generates networks requests with all the proper formatting and information. */
public class NetworkRequestGenerator {

  private static final String LOG_TAG = ListAdapter.class.getSimpleName();

  private static final String URL_BASE_PATH = "fireperf-echo.appspot.com";
  private static final int MAX_THREADS_IN_POOL = 5;
  private static final int NETWORK_REQUEST_DELAY_MEAN = 5;
  private static final int NETWORK_REQUEST_DELAY_STD_DEVIATION = 1;
  private static final int NETWORK_RESPONSE_SIZE_MEAN = 1024;
  private static final int NETWORK_RESPONSE_SIZE_STD_DEVIATION = 80;

  private static final ImmutableList<String> URL_SCHEMES = ImmutableList.of("http", "https");
  private static final ImmutableList<String> HTTP_METHODS =
      ImmutableList.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
  private static final ImmutableList<String> MIME_TYPES =
      ImmutableList.of(
          "text/html",
          "application/postscript",
          "application/octet-stream",
          "video/avi",
          "image/png",
          "text/plain");
  private static final ImmutableList<String> QUERY_PATHS =
      ImmutableList.of("some/random/path", "some/path", "some/path/which/keeps/growing");
  private static final ImmutableList<Integer> STATUS_CODES =
      ImmutableList.of(200, 201, 300, 400, 502, 503, 504);

  private final Random rand;

  NetworkRequestGenerator() {
    this.rand = new Random();
  }

  Future<?> generateRequests(final int totalRequests, final int totalSets) {
    return Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              ExecutorService perfMetricExecutor =
                  Executors.newFixedThreadPool(MAX_THREADS_IN_POOL);
              List<Future<?>> networkRequests = new ArrayList<>();

              for (int i = 0; i < totalSets; i++) {
                for (int j = 0; j < totalRequests; j++) {
                  float delayTime =
                      FireperfUtils.randomGaussianValueWithMean(
                          NETWORK_REQUEST_DELAY_MEAN, NETWORK_REQUEST_DELAY_STD_DEVIATION);

                  int responseSize =
                      (int)
                          FireperfUtils.randomGaussianValueWithMean(
                              NETWORK_RESPONSE_SIZE_MEAN, NETWORK_RESPONSE_SIZE_STD_DEVIATION);

                  String baseUrlString =
                      String.format(
                          Locale.US,
                          /* format= */ "%s://%s/%s/?delay=%fs&size=%d&mime=%s&status=%d",
                          getRandomUrlScheme(),
                          URL_BASE_PATH,
                          getRandomQueryPath(),
                          delayTime,
                          responseSize,
                          getRandomMimeType(),
                          getRandomStatusCode());

                  networkRequests.add(
                      perfMetricExecutor.submit(
                          new NetworkRequestsRunner(baseUrlString, getRandomHttpMethod())));
                }
              }

              for (Future<?> future : networkRequests) {
                try {
                  future.get(20, TimeUnit.MINUTES);
                } catch (Exception e) {
                  Log.e(LOG_TAG, e.getMessage());
                }
              }
            });
  }

  /**
   * Generates a status code at random.
   *
   * @return a random status code.
   */
  private int getRandomStatusCode() {
    int randomIndex = rand.nextInt(STATUS_CODES.size());
    return STATUS_CODES.get(randomIndex);
  }

  /**
   * Generates a MIME type at random.
   *
   * @return a random MIME type.
   */
  private String getRandomMimeType() {
    int randomIndex = rand.nextInt(MIME_TYPES.size());
    return MIME_TYPES.get(randomIndex);
  }

  /**
   * Generates a random URL scheme at random.
   *
   * @return a random URL scheme.
   */
  private String getRandomUrlScheme() {
    int randomIndex = rand.nextInt(URL_SCHEMES.size());
    return URL_SCHEMES.get(randomIndex);
  }

  /**
   * Generates an HTTP method at random.
   *
   * @return a random HTTP method.
   */
  private String getRandomHttpMethod() {
    int randomIndex = rand.nextInt(HTTP_METHODS.size());
    return HTTP_METHODS.get(randomIndex);
  }

  /**
   * Generates a random query path at random.
   *
   * @return a random query path.
   */
  private String getRandomQueryPath() {
    int randomIndex = rand.nextInt(QUERY_PATHS.size());
    return QUERY_PATHS.get(randomIndex);
  }

  private static class NetworkRequestsRunner implements Runnable {
    private final String url;
    private final String httpMethod;

    private NetworkRequestsRunner(@NonNull String url, @NonNull String httpMethod) {
      this.url = url;
      this.httpMethod = httpMethod;
    }

    @Override
    public void run() {
      try {
        Request.Builder request = new Request.Builder().url(url);

        Log.d(LOG_TAG, "Generating network request - " + url);

        if (httpMethod.equals("PATCH") || httpMethod.equals("PUT") || httpMethod.equals("POST")) {
          request.method(httpMethod, RequestBody.create(new byte[0], /* contentType= */ null));
        } else {
          request.method(httpMethod, /* body= */ null);
        }

        new OkHttpClient().newCall(request.build()).execute();

      } catch (IOException e) {
        Log.d(LOG_TAG, e.getMessage());
      }
    }
  }
}
