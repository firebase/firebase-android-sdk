package com.google.firebase.perf.application;

import androidx.annotation.NonNull;
import androidx.core.app.FrameMetricsAggregator;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Constants;

public class FragmentMonitor extends FragmentManager.FragmentLifecycleCallbacks {
  private static final AndroidLogger logger = AndroidLogger.getInstance();
  private final FragmentActivity activity;
  private final Clock clock;
  private final TransportManager transportManager;
  private final AppStateMonitor appStateMonitor;
  private final FrameMetricsAggregator frameMetricsAggregator;

  public FragmentMonitor(
      FragmentActivity activity,
      Clock clock,
      TransportManager transportManager,
      AppStateMonitor appStateMonitor,
      FrameMetricsAggregator fma) {
    this.activity = activity;
    this.clock = clock;
    this.transportManager = transportManager;
    this.appStateMonitor = appStateMonitor;
    this.frameMetricsAggregator = fma;
  }

  /**
   * Screen trace name is prefix "_st_" concatenates with Fragment's class name.
   *
   * @param fragment fragment object.
   * @return screen trace name.
   */
  public static String getFragmentScreenTraceName(Fragment fragment) {
    return Constants.SCREEN_TRACE_PREFIX + fragment.getClass().getSimpleName();
  }

  @Override
  public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
    super.onFragmentResumed(fm, f);
    // Start screen trace
    logger.debug("FragmentMonitor %s.onFragmentResumed", f.getClass().getSimpleName());
  }

  @Override
  public void onFragmentPaused(@NonNull FragmentManager fm, @NonNull Fragment f) {
    super.onFragmentPaused(fm, f);
    // Stop screen trace
    logger.debug("FragmentMonitor %s.onFragmentPaused ", f.getClass().getSimpleName());
  }
}
