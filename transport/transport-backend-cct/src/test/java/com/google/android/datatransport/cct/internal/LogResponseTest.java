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

import java.io.IOException;
import java.io.StringReader;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogResponseTest {

  @Test
  public void testLogRequestParsing_emptyJson() throws IOException {
    Assert.assertThrows(IOException.class, () -> LogResponse.fromJson(new StringReader("")));
  }

  @Test
  public void testLogRequestParsing_onlyAwaitMillis() throws IOException {
    String jsonInput = "{\"nextRequestWaitMillis\":1000}";
    assertThat(LogResponse.fromJson(new StringReader(jsonInput)).getNextRequestWaitMillis())
        .isEqualTo(1000);
  }

  @Test
  public void testLogRequestParsing_stringAwaitMillis() throws IOException {
    String jsonInput = "{\"nextRequestWaitMillis\":\"1000\"}";
    assertThat(LogResponse.fromJson(new StringReader(jsonInput)).getNextRequestWaitMillis())
        .isEqualTo(1000);
  }

  @Test
  public void testLogRequestParsing_invalidJsonObjectIncomplete() throws IOException {
    Assert.assertThrows(
        IOException.class,
        () -> LogResponse.fromJson(new StringReader("{\"nextRequestWaitMillis\":")));
  }

  @Test
  public void testLogRequestParsing_invalidJsonObjectNotClosed() throws IOException {
    Assert.assertThrows(IOException.class, () -> LogResponse.fromJson(new StringReader("{")));
  }

  @Test
  public void testLogRequestParsing_invalidJsonObjectNotOpen() throws IOException {
    Assert.assertThrows(
        IOException.class,
        () -> LogResponse.fromJson(new StringReader("\"nextRequestWaitMillis\":3}")));
  }

  @Test
  public void testLogRequestParsing_invalidJsonInvalid() throws IOException {
    Assert.assertThrows(
        IOException.class,
        () -> LogResponse.fromJson(new StringReader("{\"nextRequestWaitMillis\":}")));
  }
}
