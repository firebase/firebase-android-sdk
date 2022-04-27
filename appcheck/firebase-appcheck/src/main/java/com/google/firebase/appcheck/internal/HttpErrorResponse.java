// Copyright 2021 Google LLC
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

package com.google.firebase.appcheck.internal;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import org.json.JSONException;
import org.json.JSONObject;

/** Client-side model of an HTTP error. */
public class HttpErrorResponse {
  @VisibleForTesting static final String ERROR_KEY = "error";
  @VisibleForTesting static final String CODE_KEY = "code";
  @VisibleForTesting static final String MESSAGE_KEY = "message";

  private int errorCode;
  private String errorMessage;

  @NonNull
  public static HttpErrorResponse fromJsonString(@NonNull String jsonString) throws JSONException {
    JSONObject jsonObject = new JSONObject(jsonString);
    String innerErrorString = jsonObject.optString(ERROR_KEY);
    JSONObject innerJsonObject = new JSONObject(innerErrorString);
    int code = innerJsonObject.optInt(CODE_KEY);
    String message = innerJsonObject.optString(MESSAGE_KEY);

    return new HttpErrorResponse(code, message);
  }

  private HttpErrorResponse(int errorCode, @NonNull String errorMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public int getErrorCode() {
    return errorCode;
  }

  @NonNull
  public String getErrorMessage() {
    return errorMessage;
  }
}
