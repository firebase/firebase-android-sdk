// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.proto;

import android.app.ActivityManager;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.stacktrace.TrimmedThrowableData;
import java.util.List;
import java.util.Map;

/**
 * Helper class which handles the complicated custom protobuf writing. Methods of this class are not
 * synchronized or locked, and should be called on the single-threaded executor.
 */
public class SessionProtobufHelper {

  private static final String SIGNAL_DEFAULT = "0";
  private static final ByteString SIGNAL_DEFAULT_BYTE_STRING =
      ByteString.copyFromUtf8(SIGNAL_DEFAULT);

  private static final ByteString UNITY_PLATFORM_BYTE_STRING =
      ByteString.copyFromUtf8(CrashlyticsReport.DEVELOPMENT_PLATFORM_UNITY);

  private SessionProtobufHelper() {}

  public static void writeBeginSession(
      CodedOutputStream cos, String sessionId, String generator, long startedAtSeconds)
      throws Exception {
    cos.writeBytes(1, ByteString.copyFromUtf8(generator));
    cos.writeBytes(2, ByteString.copyFromUtf8(sessionId));
    cos.writeUInt64(3, startedAtSeconds);
  }

  public static void writeSessionApp(
      CodedOutputStream cos,
      String packageName,
      String versionCode,
      String versionName,
      String installUuid,
      int deliveryMechanism,
      String unityVersion)
      throws Exception {
    final ByteString packageNameBytes = ByteString.copyFromUtf8(packageName);
    final ByteString versionCodeBytes = ByteString.copyFromUtf8(versionCode);
    final ByteString versionNameBytes = ByteString.copyFromUtf8(versionName);
    final ByteString installIdBytes = ByteString.copyFromUtf8(installUuid);
    final ByteString unityVersionBytes =
        (unityVersion != null) ? ByteString.copyFromUtf8(unityVersion) : null;

    // Session App
    cos.writeTag(7, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(
        getSessionAppSize(
            packageNameBytes,
            versionCodeBytes,
            versionNameBytes,
            installIdBytes,
            deliveryMechanism,
            unityVersionBytes));
    cos.writeBytes(1, packageNameBytes);
    cos.writeBytes(2, versionCodeBytes);
    cos.writeBytes(3, versionNameBytes);

    // Back in Session App
    cos.writeBytes(6, installIdBytes);

    if (unityVersionBytes != null) {
      cos.writeBytes(8, UNITY_PLATFORM_BYTE_STRING);
      cos.writeBytes(9, unityVersionBytes);
    }

    cos.writeEnum(10, deliveryMechanism);
  }

  public static void writeSessionOS(
      CodedOutputStream cos, String osRelease, String osCodeName, boolean isRooted)
      throws Exception {
    final ByteString releaseBytes = ByteString.copyFromUtf8(osRelease);
    final ByteString codeNameBytes = ByteString.copyFromUtf8(osCodeName);

    cos.writeTag(8, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(getSessionOSSize(releaseBytes, codeNameBytes, isRooted));
    cos.writeEnum(1, 3);
    cos.writeBytes(2, releaseBytes);
    cos.writeBytes(3, codeNameBytes);
    cos.writeBool(4, isRooted);
  }

  public static void writeSessionDevice(
      CodedOutputStream cos,
      int arch,
      String model,
      int availableProcessors,
      long totalRam,
      long diskSpace,
      boolean isEmulator,
      int state,
      String manufacturer,
      String modelClass)
      throws Exception {
    final ByteString modelBytes = stringToByteString(model);
    final ByteString modelClassBytes = stringToByteString(modelClass);
    final ByteString manufacturerBytes = stringToByteString(manufacturer);

    cos.writeTag(9, WireFormat.WIRETYPE_LENGTH_DELIMITED);

    cos.writeRawVarint32(
        getSessionDeviceSize(
            arch,
            modelBytes,
            availableProcessors,
            totalRam,
            diskSpace,
            isEmulator,
            state,
            manufacturerBytes,
            modelClassBytes));

    // 1 - identifier is deprecated
    // 2 - udid is deprecated.
    cos.writeEnum(3, arch);
    cos.writeBytes(4, modelBytes);
    cos.writeUInt32(5, availableProcessors);
    cos.writeUInt64(6, totalRam);
    cos.writeUInt64(7, diskSpace);
    cos.writeBool(10, isEmulator);
    cos.writeUInt32(12, state);

    if (manufacturerBytes != null) {
      cos.writeBytes(13, manufacturerBytes);
    }
    if (modelClassBytes != null) {
      cos.writeBytes(14, modelClassBytes);
    }
  }

  public static void writeSessionUser(CodedOutputStream cos, String id, String name, String email)
      throws Exception {
    // ID is a required field, so it if hasn't been assigned, we'll make it empty
    final ByteString idBytes = ByteString.copyFromUtf8(id == null ? "" : id);
    final ByteString nameBytes = stringToByteString(name);
    final ByteString emailBytes = stringToByteString(email);

    int size = 0;
    size += CodedOutputStream.computeBytesSize(1, idBytes);
    if (name != null) {
      size += CodedOutputStream.computeBytesSize(2, nameBytes);
    }
    if (email != null) {
      size += CodedOutputStream.computeBytesSize(3, emailBytes);
    }

    cos.writeTag(6, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(size);
    cos.writeBytes(1, idBytes);
    if (name != null) {
      cos.writeBytes(2, nameBytes);
    }
    if (email != null) {
      cos.writeBytes(3, emailBytes);
    }
  }

  public static void writeSessionEvent(
      CodedOutputStream cos,
      long eventTime,
      String eventType,
      TrimmedThrowableData exception,
      Thread exceptionThread,
      StackTraceElement[] exceptionStack,
      Thread[] otherThreads,
      List<StackTraceElement[]> otherStacks,
      int maxChainedExceptionsDepth,
      Map<String, String> customAttributes,
      byte[] logBytes,
      ActivityManager.RunningAppProcessInfo runningAppProcessInfo,
      int orientation,
      String packageName,
      String buildId,
      Float batteryLevel,
      int batteryVelocity,
      boolean proximityEnabled,
      long usedRamInBytes,
      long diskUsedInBytes)
      throws Exception {

    final ByteString packageNameBytes = ByteString.copyFromUtf8(packageName);
    final ByteString optionalBuildIdBytes =
        (buildId == null) ? null : ByteString.copyFromUtf8(buildId.replace("-", ""));

    ByteString logByteString = null;
    if (logBytes != null) {
      logByteString = ByteString.copyFrom(logBytes);
    } else {
      Logger.getLogger().d("No log data to include with this event.");
    }

    cos.writeTag(10, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(
        getSessionEventSize(
            eventTime,
            eventType,
            exception,
            exceptionThread,
            exceptionStack,
            otherThreads,
            otherStacks,
            maxChainedExceptionsDepth,
            customAttributes,
            runningAppProcessInfo,
            orientation,
            packageNameBytes,
            optionalBuildIdBytes,
            batteryLevel,
            batteryVelocity,
            proximityEnabled,
            usedRamInBytes,
            diskUsedInBytes,
            logByteString));
    cos.writeUInt64(1, eventTime);
    cos.writeBytes(2, ByteString.copyFromUtf8(eventType));

    writeSessionEventApp(
        cos,
        exception,
        exceptionThread,
        exceptionStack,
        otherThreads,
        otherStacks,
        maxChainedExceptionsDepth,
        packageNameBytes,
        optionalBuildIdBytes,
        customAttributes,
        runningAppProcessInfo,
        orientation);
    writeSessionEventDevice(
        cos,
        batteryLevel,
        batteryVelocity,
        proximityEnabled,
        orientation,
        usedRamInBytes,
        diskUsedInBytes);
    writeSessionEventLog(cos, logByteString);
  }

  public static void writeSessionAppClsId(CodedOutputStream cos, String clsId) throws Exception {
    final ByteString orgIdBytes = ByteString.copyFromUtf8(clsId);
    cos.writeTag(7, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    final int orgIdSize = CodedOutputStream.computeBytesSize(2, orgIdBytes);
    final int sessionAppOrgSize =
        CodedOutputStream.computeTagSize(5)
            + CodedOutputStream.computeRawVarint32Size(orgIdSize)
            + orgIdSize;
    cos.writeRawVarint32(sessionAppOrgSize);
    cos.writeTag(5, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(orgIdSize);
    cos.writeBytes(2, orgIdBytes);
  }

  private static void writeSessionEventApp(
      CodedOutputStream cos,
      TrimmedThrowableData exception,
      Thread exceptionThread,
      StackTraceElement[] exceptionStack,
      Thread[] otherThreads,
      List<StackTraceElement[]> otherStacks,
      int maxChainedExceptionsDepth,
      ByteString packageNameBytes,
      ByteString optionalBuildIdBytes,
      Map<String, String> customAttributes,
      ActivityManager.RunningAppProcessInfo runningAppProcessInfo,
      int orientation)
      throws Exception {
    cos.writeTag(3, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(
        getEventAppSize(
            exception,
            exceptionThread,
            exceptionStack,
            otherThreads,
            otherStacks,
            maxChainedExceptionsDepth,
            packageNameBytes,
            optionalBuildIdBytes,
            customAttributes,
            runningAppProcessInfo,
            orientation));

    writeSessionEventAppExecution(
        cos,
        exception,
        exceptionThread,
        exceptionStack,
        otherThreads,
        otherStacks,
        maxChainedExceptionsDepth,
        packageNameBytes,
        optionalBuildIdBytes);

    if (customAttributes != null && !customAttributes.isEmpty()) {
      writeSessionEventAppCustomAttributes(cos, customAttributes);
    }

    // info == null is an error state, no way to capture that in protobuf.
    if (runningAppProcessInfo != null) {
      // Several different types of "background" states, easiest to check for not foreground.
      cos.writeBool(
          3,
          runningAppProcessInfo.importance
              != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    }

    cos.writeUInt32(4, orientation);
  }

  private static void writeSessionEventAppExecution(
      CodedOutputStream cos,
      TrimmedThrowableData exception,
      Thread exceptionThread,
      StackTraceElement[] exceptionStack,
      Thread[] otherThreads,
      List<StackTraceElement[]> otherStacks,
      int maxChainedExceptionsDepth,
      ByteString packageNameBytes,
      ByteString optionalBuildIdBytes)
      throws Exception {
    cos.writeTag(1, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(
        getEventAppExecutionSize(
            exception,
            exceptionThread,
            exceptionStack,
            otherThreads,
            otherStacks,
            maxChainedExceptionsDepth,
            packageNameBytes,
            optionalBuildIdBytes));

    writeThread(cos, exceptionThread, exceptionStack, 4, true);

    // Write the rest of the threads.
    // The crashed Thread is not returned by Thread.getAllStackTraces()
    final int len = otherThreads.length;
    for (int i = 0; i < len; i++) {
      final Thread thread = otherThreads[i];
      writeThread(cos, thread, otherStacks.get(i), 0, false);
    }

    writeSessionEventAppExecutionException(cos, exception, 1, maxChainedExceptionsDepth, 2);

    cos.writeTag(3, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(getEventAppExecutionSignalSize());
    cos.writeBytes(1, SIGNAL_DEFAULT_BYTE_STRING);
    cos.writeBytes(2, SIGNAL_DEFAULT_BYTE_STRING);
    cos.writeUInt64(3, 0);

    cos.writeTag(4, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(getBinaryImageSize(packageNameBytes, optionalBuildIdBytes));
    cos.writeUInt64(1, 0L);
    cos.writeUInt64(2, 0L);
    cos.writeBytes(3, packageNameBytes);
    if (optionalBuildIdBytes != null) {
      cos.writeBytes(4, optionalBuildIdBytes);
    }
  }

  private static void writeSessionEventAppCustomAttributes(
      CodedOutputStream cos, Map<String, String> customAttributes) throws Exception {

    for (Map.Entry<String, String> entry : customAttributes.entrySet()) {
      cos.writeTag(2, WireFormat.WIRETYPE_LENGTH_DELIMITED);
      cos.writeRawVarint32(getEventAppCustomAttributeSize(entry.getKey(), entry.getValue()));

      cos.writeBytes(1, ByteString.copyFromUtf8(entry.getKey()));
      final String value = entry.getValue();
      cos.writeBytes(2, ByteString.copyFromUtf8(value == null ? "" : value));
    }
  }

  private static void writeSessionEventAppExecutionException(
      CodedOutputStream cos,
      TrimmedThrowableData exception,
      int chainDepth,
      int maxChainedExceptionsDepth,
      int field)
      throws Exception {
    cos.writeTag(field, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(
        getEventAppExecutionExceptionSize(exception, 1, maxChainedExceptionsDepth));

    cos.writeBytes(1, ByteString.copyFromUtf8(exception.className));
    final String message = exception.localizedMessage;
    if (message != null) {
      cos.writeBytes(3, ByteString.copyFromUtf8(message));
    }

    for (StackTraceElement element : exception.stacktrace) {
      writeFrame(cos, 4, element, true);
    }

    TrimmedThrowableData cause = exception.cause;
    if (cause != null) {
      if (chainDepth < maxChainedExceptionsDepth) {
        writeSessionEventAppExecutionException(
            cos, cause, chainDepth + 1, maxChainedExceptionsDepth, 6);
      } else {
        // only report the overflow count if there was an overflow,
        // and stop reporting the rest of the chain.
        int overflowCount = 0;
        while (cause != null) {
          cause = cause.cause;
          ++overflowCount;
        }
        cos.writeUInt32(7, overflowCount);
      }
    }
  }

  private static void writeThread(
      CodedOutputStream cos,
      Thread thread,
      StackTraceElement[] stackTraceElements,
      int importance,
      boolean isCrashedThread)
      throws Exception {
    cos.writeTag(1, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    final int s = getThreadSize(thread, stackTraceElements, importance, isCrashedThread);
    cos.writeRawVarint32(s);
    cos.writeBytes(1, ByteString.copyFromUtf8(thread.getName()));
    cos.writeUInt32(2, importance);

    for (StackTraceElement stackTraceElement : stackTraceElements) {
      writeFrame(cos, 3, stackTraceElement, isCrashedThread);
    }
  }

  private static void writeFrame(
      CodedOutputStream cos, int fieldIndex, StackTraceElement element, boolean isCrashedThread)
      throws Exception {
    cos.writeTag(fieldIndex, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(getFrameSize(element, isCrashedThread));

    if (element.isNativeMethod()) {
      // certain ProGuard configs will result in negative line numbers,
      // which cannot - by design - be mapped back to the source file.
      cos.writeUInt64(1, Math.max(element.getLineNumber(), 0));
    } else {
      cos.writeUInt64(1, 0L);
    }

    cos.writeBytes(
        2, ByteString.copyFromUtf8(element.getClassName() + "." + element.getMethodName()));

    if (element.getFileName() != null) {
      cos.writeBytes(3, ByteString.copyFromUtf8(element.getFileName()));
    }

    // Same as with field 1, ProGuard sometimes generates negative numbers.
    // Here the field is optional, so we can just skip it if we're negative.
    if (!element.isNativeMethod() && element.getLineNumber() > 0) {
      cos.writeUInt64(4, element.getLineNumber());
    }

    // TODO Could be more sophisticated here based on package name.
    // Need to consider ProGuard implications and that package name conventions aren't
    // enforced by the compiler (i.e., anyone can make a class called java.io.MyClass)
    cos.writeUInt32(5, isCrashedThread ? 4 : 0);
  }

  private static void writeSessionEventDevice(
      CodedOutputStream cos,
      Float batteryLevel,
      int batteryVelocity,
      boolean proximityEnabled,
      int orientation,
      long heapAllocatedSize,
      long diskUsed)
      throws Exception {
    cos.writeTag(5, WireFormat.WIRETYPE_LENGTH_DELIMITED);
    cos.writeRawVarint32(
        getEventDeviceSize(
            batteryLevel,
            batteryVelocity,
            proximityEnabled,
            orientation,
            heapAllocatedSize,
            diskUsed));
    if (batteryLevel != null) {
      cos.writeFloat(1, batteryLevel);
    }
    cos.writeSInt32(2, batteryVelocity);
    cos.writeBool(3, proximityEnabled);
    cos.writeUInt32(4, orientation);
    cos.writeUInt64(5, heapAllocatedSize);
    cos.writeUInt64(6, diskUsed);
  }

  private static void writeSessionEventLog(CodedOutputStream cos, ByteString log) throws Exception {
    if (log != null) {
      cos.writeTag(6, WireFormat.WIRETYPE_LENGTH_DELIMITED);
      cos.writeRawVarint32(getEventLogSize(log));
      cos.writeBytes(1, log);
    }
  }

  private static int getSessionAppSize(
      ByteString packageName,
      ByteString versionCode,
      ByteString versionName,
      ByteString installUuid,
      int deliveryMechanism,
      ByteString unityVersion) {
    int size = 0;

    size += CodedOutputStream.computeBytesSize(1, packageName);
    size += CodedOutputStream.computeBytesSize(2, versionCode);
    size += CodedOutputStream.computeBytesSize(3, versionName);
    size += CodedOutputStream.computeBytesSize(6, installUuid);

    if (unityVersion != null) {
      size += CodedOutputStream.computeBytesSize(8, UNITY_PLATFORM_BYTE_STRING);
      size += CodedOutputStream.computeBytesSize(9, unityVersion);
    }

    size += CodedOutputStream.computeEnumSize(10, deliveryMechanism);

    return size;
  }

  private static int getSessionOSSize(ByteString release, ByteString codeName, boolean isRooted) {
    int size = 0;

    size += CodedOutputStream.computeEnumSize(1, 3);
    size += CodedOutputStream.computeBytesSize(2, release);
    size += CodedOutputStream.computeBytesSize(3, codeName);
    size += CodedOutputStream.computeBoolSize(4, isRooted);

    return size;
  }

  private static int getSessionDeviceSize(
      int arch,
      ByteString model,
      int availableProcessors,
      long totalRam,
      long diskSpace,
      boolean isEmulator,
      int state,
      ByteString manufacturer,
      ByteString modelClass) {
    int size = 0;

    size += CodedOutputStream.computeEnumSize(3, arch);
    size += (model == null) ? 0 : CodedOutputStream.computeBytesSize(4, model);
    size += CodedOutputStream.computeUInt32Size(5, availableProcessors);
    size += CodedOutputStream.computeUInt64Size(6, totalRam);
    size += CodedOutputStream.computeUInt64Size(7, diskSpace);
    size += CodedOutputStream.computeBoolSize(10, isEmulator);
    size += CodedOutputStream.computeUInt32Size(12, state);
    size += (manufacturer == null) ? 0 : CodedOutputStream.computeBytesSize(13, manufacturer);
    size += (modelClass == null) ? 0 : CodedOutputStream.computeBytesSize(14, modelClass);

    return size;
  }

  private static int getBinaryImageSize(
      ByteString packageNameBytes, ByteString optionalBuildIdBytes) {
    int size = 0;

    size += CodedOutputStream.computeUInt64Size(1, 0L);
    size += CodedOutputStream.computeUInt64Size(2, 0L);
    size += CodedOutputStream.computeBytesSize(3, packageNameBytes);
    if (optionalBuildIdBytes != null) {
      size += CodedOutputStream.computeBytesSize(4, optionalBuildIdBytes);
    }

    return size;
  }

  private static int getSessionEventSize(
      long eventTime,
      String eventType,
      TrimmedThrowableData exception,
      Thread exceptionThread,
      StackTraceElement[] exceptionStack,
      Thread[] otherThreads,
      List<StackTraceElement[]> otherStacks,
      int maxChainedExceptionsDepth,
      Map<String, String> customAttributes,
      ActivityManager.RunningAppProcessInfo runningAppProcessInfo,
      int orientation,
      ByteString packageNameBytes,
      ByteString optionalBuildIdBytes,
      Float batteryLevel,
      int batteryVelocity,
      boolean proximityEnabled,
      long heapAllocatedSize,
      long diskUsed,
      ByteString log) {
    int size = 0;

    size += CodedOutputStream.computeUInt64Size(1, eventTime);
    size += CodedOutputStream.computeBytesSize(2, ByteString.copyFromUtf8(eventType));
    final int eventAppSize =
        getEventAppSize(
            exception,
            exceptionThread,
            exceptionStack,
            otherThreads,
            otherStacks,
            maxChainedExceptionsDepth,
            packageNameBytes,
            optionalBuildIdBytes,
            customAttributes,
            runningAppProcessInfo,
            orientation);
    size +=
        CodedOutputStream.computeTagSize(3)
            + CodedOutputStream.computeRawVarint32Size(eventAppSize)
            + eventAppSize;
    final int eventDeviceSize =
        getEventDeviceSize(
            batteryLevel,
            batteryVelocity,
            proximityEnabled,
            orientation,
            heapAllocatedSize,
            diskUsed);
    size +=
        CodedOutputStream.computeTagSize(5)
            + CodedOutputStream.computeRawVarint32Size(eventDeviceSize)
            + eventDeviceSize;

    if (log != null) {
      final int logSize = getEventLogSize(log);
      size +=
          CodedOutputStream.computeTagSize(6)
              + CodedOutputStream.computeRawVarint32Size(logSize)
              + logSize;
    }

    return size;
  }

  private static int getEventAppSize(
      TrimmedThrowableData exception,
      Thread exceptionThread,
      StackTraceElement[] exceptionStack,
      Thread[] otherThreads,
      List<StackTraceElement[]> otherStacks,
      int maxChainedExceptionsDepth,
      ByteString packageNameBytes,
      ByteString optionalBuildIdBytes,
      Map<String, String> customAttributes,
      ActivityManager.RunningAppProcessInfo runningAppProcessInfo,
      int orientation) {
    int size = 0;

    final int executionSize =
        getEventAppExecutionSize(
            exception,
            exceptionThread,
            exceptionStack,
            otherThreads,
            otherStacks,
            maxChainedExceptionsDepth,
            packageNameBytes,
            optionalBuildIdBytes);
    size +=
        CodedOutputStream.computeTagSize(1)
            + CodedOutputStream.computeRawVarint32Size(executionSize)
            + executionSize;

    if (customAttributes != null) {
      for (Map.Entry<String, String> entry : customAttributes.entrySet()) {
        final int entrySize = getEventAppCustomAttributeSize(entry.getKey(), entry.getValue());
        size +=
            CodedOutputStream.computeTagSize(2)
                + CodedOutputStream.computeRawVarint32Size(entrySize)
                + entrySize;
      }
    }

    // info == null is an error state, no way to capture that in protobuf.
    if (runningAppProcessInfo != null) {
      size +=
          CodedOutputStream.computeBoolSize(
              3,
              runningAppProcessInfo.importance
                  != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);
    }
    size += CodedOutputStream.computeUInt32Size(4, orientation);

    return size;
  }

  private static int getEventAppExecutionSize(
      TrimmedThrowableData exception,
      Thread exceptionThread,
      StackTraceElement[] exceptionStack,
      Thread[] otherThreads,
      List<StackTraceElement[]> otherStacks,
      int maxChainedExceptionDepth,
      ByteString packageNameBytes,
      ByteString optionalBuildIdBytes) {
    int size = 0;

    int threadSize = getThreadSize(exceptionThread, exceptionStack, 4, true);
    size +=
        CodedOutputStream.computeTagSize(1)
            + CodedOutputStream.computeRawVarint32Size(threadSize)
            + threadSize;

    final int len = otherThreads.length;
    for (int i = 0; i < len; i++) {
      final Thread thread = otherThreads[i];
      threadSize = getThreadSize(thread, otherStacks.get(i), 0, false);
      size +=
          CodedOutputStream.computeTagSize(1)
              + CodedOutputStream.computeRawVarint32Size(threadSize)
              + threadSize;
    }

    final int exceptionSize =
        getEventAppExecutionExceptionSize(exception, 1, maxChainedExceptionDepth);
    size +=
        CodedOutputStream.computeTagSize(2)
            + CodedOutputStream.computeRawVarint32Size(exceptionSize)
            + exceptionSize;

    final int signalSize = getEventAppExecutionSignalSize();
    size +=
        CodedOutputStream.computeTagSize(3)
            + CodedOutputStream.computeRawVarint32Size(signalSize)
            + signalSize;

    final int binaryImageSize = getBinaryImageSize(packageNameBytes, optionalBuildIdBytes);
    size +=
        CodedOutputStream.computeTagSize(3)
            + CodedOutputStream.computeRawVarint32Size(binaryImageSize)
            + binaryImageSize;

    return size;
  }

  private static int getEventAppCustomAttributeSize(String key, String value) {
    int size = CodedOutputStream.computeBytesSize(1, ByteString.copyFromUtf8(key));
    size +=
        CodedOutputStream.computeBytesSize(2, ByteString.copyFromUtf8(value == null ? "" : value));
    return size;
  }

  private static int getEventDeviceSize(
      Float batteryLevel,
      int batteryVelocity,
      boolean proximityEnabled,
      int orientation,
      long heapAllocatedSize,
      long diskUsed) {
    int size = 0;

    if (batteryLevel != null) {
      size += CodedOutputStream.computeFloatSize(1, batteryLevel);
    }
    size += CodedOutputStream.computeSInt32Size(2, batteryVelocity);
    size += CodedOutputStream.computeBoolSize(3, proximityEnabled);
    size += CodedOutputStream.computeUInt32Size(4, orientation);
    size += CodedOutputStream.computeUInt64Size(5, heapAllocatedSize);
    size += CodedOutputStream.computeUInt64Size(6, diskUsed);

    return size;
  }

  /** The ByteString must not be null. */
  private static int getEventLogSize(ByteString log) {
    return CodedOutputStream.computeBytesSize(1, log);
  }

  private static int getEventAppExecutionExceptionSize(
      TrimmedThrowableData ex, int chainDepth, int maxChainedExceptionsDepth) {
    int size = 0;

    size += CodedOutputStream.computeBytesSize(1, ByteString.copyFromUtf8(ex.className));
    //      size += CodedOutputStream.computeBytesSize(2, getCodeBytes()); // NOT USED

    final String message = ex.localizedMessage;
    if (message != null) {
      size += CodedOutputStream.computeBytesSize(3, ByteString.copyFromUtf8(message));
    }

    for (StackTraceElement element : ex.stacktrace) {
      final int frameSize = getFrameSize(element, true);
      size +=
          CodedOutputStream.computeTagSize(4)
              + CodedOutputStream.computeRawVarint32Size(frameSize)
              + frameSize;
    }

    // size += CodedOutputStream.computeMessageSize(5, customAttributes_.get(i)); // FUTURE TASK

    TrimmedThrowableData cause = ex.cause;
    if (cause != null) {
      if (chainDepth < maxChainedExceptionsDepth) {
        final int exceptionSize =
            getEventAppExecutionExceptionSize(cause, chainDepth + 1, maxChainedExceptionsDepth);
        size +=
            CodedOutputStream.computeTagSize(6)
                + CodedOutputStream.computeRawVarint32Size(exceptionSize)
                + exceptionSize;
      } else {
        // only report the overflow count if there was an overflow,
        // and stop reporting the rest of the chain.
        int overflowCount = 0;
        while (cause != null) {
          cause = cause.cause;
          ++overflowCount;
        }

        size += CodedOutputStream.computeUInt32Size(7, overflowCount);
      }
    }

    return size;
  }

  private static int getEventAppExecutionSignalSize() {
    int size = 0;

    size += CodedOutputStream.computeBytesSize(1, SIGNAL_DEFAULT_BYTE_STRING);
    size += CodedOutputStream.computeBytesSize(2, SIGNAL_DEFAULT_BYTE_STRING);
    size += CodedOutputStream.computeUInt64Size(3, 0L);

    return size;
  }

  private static int getFrameSize(StackTraceElement element, boolean isCrashedThread) {
    int size = 0;

    if (element.isNativeMethod()) {
      // see comments in writeFrame method
      size += CodedOutputStream.computeUInt64Size(1, Math.max(element.getLineNumber(), 0));
    } else {
      size += CodedOutputStream.computeUInt64Size(1, 0L);
    }

    size +=
        CodedOutputStream.computeBytesSize(
            2, ByteString.copyFromUtf8(element.getClassName() + "." + element.getMethodName()));

    if (element.getFileName() != null) {
      size += CodedOutputStream.computeBytesSize(3, ByteString.copyFromUtf8(element.getFileName()));
    }

    if (!element.isNativeMethod() && element.getLineNumber() > 0) {
      // see comments in writeFrame method
      size += CodedOutputStream.computeUInt64Size(4, element.getLineNumber());
    }
    size += CodedOutputStream.computeUInt32Size(5, isCrashedThread ? 2 : 0);

    return size;
  }

  private static int getThreadSize(
      Thread thread,
      StackTraceElement[] stackTraceElements,
      int importance,
      boolean isCrashedThread) {
    int size = CodedOutputStream.computeBytesSize(1, ByteString.copyFromUtf8(thread.getName()));
    size += CodedOutputStream.computeUInt32Size(2, importance);

    for (StackTraceElement stackTraceElement : stackTraceElements) {
      final int frameSize = getFrameSize(stackTraceElement, isCrashedThread);
      size +=
          CodedOutputStream.computeTagSize(3)
              + CodedOutputStream.computeRawVarint32Size(frameSize)
              + frameSize;
    }

    return size;
  }

  private static ByteString stringToByteString(String s) {
    return s == null ? null : ByteString.copyFromUtf8(s);
  }
}
