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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.CODE_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.ERROR_KEY;
import static com.google.firebase.appcheck.internal.HttpErrorResponse.MESSAGE_KEY;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link HttpErrorResponse}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class HttpErrorResponseTest {
  private static final int ERROR_CODE = 403;
  private static final String ERROR_MESSAGE = "error message";

  @Test
  public void fromJsonString_expectDeserialized() throws Exception {
    JSONObject jsonObject = new JSONObject();
    JSONObject innerObject = new JSONObject();
    innerObject.put(CODE_KEY, ERROR_CODE);
    innerObject.put(MESSAGE_KEY, ERROR_MESSAGE);
    jsonObject.put(ERROR_KEY, innerObject);

    HttpErrorResponse httpErrorResponse = HttpErrorResponse.fromJsonString(jsonObject.toString());
    assertThat(httpErrorResponse.getErrorCode()).isEqualTo(ERROR_CODE);
    assertThat(httpErrorResponse.getErrorMessage()).isEqualTo(ERROR_MESSAGE);
  }
}
