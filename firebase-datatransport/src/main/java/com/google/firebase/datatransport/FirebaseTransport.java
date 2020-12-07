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

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportScheduleCallback;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.heartbeatinfo.HeartBeatResult;
import com.google.firebase.inject.Provider;
import com.google.firebase.platforminfo.UserAgentPublisher;

public class FirebaseTransport implements Transport {
    Transport platformLogTransport;
    Transport sdkTransport;
    Provider<HeartBeatInfo> heartBeatInfo;
    Provider<UserAgentPublisher> userAgentPublisher;

    FirebaseTransport(Transport platformLogTransport, Transport sdkTransport, Provider<HeartBeatInfo> heartBeatInfo, Provider<UserAgentPublisher> userAgentPublisher) {
        this.platformLogTransport = platformLogTransport;
        this.sdkTransport = sdkTransport;
        this.userAgentPublisher = userAgentPublisher;
        this.heartBeatInfo = heartBeatInfo;
    }

    @Override
    public void send(Event event){
        schedule(event, (e)->{});
    }

    @Override
    public void schedule(Event event, TransportScheduleCallback callback) {
        HeartBeatInfo info = this.heartBeatInfo.get();
        UserAgentPublisher agentPublisher = this.userAgentPublisher.get();
        this.sdkTransport.schedule(event, callback);
    }

}
