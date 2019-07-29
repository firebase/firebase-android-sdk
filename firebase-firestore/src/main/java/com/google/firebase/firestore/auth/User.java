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

package com.google.firebase.firestore.auth;

import androidx.annotation.Nullable;

/**
 * Simple wrapper around a nullable UID. Mostly exists to make code more readable and for use as a
 * key in maps (since keys cannot be null).
 */
public final class User {

  /** A User with a null UID. */
  public static final User UNAUTHENTICATED = new User(null);

  // Porting note: no GOOGLE_CREDENTIALS or FIRST_PARTY on Android, see Token for more details.

  private final @Nullable String uid;

  public User(@Nullable String uid) {
    this.uid = uid;
  }

  public @Nullable String getUid() {
    return uid;
  }

  public boolean isAuthenticated() {
    return uid != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    User user = (User) o;
    return uid != null ? uid.equals(user.uid) : user.uid == null;
  }

  @Override
  public int hashCode() {
    return uid != null ? uid.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "User(uid:" + uid + ")";
  }
}
