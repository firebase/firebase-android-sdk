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

package com.google.android.datatransport.cct.internal;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogResponseTest {

  @Test
  public void testLogRequestParsing_null() {
    assertThat(LogResponse.fromJson(null)).isNull();
  }

  @Test
  public void testLogRequestParsing_emptyJson() {
    assertThat(LogResponse.fromJson("")).isNull();
  }

  @Test
  public void testLogRequestParsing_onlyAwaitMillis() {
    String jsonInput = "{\"next_request_wait_millis\":1000}";
    assertThat(LogResponse.fromJson(jsonInput).getNextRequestWaitMillis()).isEqualTo(1000);
  }

  @Test
  public void testLogRequestParsing_stringAwaitMillis() {
    String jsonInput = "{\"next_request_wait_millis\":\"1000\"}";
    assertThat(LogResponse.fromJson(jsonInput).getNextRequestWaitMillis()).isEqualTo(1000);
  }
}
