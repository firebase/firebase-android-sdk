/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.google.firebase.perf.plugin.test;

import java.io.BufferedInputStream;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * A Sample Java class for the test project.
 *
 * This class uses the APIs of the 3rd party networking library that the Plugin will decorate with
 * the bytecode instrumented APIs defined in the SDK.
 */
final class FunctionalTestSampleJavaSource {

  public FunctionalTestSampleJavaSource() {
    okHttpExecute();
  }

  void okHttpExecute() {
    new Thread(
            new Runnable() {
              @Override
              public void run() {
                performOkHttpExecute();
              }
            })
        .start();
  }

  void performOkHttpExecute() {
    try {
      final OkHttpClient ohc = new OkHttpClient();
      final Call call = ohc.newCall(new Request.Builder().url("https://www.google.com/").build());
      final Response response = call.execute();

      ResponseBody body = response.body();
      BufferedInputStream bis = new BufferedInputStream(body.byteStream());

      byte[] result = new byte[128];
      bis.read(result);

      System.out.println(new String(result, "UTF-8"));

    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}
