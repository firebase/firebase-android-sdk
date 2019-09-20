// Copyright 2019 Google LLC
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

import androidx.annotation.NonNull;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.UserAgentPublisher;
import io.grpc.Metadata;

/**
 * Provides an implementation of the GrpcMetadataProvider interface.
 *
 * <p>This updates the metadata with platformInfo string and the heartBeatInfo code.
 */
public class FirebaseClientGrpcMetadataProvider implements GrpcMetadataProvider {

  private final Provider<HeartBeatInfo> heartBeatInfoProvider;
  private final Provider<UserAgentPublisher> userAgentPublisherProvider;
  private final String firebaseFirestoreHeartBeatTag = "fire-fst";

  private static final Metadata.Key<String> HEART_BEAT_HEADER =
      Metadata.Key.of("x-firebase-client-log-type", Metadata.ASCII_STRING_MARSHALLER);

  private static final Metadata.Key<String> USER_AGENT_HEADER =
      Metadata.Key.of("x-firebase-client", Metadata.ASCII_STRING_MARSHALLER);

  public FirebaseClientGrpcMetadataProvider(
      @NonNull Provider<UserAgentPublisher> userAgentPublisherProvider,
      @NonNull Provider<HeartBeatInfo> heartBeatInfoProvider) {
    this.userAgentPublisherProvider = userAgentPublisherProvider;
    this.heartBeatInfoProvider = heartBeatInfoProvider;
  }

  @Override
  public void updateMetadata(@NonNull Metadata metadata) {
    if (heartBeatInfoProvider.get() == null || userAgentPublisherProvider.get() == null) {
      return;
    }
    int heartBeatCode =
        heartBeatInfoProvider.get().getHeartBeatCode(firebaseFirestoreHeartBeatTag).getCode();
    // Non-zero values indicate some kind of heartbeat should be sent
    if (heartBeatCode != 0) {
      metadata.put(HEART_BEAT_HEADER, Integer.toString(heartBeatCode));
      metadata.put(USER_AGENT_HEADER, userAgentPublisherProvider.get().getUserAgent());
    }
  }
}
