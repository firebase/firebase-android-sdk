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

public class ResponseParser {
  public static final int ResponseActionDiscard = 0;
  public static final int ResponseActionRetry = 1;

  /**
   *
   *
   * <ul>
   *   <li>201 - report was accepted and enqueued immediately, discard it
   *   <li>202 - report was accepted and held for later processing, discard it
   *   <li>200, 203-299 - unexpected result, but assume success and discard the report
   *   <li>300-399 - unexpected result, assume failure, initiate retry process
   *   <li>400-499 - report rejected as duplicate or unauthorized. discard
   *   <li>&gt;= 500 - report rejected due server error. initiate retry process
   * </ul>
   */
  public static int parse(int statusCode) {
    if (statusCode >= 200 && statusCode <= 299) {
      return ResponseActionDiscard;
    } else if (statusCode >= 300 && statusCode <= 399) {
      return ResponseActionRetry;
    } else if (statusCode >= 400 && statusCode <= 499) {
      return ResponseActionDiscard;
    } else if (statusCode >= 500) {
      return ResponseActionRetry;
    } else {
      // Default to retry.
      return ResponseActionRetry;
    }
  }
}
