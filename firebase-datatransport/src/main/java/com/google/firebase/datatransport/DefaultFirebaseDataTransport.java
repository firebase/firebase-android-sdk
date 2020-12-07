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

package com.google.firebase.datatransport;

import android.content.Context;

import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.UserAgentPublisher;

public class DefaultFirebaseDataTransport implements FirebaseDataTransport {
    Context context;
    Provider<HeartBeatInfo> heartBeatInfo;
    Provider<UserAgentPublisher> userAgentPublisher;
    DefaultFirebaseDataTransport(Context context, Provider<HeartBeatInfo> heartbeatInfo, Provider<UserAgentPublisher> userAgentPublisher) {
        this.context = context;
        this.heartBeatInfo = heartBeatInfo;
        this.userAgentPublisher = userAgentPublisher;
    }

    @Override
    public TransportFactory getFactory(String backendName) {
        TransportRuntime.initialize(context);
        return new FirebaseDataTransportFactory(TransportRuntime.getInstance().newFactory(backendName), heartBeatInfo, userAgentPublisher);
    }
}
