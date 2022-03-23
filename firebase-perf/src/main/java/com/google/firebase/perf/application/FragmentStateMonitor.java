// Copyright 2022 Google LLC
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

package com.google.firebase.perf.application;

import androidx.annotation.NonNull;
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.gms.common.util.VisibleForTesting;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.FrameMetrics;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import java.util.WeakHashMap;

public class FragmentStateMonitor extends FragmentManager.FragmentLifecycleCallbacks {
  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private final WeakHashMap<Fragment, Trace> fragmentToTraceMap = new WeakHashMap<>();
  private final WeakHashMap<Fragment, FrameMetricsCalculator.FrameMetrics> fragmentToMetricsMap =
      new WeakHashMap<>();
  private final Clock clock;
  private final TransportManager transportManager;
  private final AppStateMonitor appStateMonitor;
  private final FrameMetricsAggregator frameMetricsAggregator;

  public FragmentStateMonitor(
      Clock clock,
      TransportManager transportManager,
      AppStateMonitor appStateMonitor,
      FrameMetricsAggregator fma) {
    this.clock = clock;
    this.transportManager = transportManager;
    this.appStateMonitor = appStateMonitor;
    this.frameMetricsAggregator = fma;
  }

  /**
   * Fragment screen trace name is prefix "_st_" concatenates with Fragment's class name.
   *
   * @param fragment fragment object.
   * @return Fragment screen trace name.
   */
  public String getFragmentScreenTraceName(Fragment fragment) {
    return Constants.SCREEN_TRACE_PREFIX + fragment.getClass().getSimpleName();
  }

  @Override
  public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
    super.onFragmentResumed(fm, f);
    // Start Fragment screen trace
    logger.debug("FragmentMonitor %s.onFragmentResumed", f.getClass().getSimpleName());
    Trace fragmentTrace =
        new Trace(getFragmentScreenTraceName(f), transportManager, clock, appStateMonitor);
    fragmentTrace.start();

    if (f.getParentFragment() != null) {
      fragmentTrace.putAttribute(
          Constants.PARENT_FRAGMENT_ATTRIBUTE_KEY,
          f.getParentFragment().getClass().getSimpleName());
    }
    if (f.getActivity() != null) {
      fragmentTrace.putAttribute(
          Constants.ACTIVITY_ATTRIBUTE_KEY, f.getActivity().getClass().getSimpleName());
    }
    fragmentToTraceMap.put(f, fragmentTrace);

    FrameMetrics frameMetrics =
        FrameMetricsCalculator.calculateFrameMetrics(this.frameMetricsAggregator.getMetrics());
    fragmentToMetricsMap.put(f, frameMetrics);
  }

  @Override
  public void onFragmentPaused(@NonNull FragmentManager fm, @NonNull Fragment f) {
    super.onFragmentPaused(fm, f);
    // Stop Fragment screen trace
    logger.debug("FragmentMonitor %s.onFragmentPaused ", f.getClass().getSimpleName());
    if (!fragmentToTraceMap.containsKey(f)) {
      logger.warn("FragmentMonitor: missed a fragment trace from %s", f.getClass().getSimpleName());
      return;
    }

    Trace fragmentTrace = fragmentToTraceMap.get(f);
    fragmentToTraceMap.remove(f);
    FrameMetrics preFrameMetrics = fragmentToMetricsMap.get(f);
    fragmentToMetricsMap.remove(f);

    FrameMetrics curFrameMetrics =
        FrameMetricsCalculator.calculateFrameMetrics(this.frameMetricsAggregator.getMetrics());

    int totalFrames = curFrameMetrics.getTotalFrames() - preFrameMetrics.getTotalFrames();
    int slowFrames = curFrameMetrics.getSlowFrames() - preFrameMetrics.getSlowFrames();
    int frozenFrames = curFrameMetrics.getFrozenFrames() - preFrameMetrics.getFrozenFrames();

    if (totalFrames == 0 && slowFrames == 0 && frozenFrames == 0) {
      // All metrics are zero, no need to send screen trace.
      return;
    }
    // Only putMetric if corresponding metric is non-zero.
    if (totalFrames > 0) {
      fragmentTrace.putMetric(Constants.CounterNames.FRAMES_TOTAL.toString(), totalFrames);
    }
    if (slowFrames > 0) {
      fragmentTrace.putMetric(Constants.CounterNames.FRAMES_SLOW.toString(), slowFrames);
    }
    if (frozenFrames > 0) {
      fragmentTrace.putMetric(Constants.CounterNames.FRAMES_FROZEN.toString(), frozenFrames);
    }

    fragmentTrace.stop();
  }

  @VisibleForTesting
  WeakHashMap<Fragment, Trace> getFragmentToTraceMap() {
    return fragmentToTraceMap;
  }
}
