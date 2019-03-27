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

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;

class TransportImpl<T> implements Transport<T> {
  private final String backendName;
  private final String name;
  private final Transformer<T, byte[]> transformer;
  private final TransportInternal transportInternal;

  TransportImpl(
      String backendName,
      String name,
      Transformer<T, byte[]> transformer,
      TransportInternal transportInternal) {
    this.backendName = backendName;
    this.name = name;
    this.transformer = transformer;
    this.transportInternal = transportInternal;
  }

  @Override
  public void send(Event<T> event) {
    transportInternal.send(
        SendRequest.builder()
            .setBackendName(backendName)
            .setEvent(event)
            .setTransportName(name)
            .setTransformer(transformer)
            .build());
  }
}
