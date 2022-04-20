package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.backends.BackendResponse;

/**
 * Support class that avoids making {@link Uploader#logAndUpdateState(TransportContext, int) }
 * method public in the SDK code.
 */
public class UploadTestSupport {
  private UploadTestSupport() {}

  public static BackendResponse forceUpload(TransportContext context) {
    return TransportRuntime.getInstance().getUploader().logAndUpdateState(context, 1);
  }
}
