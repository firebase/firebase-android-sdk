// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import com.google.firebase.crashlytics.BuildConfig;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Architecture;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.BinaryImage;
import com.google.firebase.crashlytics.internal.model.ImmutableList;
import com.google.firebase.crashlytics.internal.stacktrace.StackTraceTrimmingStrategy;
import com.google.firebase.crashlytics.internal.stacktrace.TrimmedThrowableData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * This class is responsible for capturing information from the system and exception objects,
 * parsing them, and returning canonical CrashlyticsReport and Event objects.
 */
public class CrashlyticsReportDataCapture {

  private static final String GENERATOR =
      String.format(Locale.US, "Crashlytics Android SDK/%s", BuildConfig.VERSION_NAME);

  // GeneratorType ANDROID_SDK = 3;
  private static final int GENERATOR_TYPE = 3;

  private static final int REPORT_ANDROID_PLATFORM = 4;
  private static final int SESSION_ANDROID_PLATFORM = 3;
  private static final String SIGNAL_DEFAULT = "0";

  private static final Map<String, Integer> ARCHITECTURES_BY_NAME = new HashMap<>();

  static {
    ARCHITECTURES_BY_NAME.put("armeabi", Architecture.ARMV6);
    ARCHITECTURES_BY_NAME.put("armeabi-v7a", Architecture.ARMV7);
    ARCHITECTURES_BY_NAME.put("arm64-v8a", Architecture.ARM64);
    ARCHITECTURES_BY_NAME.put("x86", Architecture.X86_32);
    ARCHITECTURES_BY_NAME.put("x86_64", Architecture.X86_64);
  }

  private final Context context;
  private final IdManager idManager;
  private final AppData appData;
  private final StackTraceTrimmingStrategy stackTraceTrimmingStrategy;

  public CrashlyticsReportDataCapture(
      Context context,
      IdManager idManager,
      AppData appData,
      StackTraceTrimmingStrategy stackTraceTrimmingStrategy) {
    this.context = context;
    this.idManager = idManager;
    this.appData = appData;
    this.stackTraceTrimmingStrategy = stackTraceTrimmingStrategy;
  }

  public CrashlyticsReport captureReportData(String identifier, long timestamp) {
    return buildReportData().setSession(populateSessionData(identifier, timestamp)).build();
  }

  public CrashlyticsReport captureReportData() {
    return buildReportData().build();
  }

  public Event captureEventData(
      Throwable event,
      Thread eventThread,
      String type,
      long timestamp,
      int eventThreadImportance,
      int maxChainedExceptions,
      boolean includeAllThreads) {
    final int orientation = context.getResources().getConfiguration().orientation;
    final TrimmedThrowableData trimmedEvent =
        new TrimmedThrowableData(event, stackTraceTrimmingStrategy);

    return Event.builder()
        .setType(type)
        .setTimestamp(timestamp)
        .setApp(
            populateEventApplicationData(
                orientation,
                trimmedEvent,
                eventThread,
                eventThreadImportance,
                maxChainedExceptions,
                includeAllThreads))
        .setDevice(populateEventDeviceData(orientation))
        .build();
  }

  private CrashlyticsReport.Builder buildReportData() {
    return CrashlyticsReport.builder()
        .setSdkVersion(BuildConfig.VERSION_NAME)
        .setGmpAppId(appData.googleAppId)
        .setInstallationUuid(idManager.getCrashlyticsInstallId())
        .setBuildVersion(appData.versionCode)
        .setDisplayVersion(appData.versionName)
        .setPlatform(REPORT_ANDROID_PLATFORM);
  }

  private CrashlyticsReport.Session populateSessionData(String identifier, long timestamp) {
    return CrashlyticsReport.Session.builder()
        .setStartedAt(timestamp)
        .setIdentifier(identifier)
        .setGenerator(GENERATOR)
        .setApp(populateSessionApplicationData())
        .setOs(populateSessionOperatingSystemData())
        .setDevice(populateSessionDeviceData())
        .setGeneratorType(GENERATOR_TYPE)
        .build();
  }

  private CrashlyticsReport.Session.Application populateSessionApplicationData() {
    return CrashlyticsReport.Session.Application.builder()
        .setIdentifier(idManager.getAppIdentifier())
        .setVersion(appData.versionCode)
        .setDisplayVersion(appData.versionName)
        .setInstallationUuid(idManager.getCrashlyticsInstallId())
        .build();
  }

