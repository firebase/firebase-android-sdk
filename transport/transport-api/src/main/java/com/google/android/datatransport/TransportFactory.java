package com.google.android.datatransport;

/** Each factory is backed by a single transport backend. */
public interface TransportFactory {
  /**
   * Returns a named transport instance that can be used to send values of type T.
   *
   * @param name name of the transport.
   * @param payloadType The type that is sendable by the returned {@link Transport}.
   * @param payloadTransformer Transformer that converts values of T to byte arrays.
   */
  <T> Transport<T> getTransport(
      String name, Class<T> payloadType, Transformer<T, byte[]> payloadTransformer);
}
