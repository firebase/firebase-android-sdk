// Copyright 2019 Google LLC
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

package com.google.firebase.segmentation.remote;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.squareup.okhttp.OkHttpClient;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Http client that sends request to Firebase Segmentation backend API. To be implemented */
public class SegmentationServiceClient {

  private final OkHttpClient httpClient;
  private final Executor httpRequestExecutor;

  public enum Code {
    OK,

    SERVER_INTERNAL_ERROR,

    ALREADY_EXISTS,

    PERMISSION_DENIED
  }

  public SegmentationServiceClient() {
    httpClient = new OkHttpClient();
    httpRequestExecutor = Executors.newFixedThreadPool(4);
  }

  public Task<Code> updateCustomInstallationId(
      long projectNumber,
      String customInstallationId,
      String firebaseInstanceId,
      String firebaseInstanceIdToken) {
    return Tasks.forResult(Code.OK);
  }

  public Task<Code> clearCustomInstallationId(
      long projectNumber, String firebaseInstanceId, String firebaseInstanceIdToken) {
    return Tasks.forResult(Code.OK);
  }
}
