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

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.firestore.auth.CredentialsProvider;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.util.Executors;
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

  private final CredentialsProvider<User> authProvider;
  private final CredentialsProvider<String> appCheckProvider;

  FirestoreCallCredentials(
      CredentialsProvider<User> authProvider, CredentialsProvider<String> appCheckProvider) {
    this.authProvider = authProvider;
    this.appCheckProvider = appCheckProvider;
  }

  // Don't have @Override to avoid breaking build when method is removed from interface
  @SuppressWarnings("MissingOverride")
  public void thisUsesUnstableApi() {}

  @Override
  public void applyRequestMetadata(
      RequestInfo requestInfo, Executor executor, final MetadataApplier metadataApplier) {
    System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 1");
    Task<String> authTask = authProvider.getToken();
    Task<String> appCheckTask = appCheckProvider.getToken();

    Tasks.whenAll(authTask, appCheckTask)
        .addOnCompleteListener(
            // We previously used the executor that is passed to us by the callee of the method
            // (which
            // happens to be the AsyncQueue). This sometimes led to deadlocks during shutdown, as
            // Firestore's shutdown() runs on the AsyncQueue, which would then invoke GRPC's
            // shutdown(),
            // which would then block until this callback gets schedule on the AsyncQueue.
            Executors.DIRECT_EXECUTOR,
            unused -> {
              System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 2");
              Metadata metadata = new Metadata();

              if (authTask.isSuccessful()) {
                System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 3");
                String token = authTask.getResult();
                Logger.debug(LOG_TAG, "Successfully fetched auth token.");
                if (token != null) {
                  metadata.put(AUTHORIZATION_HEADER, "Bearer " + token);
                }
              } else {
                System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 4");
                Exception exception = authTask.getException();
                if (exception instanceof FirebaseApiNotAvailableException) {
                  Logger.debug(
                      LOG_TAG, "Firebase Auth API not available, not using authentication.");
                } else if (exception instanceof FirebaseNoSignedInUserException) {
                  Logger.debug(LOG_TAG, "No user signed in, not using authentication.");
                } else {
                  System.out.println(
                      "MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 5");
                  Logger.warn(LOG_TAG, "Failed to get auth token: %s.", exception);
                  metadataApplier.fail(Status.UNAUTHENTICATED.withCause(exception));
                  return;
                }
              }

              if (appCheckTask.isSuccessful()) {
                System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 6");
                String token = appCheckTask.getResult();
                if (token != null && !token.isEmpty()) {
                  Logger.debug(LOG_TAG, "Successfully fetched AppCheck token.");
                  metadata.put(X_FIREBASE_APPCHECK, token);
                }
              } else {
                System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 7");
                Exception exception = appCheckTask.getException();
                if (exception instanceof FirebaseApiNotAvailableException) {
                  Logger.debug(LOG_TAG, "Firebase AppCheck API not available.");
                } else {
                  Logger.warn(LOG_TAG, "Failed to get AppCheck token: %s.", exception);
                  metadataApplier.fail(Status.UNAUTHENTICATED.withCause(exception));
                  System.out.println(
                      "MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 8");
                  return;
                }
              }

              System.out.println("MRSCHMIDT - FirestoreCallCredentials.applyRequestMetadata() 9");
              metadataApplier.apply(metadata);
            });
  }
}
