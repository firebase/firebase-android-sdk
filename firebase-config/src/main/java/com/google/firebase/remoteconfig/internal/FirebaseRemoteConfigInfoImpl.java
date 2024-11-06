// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient.LastFetchStatus;

/**
 * Impl class for FirebaseRemoteConfigInfo.
 *
 * @author Miraziz Yusupov
 * @hide
 */
public class FirebaseRemoteConfigInfoImpl implements FirebaseRemoteConfigInfo {
  private final long lastSuccessfulFetchTimeInMillis;
  @LastFetchStatus private final int lastFetchStatus;
  private final FirebaseRemoteConfigSettings configSettings;

  private FirebaseRemoteConfigInfoImpl(
      long lastSuccessfulFetchTimeInMillis,
      int lastFetchStatus,
      FirebaseRemoteConfigSettings configSettings) {
    this.lastSuccessfulFetchTimeInMillis = lastSuccessfulFetchTimeInMillis;
    this.lastFetchStatus = lastFetchStatus;
    this.configSettings = configSettings;
  }

  @Override
  public long getFetchTimeMillis() {
    return lastSuccessfulFetchTimeInMillis;
  }

  @Override
  public int getLastFetchStatus() {
    return lastFetchStatus;
  }

  @Override
  public FirebaseRemoteConfigSettings getConfigSettings() {
    return configSettings;
  }

  /** Builder for creating an instance of {@link FirebaseRemoteConfigInfo}. */
  public static class Builder {
    private Builder() {}

    private long builderLastSuccessfulFetchTimeInMillis;
    @LastFetchStatus private int builderLastFetchStatus;
    private FirebaseRemoteConfigSettings builderConfigSettings;

    public Builder withLastSuccessfulFetchTimeInMillis(long fetchTimeInMillis) {
      this.builderLastSuccessfulFetchTimeInMillis = fetchTimeInMillis;
      return this;
    }

    Builder withLastFetchStatus(@LastFetchStatus int lastFetchStatus) {
      this.builderLastFetchStatus = lastFetchStatus;
      return this;
    }

    Builder withConfigSettings(FirebaseRemoteConfigSettings configSettings) {
      this.builderConfigSettings = configSettings;
      return this;
    }

    public FirebaseRemoteConfigInfoImpl build() {
      return new FirebaseRemoteConfigInfoImpl(
          builderLastSuccessfulFetchTimeInMillis, builderLastFetchStatus, builderConfigSettings);
    }
  }

  static Builder newBuilder() {
    return new Builder();
  }
}
