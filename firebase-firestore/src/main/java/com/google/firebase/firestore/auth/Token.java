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
 * The current User and the authentication token provided by the underlying authentication
 * mechanism. This is the result of calling {@link CredentialsProvider#getToken}.
 *
 * <h4>Porting note: no TokenType on Android</h4>
 *
 * The TypeScript client supports 1st party Oauth tokens (for the Firebase Console to auth as the
 * developer) and OAuth2 tokens for the node.js sdk to auth with a service account. We don't have
 * plans to support either case on mobile so there's no TokenType here.
 */
public final class Token {

  @Nullable private final String value;

  private final User user;

  public Token(@Nullable String value, User user) {
    this.value = value;
    this.user = user;
  }

  /** Returns the actual raw token. */
  @Nullable
  public String getValue() {
    return value;
  }

  /**
   * Returns the user with which the token is associated (used for persisting user state on disk,
   * etc.).
   */
  public User getUser() {
    return user;
  }
}
