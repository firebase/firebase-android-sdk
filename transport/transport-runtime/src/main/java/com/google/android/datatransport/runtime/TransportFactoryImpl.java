package com.google.android.datatransport.runtime;

import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;

public final class TransportFactoryImpl implements TransportFactory {
  private String backendName;
  private final TransportInternal transportInternal;

  public TransportFactoryImpl(String backendName, TransportInternal transportInternal) {
    this.backendName = backendName;
    this.transportInternal = transportInternal;
  }

  @Override
  public <T> Transport<T> getTransport(
      String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer) {
    return new TransportImpl<>(backendName, name, payloadTransformer, transportInternal);
  }
}
