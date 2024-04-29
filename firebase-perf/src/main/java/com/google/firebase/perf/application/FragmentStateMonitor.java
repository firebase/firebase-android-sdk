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
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.metrics.FrameMetricsCalculator.PerfFrameMetrics;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.util.Optional;
import com.google.firebase.perf.util.ScreenTraceUtil;
import java.util.WeakHashMap;

public class FragmentStateMonitor extends FragmentManager.FragmentLifecycleCallbacks {
  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private final WeakHashMap<Fragment, Trace> fragmentToTraceMap = new WeakHashMap<>();
  private final Clock clock;
  private final TransportManager transportManager;
  private final AppStateMonitor appStateMonitor;
  private final FrameMetricsRecorder activityFramesRecorder;

  public FragmentStateMonitor(
      Clock clock,
      TransportManager transportManager,
      AppStateMonitor appStateMonitor,
      FrameMetricsRecorder recorder) {
    this.clock = clock;
    this.transportManager = transportManager;
    this.appStateMonitor = appStateMonitor;
    this.activityFramesRecorder = recorder;
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

    fragmentTrace.putAttribute(
        Constants.PARENT_FRAGMENT_ATTRIBUTE_KEY,
        f.getParentFragment() == null
            ? Constants.PARENT_FRAGMENT_ATTRIBUTE_VALUE_NONE
            : f.getParentFragment().getClass().getSimpleName());
    if (f.getActivity() != null) {
      fragmentTrace.putAttribute(
          Constants.ACTIVITY_ATTRIBUTE_KEY, f.getActivity().getClass().getSimpleName());
    }
    fragmentToTraceMap.put(f, fragmentTrace);
    activityFramesRecorder.startFragment(f);
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

    Optional<PerfFrameMetrics> frameMetricsData = activityFramesRecorder.stopFragment(f);
    if (!frameMetricsData.isAvailable()) {
      logger.warn("onFragmentPaused: recorder failed to trace %s", f.getClass().getSimpleName());
      return;
    }
    ScreenTraceUtil.addFrameCounters(fragmentTrace, frameMetricsData.get());
    fragmentTrace.stop();
  }

  @VisibleForTesting
  WeakHashMap<Fragment, Trace> getFragmentToTraceMap() {
    return fragmentToTraceMap;
  }
}
