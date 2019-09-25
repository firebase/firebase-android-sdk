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
import androidx.annotation.Nullable;
import com.google.android.datatransport.runtime.Destination;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;

public class LegacyFlgDestination implements Destination {
  static final String DESTINATION_NAME = "lflg";
  private static final String DEFAULT_API_KEY =
      StringMerger.mergeStrings("AzSCki82AwsLzKd5O8zo", "IayckHiZRO1EFl1aGoK");
  private static final String EXTRAS_VERSION_MARKER = "1$";
  private static final String EXTRAS_DELIMITER = "\\";

  public static final LegacyFlgDestination DEFAULT_INSTANCE =
      new LegacyFlgDestination(DEFAULT_API_KEY);

  @Nullable private final String endPoint;

  @NonNull private final String apiKey;

  public LegacyFlgDestination(@NonNull String apiKey) {
    this(apiKey, null);
  }

  public LegacyFlgDestination(@NonNull String apiKey, @Nullable String endPoint) {
    this.apiKey = apiKey;
    this.endPoint = endPoint;
  }

  @NonNull
  String getAPIKey() {
    return apiKey;
  }

  @Nullable
  String getEndPoint() {
    return endPoint;
  }

  @NonNull
  @Override
  public String getName() {
    return DESTINATION_NAME;
  }

  @NonNull
  @Override
  public byte[] getExtras() {
    return encodeAsExtras();
  }

  @NonNull
  public static LegacyFlgDestination parseFromExtras(@NonNull byte[] a) {
    try {
      String encodedExtra = new String(a, "UTF-8");
      if (!encodedExtra.startsWith(EXTRAS_VERSION_MARKER)) {
        throw new IllegalArgumentException("Version marker missing from extras");
      }
      encodedExtra = encodedExtra.substring(EXTRAS_VERSION_MARKER.length());
      String[] fields = encodedExtra.split(Pattern.quote(EXTRAS_DELIMITER), 2);
      if (fields.length != 2) {
        throw new IllegalArgumentException("Extra is not a valid encoded LegacyFlgDestination");
      }
      String endPoint = fields[0];
      String apiKey = fields[1];
      return new LegacyFlgDestination(apiKey, endPoint);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 encoding not found.");
    }
  }

  private byte[] encodeAsExtras() {
    String buffer =
        String.format(
            "%s%s%s%s",
            EXTRAS_VERSION_MARKER,
            endPoint == null ? "" : endPoint,
            EXTRAS_DELIMITER,
            apiKey == null ? "" : apiKey);
    try {
      return buffer.getBytes("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("UTF-8 encoding not found.");
    }
  }
}
