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

package com.google.firebase.perf.session.gauges;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.util.StorageUnit;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.session.gauges.GaugeMetadataManager} */
@RunWith(RobolectricTestRunner.class)
public class GaugeMetadataManagerTest extends FirebasePerformanceTestBase {

  private GaugeMetadataManager testGaugeMetadataManager = null;

  private static final long RUNTIME_MAX_MEMORY_BYTES = StorageUnit.MEGABYTES.toBytes(150);
  private static final long DEVICE_RAM_SIZE_BYTES = StorageUnit.GIGABYTES.toBytes(4);
  private static final long DEVICE_RAM_SIZE_KB = StorageUnit.GIGABYTES.toKilobytes(4);
  private static final int RUNTIME_MAX_ENCOURAGED_MEMORY_MB = 120; // 120 MB

  @Mock private Runtime runtime;
  private ActivityManager activityManager;

  @Before
  public void setUp() {
    initMocks(this);
    Context appContext = ApplicationProvider.getApplicationContext();
    activityManager = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);

    mockMemory();
    testGaugeMetadataManager = new GaugeMetadataManager(runtime, appContext);
  }

  private void mockMemory() {
    // TODO(b/177317586): Unable to mock Runtime class after introduction of "mockito-inline" which
    //  is an incubating feature for mocking final classes.
    // when(runtime.maxMemory()).thenReturn(RUNTIME_MAX_MEMORY_BYTES);

    ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    memoryInfo.totalMem = DEVICE_RAM_SIZE_BYTES;

    shadowOf(activityManager).setMemoryInfo(memoryInfo);
    shadowOf(activityManager).setMemoryClass(RUNTIME_MAX_ENCOURAGED_MEMORY_MB);
  }

  @Test
  public void testGetMaxAppJavaHeapMemory_returnsExpectedValue() {
    assertThat(testGaugeMetadataManager.getMaxAppJavaHeapMemoryKb()).isGreaterThan(0);
    //        .isEqualTo(StorageUnit.BYTES.toKilobytes(RUNTIME_MAX_MEMORY_BYTES));
  }

  @Test
  public void testGetMaxEncouragedAppJavaHeapMemory_returnsExpectedValue() {
    assertThat(testGaugeMetadataManager.getMaxEncouragedAppJavaHeapMemoryKb())
        .isEqualTo(StorageUnit.MEGABYTES.toKilobytes(RUNTIME_MAX_ENCOURAGED_MEMORY_MB));
  }

  @Test
  public void testGetDeviceRamSize_returnsExpectedValue() throws IOException {
    int ramSize = testGaugeMetadataManager.getDeviceRamSizeKb();

    assertThat(ramSize).isEqualTo(StorageUnit.BYTES.toKilobytes(DEVICE_RAM_SIZE_BYTES));
  }
}
