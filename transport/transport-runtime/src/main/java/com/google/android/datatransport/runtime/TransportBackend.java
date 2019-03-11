package com.google.android.datatransport.runtime;

public interface TransportBackend {
  EventInternal decorate(EventInternal event);

  BackendResponse send(Iterable<EventInternal> event);
}
