package com.google.firebase.functions;

/** Exposes fields for testing from Kotlin, which is in a different package */
public final class TestVisibilityUtil {
  private TestVisibilityUtil() {}

  /**
   * Returns true if the {@link HttpsCallableReference} is configured to use FAC limited-use tokens.
   */
  public static boolean refUsesLimitedUseFacTokens(HttpsCallableReference ref) {
    return ref.options.getLimitedUseAppCheckTokens();
  }
}