  private CrashlyticsReport.Session.OperatingSystem populateSessionOperatingSystemData() {
    return CrashlyticsReport.Session.OperatingSystem.builder()
        .setPlatform(SESSION_ANDROID_PLATFORM)
        .setVersion(VERSION.RELEASE)
        .setBuildVersion(VERSION.CODENAME)
        .setJailbroken(CommonUtils.isRooted(context))
        .build();
  }

  private CrashlyticsReport.Session.Device populateSessionDeviceData() {
    final StatFs statFs = new StatFs(Environment.getDataDirectory().getPath());
    final int arch = getDeviceArchitecture();
    final int availableProcessors = Runtime.getRuntime().availableProcessors();
    final long totalRam = CommonUtils.getTotalRamInBytes();
    final long diskSpace = (long) statFs.getBlockCount() * (long) statFs.getBlockSize();
    final boolean isEmulator = CommonUtils.isEmulator(context);
    final int state = CommonUtils.getDeviceState(context);
    final String manufacturer = Build.MANUFACTURER;
    final String modelClass = Build.PRODUCT;

    return CrashlyticsReport.Session.Device.builder()
        .setArch(arch)
        .setModel(Build.MODEL)
        .setCores(availableProcessors)
        .setRam(totalRam)
        .setDiskSpace(diskSpace)
        .setSimulator(isEmulator)
        .setState(state)
        .setManufacturer(manufacturer)
        .setModelClass(modelClass)
        .build();
  }

  private Event.Application populateEventApplicationData(
      int orientation,
      TrimmedThrowableData trimmedEvent,
      Thread eventThread,
      int eventThreadImportance,
      int maxChainedExceptions,
      boolean includeAllThreads) {
    Boolean isBackground = null;
    final RunningAppProcessInfo runningAppProcessInfo =
        CommonUtils.getAppProcessInfo(appData.packageName, context);
    if (runningAppProcessInfo != null) {
      // Several different types of "background" states, easiest to check for not foreground.
      isBackground =
          runningAppProcessInfo.importance
              != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
    }

    return Event.Application.builder()
        .setBackground(isBackground)
        .setUiOrientation(orientation)
        .setExecution(
            populateExecutionData(
                trimmedEvent,
                eventThread,
                eventThreadImportance,
                maxChainedExceptions,
                includeAllThreads))
        .build();
  }

  private Event.Device populateEventDeviceData(int orientation) {
    final BatteryState battery = BatteryState.get(context);
    final Float batteryLevel = battery.getBatteryLevel();
    final Double batteryLevelDouble = (batteryLevel != null) ? batteryLevel.doubleValue() : null;
    final int batteryVelocity = battery.getBatteryVelocity();
    final boolean proximityEnabled = CommonUtils.getProximitySensorEnabled(context);
    final long usedRamBytes =
        CommonUtils.getTotalRamInBytes() - CommonUtils.calculateFreeRamInBytes(context);
    final long diskUsedBytes =
        CommonUtils.calculateUsedDiskSpaceInBytes(Environment.getDataDirectory().getPath());

    return Event.Device.builder()
        .setBatteryLevel(batteryLevelDouble)
        .setBatteryVelocity(batteryVelocity)
        .setProximityOn(proximityEnabled)
        .setOrientation(orientation)
        .setRamUsed(usedRamBytes)
        .setDiskUsed(diskUsedBytes)
        .build();
  }

  private Execution populateExecutionData(
      TrimmedThrowableData trimmedEvent,
      Thread eventThread,
      int eventThreadImportance,
      int maxChainedExceptions,
      boolean includeAllThreads) {
    return Execution.builder()
        .setThreads(
            populateThreadsList(
                trimmedEvent, eventThread, eventThreadImportance, includeAllThreads))
        .setException(
            populateExceptionData(trimmedEvent, eventThreadImportance, maxChainedExceptions))
        .setSignal(populateSignalData())
        .setBinaries(populateBinaryImagesList())
        .build();
  }

  private ImmutableList<Execution.Thread> populateThreadsList(
      TrimmedThrowableData trimmedEvent,
      Thread eventThread,
      int eventThreadImportance,
      boolean includeAllThreads) {
    List<Execution.Thread> threadsList = new ArrayList<>();

    threadsList.add(
        populateThreadData(eventThread, trimmedEvent.stacktrace, eventThreadImportance));

    if (includeAllThreads) {
      final Map<Thread, StackTraceElement[]> allStackTraces = Thread.getAllStackTraces();
      for (Map.Entry<Thread, StackTraceElement[]> entry : allStackTraces.entrySet()) {
        final Thread thread = entry.getKey();
        // Skip the event thread, since we populated it first.
        if (!thread.equals(eventThread)) {
          threadsList.add(
              populateThreadData(
                  thread, stackTraceTrimmingStrategy.getTrimmedStackTrace(entry.getValue())));
        }
      }
    }

    return ImmutableList.from(threadsList);
  }

