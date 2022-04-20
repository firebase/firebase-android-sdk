package com.google.android.datatransport.runtime;

import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.UploadTestSupport;

/** Test support for {@link TransportRuntime}. */
public final class TransportRuntimeTesting {
  private TransportRuntimeTesting() {}

  /** Synchronously force upload all scheduled events for a given destination and priority. */
  public static BackendResponse forceUpload(Destination destination, Priority priority) {
    return UploadTestSupport.forceUpload(
        TransportContext.builder()
            .setBackendName(destination.getName())
            .setExtras(destination.getExtras())
            .setPriority(priority)
            .build());
  }
}
