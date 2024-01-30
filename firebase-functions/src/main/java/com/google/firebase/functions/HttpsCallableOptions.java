// Copyright 2023 Google LLC
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

package com.google.firebase.functions;

import androidx.annotation.NonNull;

/**
 * Options for configuring the callable function.
 *
 * <p>These properties are immutable once a callable function reference is instantiated.
 */
public class HttpsCallableOptions {
  // If true, request a limited-use token from AppCheck.
  private final boolean limitedUseAppCheckTokens;

  private HttpsCallableOptions(boolean limitedUseAppCheckTokens) {
    this.limitedUseAppCheckTokens = limitedUseAppCheckTokens;
  }

  /**
   * Returns the setting indicating if limited-use App Check tokens are enforced for this function.
   */
  public boolean getLimitedUseAppCheckTokens() {
    return limitedUseAppCheckTokens;
  }

  /** Builder class for {@link com.google.firebase.functions.HttpsCallableOptions} */
  public static class Builder {
    private boolean limitedUseAppCheckTokens = false;

    /**
     * Sets whether or not to use limited-use App Check tokens when invoking the associated
     * function.
     */
    @NonNull
    public Builder setLimitedUseAppCheckTokens(boolean limitedUse) {
      limitedUseAppCheckTokens = limitedUse;
      return this;
    }

    /** Returns the setting indicating if limited-use App Check tokens are enforced. */
    public boolean getLimitedUseAppCheckTokens() {
      return limitedUseAppCheckTokens;
    }

    /** Builds a new {@link com.google.firebase.functions.HttpsCallableOptions}. */
    @NonNull
    public HttpsCallableOptions build() {
      return new HttpsCallableOptions(limitedUseAppCheckTokens);
    }
  }
}
