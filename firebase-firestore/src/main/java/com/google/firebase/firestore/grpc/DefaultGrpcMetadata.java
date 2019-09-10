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

package com.google.firebase.firestore.grpc;

import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.platforminfo.UserAgentPublisher;

import io.grpc.Metadata;

public class DefaultGrpcMetadata implements GrpcMetadata{

    private final HeartBeatInfo heartBeatInfo;
    private final UserAgentPublisher userAgentPublisher;
    private final String firebaseFirestoreHeartBeatTag = "fire-fst";

    private static final Metadata.Key<String> HEART_BEAT_HEADER =
            Metadata.Key.of("x-firebase-client-log-type", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> USER_AGENT_HEADER =
            Metadata.Key.of("x-firebase-client", Metadata.ASCII_STRING_MARSHALLER);

    public DefaultGrpcMetadata(UserAgentPublisher userAgentPublisher, HeartBeatInfo heartBeatInfo) {
        this.userAgentPublisher = userAgentPublisher;
        this.heartBeatInfo = heartBeatInfo;
    }

    @Override
    public void updateMetadata(Metadata metadata) {
        if(this.userAgentPublisher != null) {
            metadata.put(USER_AGENT_HEADER, userAgentPublisher.getUserAgent());
        }
        if(this.heartBeatInfo != null) {
            metadata.put(HEART_BEAT_HEADER, Integer.toString(heartBeatInfo.getHeartBeatCode(firebaseFirestoreHeartBeatTag).getCode()));
        }
    }
}
