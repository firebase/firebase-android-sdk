package com.google.android.datatransport.runtime;

import android.annotation.SuppressLint;
import androidx.annotation.Discouraged;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.Transport;

@Discouraged(
    message =
        "TransportRuntime is not a realtime delivery system, don't use unless you absolutely must.")
public final class ForcedSender {
  @WorkerThread
  public static void sendBlockingWithPriority(Transport<?> transport, Priority priority) {
    @SuppressLint("DiscouragedApi")
    TransportContext context = getTransportContextOrThrow(transport).withPriority(priority);
    TransportRuntime.getInstance().getUploader().upload(context, 1, () -> {});
  }

  private static TransportContext getTransportContextOrThrow(Transport<?> transport) {
    if (transport instanceof TransportImpl) {
      return ((TransportImpl<?>) transport).getTransportContext();
    }
    throw new IllegalArgumentException("Expected instance of TransportImpl.");
  }
}
