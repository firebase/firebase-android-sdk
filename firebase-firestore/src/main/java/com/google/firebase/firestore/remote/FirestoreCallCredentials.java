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

package com.google.firebase.firestore.remote;

import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.util.Logger;
import com.google.firebase.internal.api.FirebaseNoSignedInUserException;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.concurrent.Executor;

/** CallCredentials that applies any authorization headers. */
final class FirestoreCallCredentials extends CallCredentials {

  private static final String LOG_TAG = "FirestoreCallCredentials";

  private static final Metadata.Key<String> AUTHORIZATION_HEADER =
      Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final CredentialsProvider credentialsProvider;

  FirestoreCallCredentials(CredentialsProvider provider) {
    credentialsProvider = provider;
  }

  // Don't have @Override to avoid breaking build when method is removed from interface
  @SuppressWarnings("MissingOverride")
  public void thisUsesUnstableApi() {}

  @Override
  public void applyRequestMetadata(
      RequestInfo requestInfo, Executor executor, final MetadataApplier metadataApplier) {
    credentialsProvider
        .getToken()
        .addOnSuccessListener(
            executor,
            token -> {
              Logger.debug(LOG_TAG, "Successfully fetched token.");
              Metadata metadata = new Metadata();
              if (token != null) {
                metadata.put(AUTHORIZATION_HEADER, "Bearer " + token);
              }
              metadataApplier.apply(metadata);
            })
        .addOnFailureListener(
            executor,
            exception -> {
              if (exception instanceof FirebaseApiNotAvailableException) {
                Logger.debug(LOG_TAG, "Firebase Auth API not available, not using authentication.");
                metadataApplier.apply(new Metadata());
              } else if (exception instanceof FirebaseNoSignedInUserException) {
                Logger.debug(LOG_TAG, "No user signed in, not using authentication.");
                metadataApplier.apply(new Metadata());
              } else {
                Logger.warn(LOG_TAG, "Failed to get token: %s.", exception);
                metadataApplier.fail(Status.UNAUTHENTICATED.withCause(exception));
              }
            });
  }
}
