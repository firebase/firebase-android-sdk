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
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

/** Http client that sends request to Firebase Segmentation backend API. To be implemented */
public class SegmentationServiceClient {

  private static final String FIREBASE_SEGMENTATION_API_DOMAIN =
      "firebasesegmentation.googleapis.com";
  private static final String UPDATE_REQUEST_RESOURCE_NAME_FORMAT =
      "projects/%s/installations/%s/customSegmentationData";
  private static final String CLEAR_REQUEST_RESOURCE_NAME_FORMAT =
      "projects/%s/installations/%s/customSegmentationData:clear";
  private static final String FIREBASE_SEGMENTATION_API_VERSION = "alpha1";

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  private final OkHttpClient httpClient;
  private final Executor httpRequestExecutor;

  public enum Code {
    OK,

    HTTP_CLIENT_ERROR,

    CONFLICT,

    NETWORK_ERROR,

    SERVER_ERROR,

    UNAUTHORIZED,
  }

  public SegmentationServiceClient() {
    httpClient = new OkHttpClient();
    httpRequestExecutor = Executors.newFixedThreadPool(4);
  }

  public Task<Code> updateCustomInstallationId(
      long projectNumber,
      String apiKey,
      String customInstallationId,
      String firebaseInstanceId,
      String firebaseInstanceIdToken) {
    String resourceName =
        String.format(UPDATE_REQUEST_RESOURCE_NAME_FORMAT, projectNumber, firebaseInstanceId);

    RequestBody requestBody;
    try {
      requestBody =
          RequestBody.create(
              JSON,
              buildUpdateCustomSegmentationDataRequestBody(resourceName, customInstallationId)
                  .toString());
    } catch (JSONException e) {
      return Tasks.forException(e);
    }

    Request request =
        new Request.Builder()
            .url(
                String.format(
                    "https://%s/%s/%s",
                    FIREBASE_SEGMENTATION_API_DOMAIN,
                    FIREBASE_SEGMENTATION_API_VERSION,
                    resourceName))
            .header("X-Goog-Api-Key", apiKey)
            .header("Authorization", "FIREBASE_INSTALLATIONS_AUTH " + firebaseInstanceIdToken)
            .header("Content-Type", "application/json")
            .patch(requestBody)
            .build();

    TaskCompletionSource<Code> taskCompletionSource = new TaskCompletionSource<>();
    httpRequestExecutor.execute(
        () -> {
          try {
            Response response = httpClient.newCall(request).execute();
            switch (response.code()) {
              case 200:
                taskCompletionSource.setResult(Code.OK);
                break;
              case 401:
                taskCompletionSource.setResult(Code.UNAUTHORIZED);
                break;
              case 409:
                taskCompletionSource.setResult(Code.CONFLICT);
                break;
              default:
                taskCompletionSource.setResult(Code.SERVER_ERROR);
                break;
            }
          } catch (IOException e) {
            taskCompletionSource.setResult(Code.NETWORK_ERROR);
          }
        });
    return taskCompletionSource.getTask();
  }

  private static JSONObject buildUpdateCustomSegmentationDataRequestBody(
      String resourceName, String customInstallationId) throws JSONException {
    JSONObject rlt = new JSONObject();
    rlt.put(
        "update_mask",
        "custom_segmentation_data.name,custom_segmentation_data.custom_installation_id");
    JSONObject customSegmentationData = new JSONObject();
    customSegmentationData.put("name", resourceName);
    customSegmentationData.put("custom_installation_id", customInstallationId);
    rlt.put("custom_segmentation_data", customSegmentationData);
    return rlt;
  }

  public Task<Code> clearCustomInstallationId(
      long projectNumber,
      String apiKey,
      String firebaseInstanceId,
      String firebaseInstanceIdToken) {
    String resourceName =
        String.format(CLEAR_REQUEST_RESOURCE_NAME_FORMAT, projectNumber, firebaseInstanceId);

    RequestBody requestBody;
    try {
      requestBody =
          RequestBody.create(
              JSON, buildClearCustomSegmentationDataRequestBody(resourceName).toString());
    } catch (JSONException e) {
      return Tasks.forException(e);
    }

    Request request =
        new Request.Builder()
            .url(
                String.format(
                    "https://%s/%s/%s",
                    FIREBASE_SEGMENTATION_API_DOMAIN,
                    FIREBASE_SEGMENTATION_API_VERSION,
                    resourceName))
            .header("X-Goog-Api-Key", apiKey)
            .header("Authorization", "FIREBASE_INSTALLATIONS_AUTH " + firebaseInstanceIdToken)
            .header("Content-Type", "application/json")
            .post(requestBody)
            .build();

    TaskCompletionSource<Code> taskCompletionSource = new TaskCompletionSource<>();
    httpRequestExecutor.execute(
        () -> {
          try {
            Response response = httpClient.newCall(request).execute();
            switch (response.code()) {
              case 200:
                taskCompletionSource.setResult(Code.OK);
                break;
              case 401:
                taskCompletionSource.setResult(Code.UNAUTHORIZED);
                break;
              default:
                taskCompletionSource.setResult(Code.SERVER_ERROR);
                break;
            }
          } catch (IOException e) {
            taskCompletionSource.setResult(Code.NETWORK_ERROR);
          }
        });
    return taskCompletionSource.getTask();
  }

  private static JSONObject buildClearCustomSegmentationDataRequestBody(String resourceName)
      throws JSONException {
    return new JSONObject().put("name", resourceName);
  }
}
