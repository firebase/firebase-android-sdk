/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * POJO to contain the true Firebase installation id and Firebase authentication token.
 *
 * <p>This is not the Crashlytics installation id.
 */
public class FirebaseInstallationId {
  @Nullable final String fid;
  @Nullable final String authToken;

  FirebaseInstallationId(@Nullable String fid, @Nullable String authToken) {
    this.fid = fid;
    this.authToken = authToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FirebaseInstallationId that = (FirebaseInstallationId) o;
    return Objects.equals(fid, that.fid) && Objects.equals(authToken, that.authToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(fid, authToken);
  }

  @NonNull
  @Override
  public String toString() {
    return "FirebaseInstallationId{"
        + "fid='"
        + fid
        + '\''
        + ", authToken='"
        + authToken
        + '\''
        + '}';
  }
}
