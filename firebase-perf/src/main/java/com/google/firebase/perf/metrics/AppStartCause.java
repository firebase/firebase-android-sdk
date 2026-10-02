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
 * Note that the importance read itself is NOT version-gated — {@code getMyMemoryState} is
 * available since API 16 and {@link #importance} is recorded on every API level. Only the
 * classification is gated; see {@link #capture} for why. See
 * https://github.com/firebase/firebase-android-sdk/issues/8509.
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
   *
   * <p>{@link #importance} is read on every API level, but only API 34+ classifies on it.
   * The gate is a deliberate scoping of risk, not an API-availability limit:
   *
   * <ul>
   *   <li>There is no pre-API-34 defect to fix. The bug in #8103 is an API-34+ ordering
   *       change (the OS drains the posted main-thread runnable before delivering the
   *       activity-launch transaction); below 34 the legacy ordering check in
   *       {@link AppStartTrace} still classifies correctly.
   *   <li>The two signals are not equivalent. The legacy check is a relative ordering
   *       evaluated at the first {@code onActivityCreated}; this one is a single sample
   *       taken during ContentProvider init. {@code PROCESS_STATE_BOUND_TOP} maps to
   *       {@code IMPORTANCE_FOREGROUND}, so a process forked because a foreground app
   *       bound one of its services samples FOREGROUND here — and a warm start that
   *       follows would be admitted as {@code _app_start}, which the pre-34 path
   *       suppresses today.
   *   <li>Suppression driven by this signal fails silently. A wrong sample on a genuine
   *       launcher tap drops {@code _app_start} with no error surface; the legacy check
   *       fails the other way (ambiguity keeps the trace). On API 34+ that trade beat a
   *       total loss of the trace, which is not the situation below 34.
   *   <li>The procState-to-importance mapping is not one function across the pre-34 range:
   *       {@code procStateToImportanceForTargetSdk} returns the {@code *_PRE_26} /
   *       {@code *_PRE_28} constants for apps targeting below API 26, and pre-Oreo devices
   *       predate the background-execution limits. minSdk here is 23.
   *   <li>No production data backs the swap on that population. Extending the signal
   *       downward warrants shadow-comparing it against the legacy decision across the
   *       pre-34 fleet first, which is why {@link #importance} is recorded even where it
   *       is not acted on.
   * </ul>
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

    // API < 34: legacy AppStartTrace logic owns the decision. `importance` is still
    // recorded above so the two signals can be compared before any future tier flip.
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
