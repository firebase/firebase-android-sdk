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

package com.google.firebase.crashlytics.internal.common;

import static org.junit.Assert.assertEquals;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import org.junit.Test;

public class ResponseParserTest extends CrashlyticsTestCase {
  /** Tests the parse method, ResponsParse contains a comment with the logic. */
  @Test
  public void testParse() {
    assertEquals(ResponseParser.ResponseActionDiscard, ResponseParser.parse(201));
    assertEquals(ResponseParser.ResponseActionDiscard, ResponseParser.parse(202));
    assertEquals(ResponseParser.ResponseActionRetry, ResponseParser.parse(300));
    assertEquals(ResponseParser.ResponseActionRetry, ResponseParser.parse(399));
    assertEquals(ResponseParser.ResponseActionDiscard, ResponseParser.parse(400));
    assertEquals(ResponseParser.ResponseActionDiscard, ResponseParser.parse(499));
    assertEquals(ResponseParser.ResponseActionRetry, ResponseParser.parse(500));
  }
}
