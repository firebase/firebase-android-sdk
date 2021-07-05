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

package com.google.android.datatransport.runtime.backends;

import com.google.auto.value.AutoValue;

/**
 * Encapsulates all upload options to indicate whether data sent through {@link TransportBackend}
 * should be uploaded.
 */
@AutoValue
public abstract class UploadOptions {

  /**
   * It indicates whether data sent through {@link TransportBackend} should be recorded as client
   * health metrics and upload to Flg server.
   */
  public abstract boolean shouldUploadClientHealthMetrics();

  public static UploadOptions none() {
    return UploadOptions.builder().setShouldUploadClientHealthMetrics(false).build();
  }

  public static UploadOptions.Builder builder() {
    return new AutoValue_UploadOptions.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract UploadOptions.Builder setShouldUploadClientHealthMetrics(
        boolean shouldUploadClientHealthMetrics);

    public abstract UploadOptions build();
  }
}
