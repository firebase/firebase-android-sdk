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
