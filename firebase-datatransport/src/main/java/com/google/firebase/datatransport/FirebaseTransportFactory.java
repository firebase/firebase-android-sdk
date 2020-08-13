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
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntime;

final class FirebaseTransportFactory implements TransportFactory {

  private static final String PLATFORM_LOGGING_SOURCE_NAME = "PLATFORM_LOGGING";
  private TransportFactory transportFactory;

  FirebaseTransportFactory(Context context) {
    TransportRuntime.initialize(context);
    transportFactory = TransportRuntime.getInstance().newFactory(CCTDestination.LEGACY_INSTANCE);
  }

  @Override
  public <T> Transport<T> getTransport(
      String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
    return getTransport(name, payloadType, Encoding.of("proto"), payloadTransformer);
  }

  @Override
  public <T> Transport<T> getTransport(
      String name,
      Class<T> payloadType,
      Encoding payloadEncoding,
      Transformer<T, byte[]> payloadTransformer) {
    //    transportFactory.getTransport(
    //        PLATFORM_LOGGING_SOURCE_NAME,
    //        FirebasePlatformInfo.class,
    //        FirebasePlatformInfo::toByteArray);
    return transportFactory.getTransport(name, payloadType, payloadEncoding, payloadTransformer);
  }
}
