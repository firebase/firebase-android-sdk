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

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.firestore.auth.AppCheckTokenProvider;
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

  private static final Metadata.Key<String> X_FIREBASE_APPCHECK =
      Metadata.Key.of("x-firebase-appcheck", Metadata.ASCII_STRING_MARSHALLER);

  private final CredentialsProvider authProvider;
  private final AppCheckTokenProvider appCheckTokenProvider;

  FirestoreCallCredentials(
      CredentialsProvider authProvider, AppCheckTokenProvider appCheckTokenProvider) {
    this.authProvider = authProvider;
    this.appCheckTokenProvider = appCheckTokenProvider;
  }

  // Don't have @Override to avoid breaking build when method is removed from interface
  @SuppressWarnings("MissingOverride")
  public void thisUsesUnstableApi() {}

  @Override
  public void applyRequestMetadata(
      RequestInfo requestInfo, Executor executor, final MetadataApplier metadataApplier) {
    authProvider
        .getToken()
        .continueWithTask(
            executor,
            task -> {
              Metadata metadata = new Metadata();
              if (task.isSuccessful()) {
                String token = task.getResult();
                Logger.debug(LOG_TAG, "Successfully fetched token.");
                if (token != null) {
                  metadata.put(AUTHORIZATION_HEADER, "Bearer " + token);
                }
                return Tasks.forResult(metadata);
              } else {
                Exception exception = task.getException();
                if (exception instanceof FirebaseApiNotAvailableException) {
                  Logger.debug(
                      LOG_TAG, "Firebase Auth API not available, not using authentication.");
                  return Tasks.forResult(metadata);
                } else if (exception instanceof FirebaseNoSignedInUserException) {
                  Logger.debug(LOG_TAG, "No user signed in, not using authentication.");
                  return Tasks.forResult(metadata);
                } else {
                  Logger.warn(LOG_TAG, "Failed to get auth token: %s.", exception);
                  return Tasks.forException(exception);
                }
              }
            })
        .addOnFailureListener(
            executor,
            exception -> {
              // If the auth task has failed, there is no point in getting the AppCheck token.
              metadataApplier.fail(Status.UNAUTHENTICATED.withCause(exception));
            })
        .addOnSuccessListener(
            executor,
            metadata -> {
              // If the auth task has succeeded, try to get the AppCheck token as well.
              appCheckTokenProvider
                  .getToken()
                  .addOnSuccessListener(
                      executor,
                      appCheckToken -> {
                        if (appCheckToken != null && !appCheckToken.isEmpty()) {
                          metadata.put(X_FIREBASE_APPCHECK, appCheckToken);
                        }
                        metadataApplier.apply(metadata);
                      })
                  .addOnFailureListener(
                      executor,
                      exception -> {
                        if (exception instanceof FirebaseApiNotAvailableException) {
                          Logger.debug(LOG_TAG, "Firebase AppCheck API not available.");
                          metadataApplier.apply(metadata);
                        } else {
                          Logger.warn(LOG_TAG, "Failed to get AppCheck token: %s.", exception);
                          metadataApplier.fail(Status.UNAUTHENTICATED.withCause(exception));
                        }
                      });
            });
  }
}
