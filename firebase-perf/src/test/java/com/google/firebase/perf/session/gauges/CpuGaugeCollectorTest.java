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

import android.os.Environment;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.v1.CpuMetricReading;
import com.google.testing.timing.FakeScheduledExecutorService;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowEnvironment;

/** Unit tests for {@link com.google.firebase.perf.session.gauges.CpuGaugeCollector} */
@RunWith(RobolectricTestRunner.class)
public final class CpuGaugeCollectorTest {
  private static final long MICROSECONDS_PER_SECOND = TimeUnit.SECONDS.toMicros(1);

  private CpuGaugeCollector testGaugeCollector = null;
  private FakeScheduledExecutorService fakeScheduledExecutorService = null;
  private String fakeProcFile = null;
  private final long fakeClockTicksPerSecond = 100;

  @Before
  public void setUp() throws IOException {
    fakeScheduledExecutorService = new FakeScheduledExecutorService();
    fakeProcFile = createFakeFileToEmulateProcPidStat("100", "100", "100", "100");
    testGaugeCollector =
        new CpuGaugeCollector(fakeScheduledExecutorService, fakeProcFile, fakeClockTicksPerSecond);
  }

  @After
  public void tearDown() {
    deleteFakeProcFile();
  }

  @Test
  public void testStartCollectingAddsCpuMetricReadingsToTheConcurrentLinkedQueue()
      throws Exception {
    testGaugeCollector.startCollecting(100, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.cpuMetricReadings).hasSize(1);
  }

