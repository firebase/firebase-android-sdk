// Copyright 2018 Google LLC
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

package com.google.firebase.platforminfo;
import com.google.auto.value.AutoValue;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The class is not public to ensure other components cannot depend on it. */
@AutoValue
abstract class SDKVersion {
  public static Builder builder() {
    return new AutoValue_SDKVersion.Builder();
  }

  @Nonnull
  public abstract String getSDKName();

  @Nonnull
  public abstract String getVersion();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setSDKName(String sdkName);
    public abstract Builder setVersion(String version);
    public abstract SDKVersion build();
  }
}
