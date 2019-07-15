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

package com.google.android.datatransport.cct;

import androidx.annotation.NonNull;
import com.google.android.datatransport.runtime.Destination;
import java.io.UnsupportedEncodingException;

public class LegacyFlgDestination implements Destination {
  static final String DESTINATION_NAME = "lflg";
  private static final String DEFAULT_API_KEY =
      StringMerger.mergeStrings("AzSCki82AwsLzKd5O8zo", "IayckHiZRO1EFl1aGoK");

  public static final LegacyFlgDestination DEFAULT_INSTANCE =
      new LegacyFlgDestination(DEFAULT_API_KEY);

  private final String apiKey;

  private LegacyFlgDestination(String apiKey) {
    this.apiKey = apiKey;
  }

  @NonNull
  String getAPIKey() {
    return apiKey;
  }

  @NonNull
  @Override
  public String getName() {
    return DESTINATION_NAME;
  }

  @NonNull
  @Override
  public byte[] getExtras() {
    return encodeString(apiKey);
  }

  @NonNull
  static byte[] encodeString(@NonNull String s) {
    try {
      return s.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 encoding not found.");
    }
  }

  @NonNull
  static String decodeExtras(@NonNull byte[] a) {
    try {
      return new String(a, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 encoding not found.");
    }
  }
}
