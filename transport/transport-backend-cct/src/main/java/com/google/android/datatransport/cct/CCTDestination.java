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

public final class CCTDestination implements Destination {
  static final String DESTINATION_NAME = "cct";
  private static final String DEFAULT_API_KEY =
      StringMerger.mergeStrings("AzSCki82AwsLzKd5O8zo", "IayckHiZRO1EFl1aGoK");

  public static final CCTDestination DEFAULT_INSTANCE = new CCTDestination();
  public static final CCTDestination DEFAULT_LEGACY_INSTANCE =
      new CCTDestination(DEFAULT_API_KEY, null);

  private final String apiKey;
  private final String endPoint;

  private CCTDestination() {
    this(null, null);
  }

  public CCTDestination(@Nullable String apiKey, @Nullable String endPoint) {
    this.apiKey = apiKey;
    this.endPoint = endPoint;
  }

  @NonNull
  @Override
  public String getName() {
    return DESTINATION_NAME;
  }

  @Nullable
  @Override
  public byte[] getExtras() {
    return null;
  }

  @Nullable
  public String getAPIKey() {
    return apiKey;
  }

  @Nullable
  public String getEndPoint() {
    return endPoint;
  }
}
