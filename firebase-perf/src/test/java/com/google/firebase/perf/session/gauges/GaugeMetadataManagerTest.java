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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.robolectric.Shadows.shadowOf;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Environment;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.util.StorageUnit;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowEnvironment;

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
  private Context appContext;

  @Before
  public void setUp() {
    initMocks(this);
    appContext = ApplicationProvider.getApplicationContext();
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
    assertThat(ramSize).isEqualTo(testGaugeMetadataManager.readTotalRAM(createFakeMemInfoFile()));
  }

  /** @return The file path of this fake file which can be used to read the file. */
  private String createFakeMemInfoFile() throws IOException {
    // Due to file permission issues on forge, it's easiest to just write this file to the emulated
    // robolectric external storage.
    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);

    File file = new File(Environment.getExternalStorageDirectory(), "FakeProcMemInfoFile");
    Writer fileWriter;

    fileWriter = Files.newBufferedWriter(file.toPath(), UTF_8);
    fileWriter.write(MEM_INFO_CONTENTS);
    fileWriter.close();

    return file.getAbsolutePath();
  }

  private static final String MEM_INFO_CONTENTS =
      "MemTotal:        "
          + DEVICE_RAM_SIZE_KB
          + " kB\n"
          + "MemFree:          542404 kB\n"
          + "MemAvailable:    1392324 kB\n"
          + "Buffers:           64292 kB\n"
          + "Cached:           826180 kB\n"
          + "SwapCached:         4196 kB\n"
          + "Active:           934768 kB\n"
          + "Inactive:         743812 kB\n"
          + "Active(anon):     582132 kB\n"
          + "Inactive(anon):   241500 kB\n"
          + "Active(file):     352636 kB\n"
          + "Inactive(file):   502312 kB\n"
          + "Unevictable:        5148 kB\n"
          + "Mlocked:             256 kB\n"
          + "SwapTotal:        524284 kB\n"
          + "SwapFree:         484800 kB\n"
          + "Dirty:                 4 kB\n"
          + "Writeback:             0 kB\n"
          + "AnonPages:        789404 kB\n"
          + "Mapped:           241928 kB\n"
          + "Shmem:             30632 kB\n"
          + "Slab:             122320 kB\n"
          + "SReclaimable:      42552 kB\n"
          + "SUnreclaim:        79768 kB\n"
          + "KernelStack:       22816 kB\n"
          + "PageTables:        35344 kB\n"
          + "NFS_Unstable:          0 kB\n"
          + "Bounce:                0 kB\n"
          + "WritebackTmp:          0 kB\n"
          + "CommitLimit:     2042280 kB\n"
          + "Committed_AS:   76623352 kB\n"
          + "VmallocTotal:   251658176 kB\n"
          + "VmallocUsed:      232060 kB\n"
          + "VmallocChunk:   251347444 kB\n"
          + "NvMapMemFree:      48640 kB\n"
          + "NvMapMemUsed:     471460 kB\n";
}
