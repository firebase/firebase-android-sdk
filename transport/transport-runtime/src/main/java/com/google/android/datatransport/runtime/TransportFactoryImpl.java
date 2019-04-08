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

package com.google.android.datatransport.runtime;

import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;

final class TransportFactoryImpl implements TransportFactory {
  private final TransportContext transportContext;
  private final TransportInternal transportInternal;

  TransportFactoryImpl(TransportContext transportContext, TransportInternal transportInternal) {
    this.transportContext = transportContext;
    this.transportInternal = transportInternal;
  }

  @Override
  public <T> Transport<T> getTransport(
      String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
    return new TransportImpl<>(transportContext, name, payloadTransformer, transportInternal);
  }
}
