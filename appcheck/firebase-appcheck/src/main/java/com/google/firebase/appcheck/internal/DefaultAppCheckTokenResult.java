// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;
import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.FirebaseException;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.AppCheckTokenResult;

/** Default implementation for {@link AppCheckTokenResult} */
public final class DefaultAppCheckTokenResult extends AppCheckTokenResult {
  // The base64 URL-safe encoded JSON object "{"error":"UNKNOWN_ERROR"}"
  @VisibleForTesting static final String DUMMY_TOKEN = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ==";

  private final String token;
  private final FirebaseException error;

  @NonNull
  public static DefaultAppCheckTokenResult constructFromAppCheckToken(
      @NonNull AppCheckToken token) {
    checkNotNull(token);
    return new DefaultAppCheckTokenResult(token.getToken(), /* error= */ null);
  }

  @NonNull
  public static DefaultAppCheckTokenResult constructFromError(@NonNull FirebaseException error) {
    return new DefaultAppCheckTokenResult(DUMMY_TOKEN, checkNotNull(error));
  }

  private DefaultAppCheckTokenResult(@NonNull String tokenJwt, @Nullable FirebaseException error) {
    checkNotEmpty(tokenJwt);
    this.token = tokenJwt;
    this.error = error;
  }

  @NonNull
  @Override
  public String getToken() {
    return token;
  }

  @Nullable
  @Override
  public Exception getError() {
    return error;
  }
}
