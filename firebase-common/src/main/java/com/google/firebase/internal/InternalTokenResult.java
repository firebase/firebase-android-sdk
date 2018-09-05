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

package com.google.firebase.internal;

import android.support.annotation.Nullable;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.internal.Objects;

/** Represents an internal token result. */
@KeepForSdk
public class InternalTokenResult {

  private String token;

  @KeepForSdk
  public InternalTokenResult(@Nullable String token) {
    this.token = token;
  }

  @KeepForSdk
  @Nullable
  public String getToken() {
    return token;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(token);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof InternalTokenResult)) {
      return false;
    }
    InternalTokenResult other = (InternalTokenResult) o;
    return Objects.equal(token, other.token);
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this).add("token", token).toString();
  }
}
