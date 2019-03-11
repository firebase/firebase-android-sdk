package com.google.android.datatransport.runtime;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;

class TransportImpl<T> implements Transport<T> {
  private String backendName;
  private String name;
  private Transformer<T, byte[]> transformer;
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
