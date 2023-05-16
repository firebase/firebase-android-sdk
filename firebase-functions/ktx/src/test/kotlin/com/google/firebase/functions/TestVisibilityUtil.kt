package com.google.firebase.functions;

/**
 * Returns true if the {@link HttpsCallableReference} is configured to use FAC limited-use tokens.
 */
fun HttpsCallableReference.usesLimitedUseFacTokens() = options.getLimitedUseAppCheckTokens()