  private Execution.Thread populateThreadData(Thread thread, StackTraceElement[] stacktrace) {
    return populateThreadData(thread, stacktrace, 0);
  }

  private Execution.Thread populateThreadData(
      Thread thread, StackTraceElement[] stacktrace, int importance) {
    return Execution.Thread.builder()
        .setName(thread.getName())
        .setImportance(importance)
        .setFrames(ImmutableList.from(populateFramesList(stacktrace, importance)))
        .build();
  }

  private ImmutableList<Execution.Thread.Frame> populateFramesList(
      StackTraceElement[] stacktrace, int importance) {
    final List<Execution.Thread.Frame> framesList = new ArrayList<>();
    for (StackTraceElement element : stacktrace) {
      framesList.add(
          populateFrameData(element, Execution.Thread.Frame.builder().setImportance(importance)));
    }
    return ImmutableList.from(framesList);
  }

  private Execution.Exception populateExceptionData(
      TrimmedThrowableData trimmedEvent, int eventThreadImportance, int maxChainedExceptions) {
    return populateExceptionData(trimmedEvent, eventThreadImportance, maxChainedExceptions, 0);
  }

  private Execution.Exception populateExceptionData(
      TrimmedThrowableData trimmedEvent,
      int eventThreadImportance,
      int maxChainedExceptions,
      int chainDepth) {
    final String type = trimmedEvent.className;
    final String reason = trimmedEvent.localizedMessage;
    final StackTraceElement[] stacktrace =
        trimmedEvent.stacktrace != null ? trimmedEvent.stacktrace : new StackTraceElement[0];
    final TrimmedThrowableData cause = trimmedEvent.cause;

    int overflowCount = 0;
    if (chainDepth >= maxChainedExceptions) {
      TrimmedThrowableData skipped = cause;
      while (skipped != null) {
        skipped = skipped.cause;
        ++overflowCount;
      }
    }

    final Execution.Exception.Builder builder =
        Execution.Exception.builder()
            .setType(type)
            .setReason(reason)
            .setFrames(ImmutableList.from(populateFramesList(stacktrace, eventThreadImportance)))
            .setOverflowCount(overflowCount);

    if (cause != null && overflowCount == 0) {
      builder.setCausedBy(
          populateExceptionData(
              cause, eventThreadImportance, maxChainedExceptions, chainDepth + 1));
    }

    return builder.build();
  }

  private Execution.Thread.Frame populateFrameData(
      StackTraceElement element, Execution.Thread.Frame.Builder frameBuilder) {
    long pc = 0L;
    if (element.isNativeMethod()) {
      // certain ProGuard configs will result in negative line numbers,
      // which cannot - by design - be mapped back to the source file.
      pc = Math.max(element.getLineNumber(), 0L);
    }

    final String symbol = element.getClassName() + "." + element.getMethodName();
    final String file = element.getFileName();

    // Same as with pc, ProGuard sometimes generates negative numbers.
    // Here the field is optional, so we can just skip it if we're negative.
    long offset = 0L;
    if (!element.isNativeMethod() && element.getLineNumber() > 0) {
      offset = element.getLineNumber();
    }

    return frameBuilder.setPc(pc).setSymbol(symbol).setFile(file).setOffset(offset).build();
  }

  private ImmutableList<BinaryImage> populateBinaryImagesList() {
    return ImmutableList.from(populateBinaryImageData());
  }

  private Execution.BinaryImage populateBinaryImageData() {
    return Execution.BinaryImage.builder()
        .setBaseAddress(0L)
        .setSize(0L)
        .setName(appData.packageName)
        .setUuid(appData.buildId)
        .build();
  }

  private Execution.Signal populateSignalData() {
    return Execution.Signal.builder()
        .setName(SIGNAL_DEFAULT)
        .setCode(SIGNAL_DEFAULT)
        .setAddress(0L)
        .build();
  }

  @Architecture
  private static int getDeviceArchitecture() {
    final String primaryAbi = Build.CPU_ABI;

    if (TextUtils.isEmpty(primaryAbi)) {
      return Architecture.UNKNOWN;
    }

    final Integer arch = ARCHITECTURES_BY_NAME.get(primaryAbi.toLowerCase(Locale.US));
    if (arch == null) {
      return Architecture.UNKNOWN;
    }

    return arch;
  }
}
