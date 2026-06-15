// Copyright 2026 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.metrics;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * OS-reported reason this process was forked, used by {@link AppStartTrace} to decide
 * whether to emit the {@code _app_start} trace.
 *
 * API 34+: {@link ActivityManager#getMyMemoryState} importance.
 *   {@code IMPORTANCE_FOREGROUND} at first capture indicates an activity-driven start.
 *
 * API < 34: returns {@link Cause#UNKNOWN}; legacy logic in {@link AppStartTrace} owns
 *   the decision on these versions.
 *
 * @hide
 */
final class AppStartCause {

  /** Classification of why the process was forked. */
  enum Cause {
    /** Process forked to satisfy an activity launch. */
    FOREGROUND,
    /** Couldn't decide — caller falls back to its own heuristic. */
    UNKNOWN
  }

  /** OS classification. Never null. */
  final @NonNull Cause cause;

  /** {@code RunningAppProcessInfo.importance} at capture, or {@code -1} if unread. */
  final int importance;

  /** {@link Build.VERSION#SDK_INT} at capture. */
  final int apiLevel;

  @VisibleForTesting
  AppStartCause(@NonNull Cause cause, int importance, int apiLevel) {
    this.cause = cause;
    this.importance = importance;
    this.apiLevel = apiLevel;
  }

  /**
   * Capture the cause for the current process. Call as early as possible (during
   * {@code AppStartTrace.registerActivityLifecycleCallbacks}) so the OS-set values still
   * reflect the original fork reason rather than transient state mid-init.
   */
  static @NonNull AppStartCause capture(@Nullable Context appContext) {
    final int apiLevel = Build.VERSION.SDK_INT;
    if (appContext == null) {
      return new AppStartCause(Cause.UNKNOWN, -1, apiLevel);
    }

    final ActivityManager activityManager =
        (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
    if (activityManager == null) {
      return new AppStartCause(Cause.UNKNOWN, -1, apiLevel);
    }

    final int importance = readImportance();

    if (apiLevel >= 34) {
      Cause cause =
          importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
              ? Cause.FOREGROUND
              : Cause.UNKNOWN;
      return new AppStartCause(cause, importance, apiLevel);
    }

    // API < 34: legacy AppStartTrace logic owns the decision.
    return new AppStartCause(Cause.UNKNOWN, importance, apiLevel);
  }

  private static int readImportance() {
    try {
      ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
      ActivityManager.getMyMemoryState(info);
      return info.importance;
    } catch (Throwable t) {
      return -1;
    }
  }
}