  @Test
  public void testStartCollectingHasCorrectInterval() {
    testGaugeCollector.startCollecting(/* cpuMetricCollectionRateMs= */ 500, new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(500);

    testGaugeCollector.collectOnce(new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(500);

    testGaugeCollector.startCollecting(/* cpuMetricCollectionRateMs= */ 200, new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(200);

    testGaugeCollector.collectOnce(new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(200);
  }

  @Test
  public void testStopCollectingCancelsFutureTasks() {
    testGaugeCollector.startCollecting(500, new Timer());
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS)).isEqualTo(0);
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(fakeScheduledExecutorService.getDelayToNextTask(TimeUnit.MILLISECONDS))
        .isEqualTo(500);

    assertThat(fakeScheduledExecutorService.isEmpty()).isFalse();
    testGaugeCollector.stopCollecting();
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testStartCollectingDoesntAddAnythingToQueueWhenReadingProcPidStatFileFails() {
    deleteFakeProcFile();
    testGaugeCollector.startCollecting(500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.cpuMetricReadings).hasSize(0);
  }

  @Test
  public void testStartCollection_doesNotAddAnythingToQueueWhenFileNotFound() {
    deleteFakeProcFile();
    testGaugeCollector.startCollecting(500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.cpuMetricReadings).hasSize(0);
  }

  @Test
  public void testCollectingCpuMetricHasCorrectValuesFromProcPidStatFile() throws IOException {
    testGaugeCollector.startCollecting(500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    deleteFakeProcFile();
    createFakeFileToEmulateProcPidStat("200", "200", "400", "400");
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    CpuMetricReading recordedReadingOne = testGaugeCollector.cpuMetricReadings.poll();
    CpuMetricReading recordedReadingTwo = testGaugeCollector.cpuMetricReadings.poll();

    assertThat(recordedReadingOne.getSystemTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(200, fakeClockTicksPerSecond));
    assertThat(recordedReadingOne.getUserTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(200, fakeClockTicksPerSecond));

    assertThat(recordedReadingTwo.getSystemTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(800, fakeClockTicksPerSecond));
    assertThat(recordedReadingTwo.getUserTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(400, fakeClockTicksPerSecond));
  }

  @Test
  public void
      testCollectingCpuMetricHasCorrectValuesFromProcPidStatFileDifferentClockTicksPerSecond()
          throws IOException {
    final long differentClockTicksPerSecond = 200;
    testGaugeCollector =
        new CpuGaugeCollector(
            fakeScheduledExecutorService, fakeProcFile, differentClockTicksPerSecond);
    testGaugeCollector.startCollecting(500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    deleteFakeProcFile();
    createFakeFileToEmulateProcPidStat("200", "200", "400", "400");
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();

    CpuMetricReading recordedReadingOne = testGaugeCollector.cpuMetricReadings.poll();
    CpuMetricReading recordedReadingTwo = testGaugeCollector.cpuMetricReadings.poll();

    assertThat(recordedReadingOne.getSystemTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(200, differentClockTicksPerSecond));
    assertThat(recordedReadingOne.getUserTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(200, differentClockTicksPerSecond));

    assertThat(recordedReadingTwo.getSystemTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(800, differentClockTicksPerSecond));
    assertThat(recordedReadingTwo.getUserTimeUs())
        .isEqualTo(convertClockTicksToMicroseconds(400, differentClockTicksPerSecond));
  }

  @Test
  public void testDoesntCollectAnyDataWhenInvalidValueForCpuClockTicksPerSecond() {
    final long invalidClockTicksPerSecond = -1;
    testGaugeCollector =
        new CpuGaugeCollector(
            fakeScheduledExecutorService, fakeProcFile, invalidClockTicksPerSecond);

    testGaugeCollector.startCollecting(100, new Timer());
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testDoesntCollectAnyDataWhenCpuClockTicksPerSecondIsZero() {
    final long invalidClockTicksPerSecond = 0;
    testGaugeCollector =
        new CpuGaugeCollector(
            fakeScheduledExecutorService, fakeProcFile, invalidClockTicksPerSecond);

    testGaugeCollector.startCollecting(100, new Timer());
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testCollectingCpuMetricDoesntAddAnythingToQueueWhenCannotParseIntegersFromFile()
      throws IOException {
    deleteFakeProcFile();
    createFakeFileToEmulateProcPidStat("NaN", "NaN", "NaN", "NaN");

    testGaugeCollector.startCollecting(500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.cpuMetricReadings).hasSize(0);
  }

  @Test
  public void
      testCollectingCpuMetricDoesntAddAnythingToQueueWhenItEncountersProcFileWithInsufficientData()
          throws IOException {
    deleteFakeProcFile();
    createFakeFileWithContents("INVALID_CONTENT");

    testGaugeCollector.startCollecting(500, new Timer());
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.cpuMetricReadings).hasSize(0);
  }

  @Test
  public void testCollectCpuMetricContainsApproximatelyCorrectTimestamp() {
    Timer testTimer = new Timer();
    testGaugeCollector.startCollecting(100, testTimer);
    long beforeTimestampUs = testTimer.getCurrentTimestampMicros();
    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    long afterTimestampUs = testTimer.getCurrentTimestampMicros();

    CpuMetricReading metricReading = testGaugeCollector.cpuMetricReadings.poll();
    assertThat(metricReading.getClientTimeUs()).isAtLeast(beforeTimestampUs);
    assertThat(metricReading.getClientTimeUs()).isAtMost(afterTimestampUs);
  }

  @Test
  public void testCollectCpuMetricDoesntStartCollectingWithInvalidCpuMetricCollectionRate() {
    testGaugeCollector.startCollecting(-1, new Timer());
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();

    testGaugeCollector.startCollecting(0, new Timer());
    assertThat(fakeScheduledExecutorService.isEmpty()).isTrue();
  }

  @Test
  public void testCollectOnce_addOnlyOneCpuMetricReadingToQueue() {
    assertThat(testGaugeCollector.cpuMetricReadings).isEmpty();
    testGaugeCollector.collectOnce(new Timer());

    fakeScheduledExecutorService.simulateSleepExecutingAtMostOneTask();
    assertThat(testGaugeCollector.cpuMetricReadings).hasSize(1);
  }

  @Test
  public void testInvalidFrequency() {
    // Verify with -ve value
    assertThat(CpuGaugeCollector.isInvalidCollectionFrequency(-1)).isTrue();

    // Verify with 0
    assertThat(CpuGaugeCollector.isInvalidCollectionFrequency(0)).isTrue();

    // Verify with +ve value
    assertThat(CpuGaugeCollector.isInvalidCollectionFrequency(1)).isFalse();
  }

  /** @return The file path of this fake file which can be used to read the file. */
  private String createFakeFileWithContents(String fileContent) throws IOException {
    // Due to file permission issues on forge, it's easiest to just write this file to the emulated
    // robolectric external storage.
    ShadowEnvironment.setExternalStorageState(Environment.MEDIA_MOUNTED);

    File file =
        new File(Environment.getExternalStorageDirectory(), "CPUGaugeCollectorTestFakeProcFile");
    Writer fileWriter;

    fileWriter = Files.newBufferedWriter(file.toPath(), UTF_8);
    fileWriter.write(fileContent);
    fileWriter.close();
    return file.getAbsolutePath();
  }

  /** @return The file path of this fake file which can be used to read the file. */
  private String createFakeFileToEmulateProcPidStat(
      String utime, String cutime, String stime, String cstime) throws IOException {
    return createFakeFileWithContents(procFileContents(utime, cutime, stime, cstime));
  }

  private void deleteFakeProcFile() {
    File file = new File(fakeProcFile);
    file.delete();
  }

  /**
   * Returns a correctly formatted proc/[pid]/stat file with the provided params in the right
   * position.
   *
   * @param utime The amount of time spent by the process in user space.
   * @param cutime The amount of time spent by the process' children in user space.
   * @param stime The amount of time spent by the process in kernel space.
   * @param cstime The amount of time spent by the process' children in the kernel space.
   * @return String containing the proc/[pid]/stat file contents.
   */
  private String procFileContents(String utime, String cutime, String stime, String cstime) {
    final int utimePositionInProcPidStat = 13;
    final int stimePositionInProcPidStat = 14;
    final int cutimePositionInProcPidStat = 15;
    final int cstimePositionInProcPidStat = 16;

    StringBuilder procFileStringBuilder = new StringBuilder();
    procFileStringBuilder.append("1234 (someversionofprocessname)");
    Random rand = new Random();

    int minNumberOfValuesWeExpectToSeeInFile = 20;
    for (int i = 2; i < minNumberOfValuesWeExpectToSeeInFile; ++i) {
      switch (i) {
        case utimePositionInProcPidStat:
          procFileStringBuilder.append(" ");
          procFileStringBuilder.append(utime);
          break;
        case stimePositionInProcPidStat:
          procFileStringBuilder.append(" ");
          procFileStringBuilder.append(stime);
          break;
        case cutimePositionInProcPidStat:
          procFileStringBuilder.append(" ");
          procFileStringBuilder.append(cutime);
          break;
        case cstimePositionInProcPidStat:
          procFileStringBuilder.append(" ");
          procFileStringBuilder.append(cstime);
          break;
        default:
          procFileStringBuilder.append(" ");
          procFileStringBuilder.append(rand.nextInt());
          break;
      }
    }

    return procFileStringBuilder.toString();
  }

  private long convertClockTicksToMicroseconds(long clockTicks, long clockTicksPerSecond) {
    return Math.round(((double) clockTicks / clockTicksPerSecond) * MICROSECONDS_PER_SECOND);
  }
}
