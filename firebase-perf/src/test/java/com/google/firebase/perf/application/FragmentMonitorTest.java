package com.google.firebase.perf.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.Lifecycle;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.config.DeviceCacheManager;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.TraceMetric;
import com.google.testing.timing.FakeDirectExecutorService;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/** Unit tests for {@link com.google.firebase.perf.application.FragmentMonitor}. */
@RunWith(RobolectricTestRunner.class)
public class FragmentMonitorTest extends FirebasePerformanceTestBase {

    @Mock private Clock clock;
    @Mock private Fragment mockFragment;
    @Mock private FragmentManager mockfragmentManager;
    @Mock private TransportManager mockTransportManager;
    @Mock private AppCompatActivity mockActivity;

    @Captor
    private ArgumentCaptor<TraceMetric> argTraceMetric;

    private long currentTime = 0;

    private Activity activity1;

    @Before
    public void setUp() {
        currentTime = 0;
        initMocks(this);
        doAnswer((Answer<Timer>) invocationOnMock -> new Timer(currentTime)).when(clock).getTime();

//        activity1 = createFakeActivity(/* isHardwareAccelerated= */ true);

        DeviceCacheManager.clearInstance();
        ConfigResolver.clearInstance();

        ConfigResolver configResolver = ConfigResolver.getInstance();
        configResolver.setDeviceCacheManager(new DeviceCacheManager(new FakeDirectExecutorService()));

    }

    @Test
    public void testFragment() {
        AppStateMonitor appStateMonitor = new AppStateMonitor(mockTransportManager, clock);
        FragmentMonitor fragmentMonitor = new FragmentMonitor(mockActivity, clock, mockTransportManager, appStateMonitor);
        fragmentMonitor.onFragmentStarted(mockfragmentManager, mockFragment);
        verify(mockTransportManager, times(0)).log(any(TraceMetric.class), any());

        fragmentMonitor.onFragmentStopped(mockfragmentManager, mockFragment);
        verify(mockTransportManager, times(2)).log(any(TraceMetric.class), any());
    }

//    private static Activity createFakeActivity(boolean isHardwareAccelerated) {
//        ActivityController<Activity> fakeActivityController = Robolectric.buildActivity(Activity.class);
//
//        if (isHardwareAccelerated) {
//            fakeActivityController.get().getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
//        } else {
//            fakeActivityController.get().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
//        }
//
//        return fakeActivityController.start().get();
//    }


}
