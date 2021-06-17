// Copyright 2021 Google LLC
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
package com.google.firebase.dynamiclinks.internal;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.internal.Preconditions;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Simple activity for use by API tests that may require an activity context and connection
 * resolution.
 */
public final class ApiTestActivity extends Activity {

  private static final int REQUEST_RESOLVE_FAILURE = 777;
  private static final ResolutionFailedException NO_FAILURE = new ResolutionFailedException("");

  private final Semaphore mResolutionComplete = new Semaphore(0);
  private final AtomicReference<ResolutionFailedException> mResolutionFailure =
      new AtomicReference<>(NO_FAILURE);

  /** Blocks until the resolution completes, or the timeout is reached. */
  public void resolve(final ConnectionResult result, long timeout, TimeUnit unit)
      throws TimeoutException, InterruptedException, ResolutionFailedException {
    Preconditions.checkNotMainThread("Must not be called on the main thread");
    Preconditions.checkArgument(result.hasResolution(), "ConnectionResult must have a resolution");

    runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            try {
              result.startResolutionForResult(ApiTestActivity.this, REQUEST_RESOLVE_FAILURE);
            } catch (IntentSender.SendIntentException e) {
              mResolutionFailure.set(
                  new ResolutionFailedException("Starting resolution failed", e));
              mResolutionComplete.release();
            }
          }
        });

    if (!mResolutionComplete.tryAcquire(timeout, unit)) {
      throw new TimeoutException("Timed out waiting for resolution to complete");
    }

    ResolutionFailedException resolutionFailure = mResolutionFailure.getAndSet(NO_FAILURE);
    if (resolutionFailure != NO_FAILURE) {
      throw resolutionFailure;
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_RESOLVE_FAILURE) {
      if (resultCode != RESULT_OK) {
        mResolutionFailure.set(
            new ResolutionFailedException("Resolution failed with result code: " + resultCode));
      }

      mResolutionComplete.release();
    }
  }

  /** Thrown when a connection resolution fails. */
  public static class ResolutionFailedException extends Exception {

    public ResolutionFailedException(String message) {
      super(message);
    }

    public ResolutionFailedException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
