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
package com.google.firebase.messaging;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.Arrays;
import java.util.List;

/**
 * {@link ComponentRegistrar} for FirebaseMessaging - see
 * go/firebase-components-android-integration-guide for more details
 *
 * @hide
 */
@KeepForSdk
@Keep
public class FirebaseMessagingRegistrar implements ComponentRegistrar {
  @Override
  @Keep
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseMessaging.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(FirebaseInstanceId.class))
            .add(Dependency.required(UserAgentPublisher.class))
            .add(Dependency.required(HeartBeatInfo.class))
            .add(Dependency.optional(TransportFactory.class))
            .add(Dependency.required(FirebaseInstallationsApi.class))
            .factory(
                container ->
                    new FirebaseMessaging(
                        container.get(FirebaseApp.class),
                        container.get(FirebaseInstanceId.class),
                        container.get(UserAgentPublisher.class),
                        container.get(HeartBeatInfo.class),
                        container.get(FirebaseInstallationsApi.class),
                        determineFactory(container.get(TransportFactory.class))))
            .alwaysEager()
            .build(),
        LibraryVersionComponent.create("fire-fcm", BuildConfig.VERSION_NAME));
  }

  @VisibleForTesting
  static TransportFactory determineFactory(TransportFactory realFactory) {
    // in 1p context there is no dependency on firebase-datatransport, so the factory may be
    // missing. Note that it is possible for it to be present if another SDK(e.g. fiam) is used in
    // the 1p app.
    if (realFactory == null
        || !CCTDestination.LEGACY_INSTANCE.getSupportedEncodings().contains(Encoding.of("json"))) {
      return new DevNullTransportFactory();
    }

    return realFactory;
  }

  /** Produces Transports that don't send events anywhere. */
  @VisibleForTesting
  public static class DevNullTransportFactory implements TransportFactory {
    @Override
    public <T> Transport<T> getTransport(
        String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
      return new DevNullTransport<>();
    }

    @Override
    public <T> Transport<T> getTransport(
        String name,
        Class<T> payloadType,
        Encoding payloadEncoding,
        Transformer<T, byte[]> payloadTransformer) {
      return new DevNullTransport<>();
    }
  }

  private static class DevNullTransport<T> implements Transport<T> {
    @Override
    public void send(Event<T> event) {}

    @Override
    public void schedule(Event<T> event, TransportScheduleCallback callback) {
      callback.onSchedule(null);
    }
  }
}
