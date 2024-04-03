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

package com.google.firebase.crashlytics.internal.model.serialization;

import android.util.Base64;
import android.util.JsonReader;
import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.model.AutoCrashlyticsReportEncoder;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.ApplicationExitInfo.BuildIdMappingForArch;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.CustomAttribute;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CrashlyticsReportJsonTransform {

  private static final DataEncoder CRASHLYTICS_REPORT_JSON_ENCODER =
      new JsonDataEncoderBuilder()
          .configureWith(AutoCrashlyticsReportEncoder.CONFIG)
          .ignoreNullValues(true)
          .build();

  @NonNull
  public String reportToJson(@NonNull CrashlyticsReport report) {
    return CRASHLYTICS_REPORT_JSON_ENCODER.encode(report);
  }

  @NonNull
  public String eventToJson(@NonNull CrashlyticsReport.Session.Event event) {
    return CRASHLYTICS_REPORT_JSON_ENCODER.encode(event);
  }

  @NonNull
  public String applicationExitInfoToJson(
      @NonNull CrashlyticsReport.ApplicationExitInfo applicationExitInfo) {
    return CRASHLYTICS_REPORT_JSON_ENCODER.encode(applicationExitInfo);
  }

  @NonNull
  public CrashlyticsReport reportFromJson(@NonNull String json) throws IOException {
    try (JsonReader jsonReader = new JsonReader(new StringReader(json))) {
      return parseReport(jsonReader);
    } catch (IllegalStateException e) {
      throw new IOException(e);
    }
  }

  @NonNull
  public CrashlyticsReport.Session.Event eventFromJson(@NonNull String json) throws IOException {
    try (JsonReader jsonReader = new JsonReader(new StringReader(json))) {
      return parseEvent(jsonReader);
    } catch (IllegalStateException e) {
      throw new IOException(e);
    }
  }

  @NonNull
  public CrashlyticsReport.ApplicationExitInfo applicationExitInfoFromJson(@NonNull String json)
      throws IOException {
    try (JsonReader jsonReader = new JsonReader(new StringReader(json))) {
      return parseAppExitInfo(jsonReader);
    } catch (IllegalStateException e) {
      throw new IOException(e);
    }
  }

  @NonNull
  private static CrashlyticsReport parseReport(@NonNull JsonReader jsonReader) throws IOException {
    final CrashlyticsReport.Builder builder = CrashlyticsReport.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "sdkVersion":
          builder.setSdkVersion(jsonReader.nextString());
          break;
        case "gmpAppId":
          builder.setGmpAppId(jsonReader.nextString());
          break;
        case "platform":
          builder.setPlatform(jsonReader.nextInt());
          break;
        case "installationUuid":
          builder.setInstallationUuid(jsonReader.nextString());
          break;
        case "firebaseInstallationId":
          builder.setFirebaseInstallationId(jsonReader.nextString());
          break;
        case "firebaseAuthenticationToken":
          builder.setFirebaseAuthenticationToken(jsonReader.nextString());
          break;
        case "appQualitySessionId":
          builder.setAppQualitySessionId(jsonReader.nextString());
          break;
        case "buildVersion":
          builder.setBuildVersion(jsonReader.nextString());
          break;
        case "displayVersion":
          builder.setDisplayVersion(jsonReader.nextString());
          break;
        case "session":
          builder.setSession(parseSession(jsonReader));
          break;
        case "ndkPayload":
          builder.setNdkPayload(parseNdkPayload(jsonReader));
          break;
        case "appExitInfo":
          builder.setAppExitInfo(parseAppExitInfo(jsonReader));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.Session parseSession(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.Session.Builder builder = CrashlyticsReport.Session.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "generator":
          builder.setGenerator(jsonReader.nextString());
          break;
        case "identifier":
          builder.setIdentifierFromUtf8Bytes(
              Base64.decode(jsonReader.nextString(), Base64.NO_WRAP));
          break;
        case "appQualitySessionId":
          builder.setAppQualitySessionId(jsonReader.nextString());
          break;
        case "startedAt":
          builder.setStartedAt(jsonReader.nextLong());
          break;
        case "endedAt":
          builder.setEndedAt(jsonReader.nextLong());
          break;
        case "crashed":
          builder.setCrashed(jsonReader.nextBoolean());
          break;
        case "user":
          builder.setUser(parseUser(jsonReader));
          break;
        case "app":
          builder.setApp(parseApp(jsonReader));
          break;
        case "os":
          builder.setOs(parseOs(jsonReader));
          break;
        case "device":
          builder.setDevice(parseDevice(jsonReader));
          break;
        case "events":
          builder.setEvents(parseArray(jsonReader, CrashlyticsReportJsonTransform::parseEvent));
          break;
        case "generatorType":
          builder.setGeneratorType(jsonReader.nextInt());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.FilesPayload parseNdkPayload(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.FilesPayload.Builder builder = CrashlyticsReport.FilesPayload.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "files":
          builder.setFiles(parseArray(jsonReader, CrashlyticsReportJsonTransform::parseFile));
          break;
        case "orgId":
          builder.setOrgId(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.ApplicationExitInfo parseAppExitInfo(
      @NonNull JsonReader jsonReader) throws IOException {
    final CrashlyticsReport.ApplicationExitInfo.Builder builder =
        CrashlyticsReport.ApplicationExitInfo.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "pid":
          builder.setPid(jsonReader.nextInt());
          break;
        case "processName":
          builder.setProcessName(jsonReader.nextString());
          break;
        case "reasonCode":
          builder.setReasonCode(jsonReader.nextInt());
          break;
        case "importance":
          builder.setImportance(jsonReader.nextInt());
          break;
        case "pss":
          builder.setPss(jsonReader.nextLong());
          break;
        case "rss":
          builder.setRss(jsonReader.nextLong());
          break;
        case "timestamp":
          builder.setTimestamp(jsonReader.nextLong());
          break;
        case "traceFile":
          builder.setTraceFile(jsonReader.nextString());
          break;
        case "buildIdMappingForArch":
          builder.setBuildIdMappingForArch(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseBuildIdMappingForArch));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.FilesPayload.File parseFile(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.FilesPayload.File.Builder builder =
        CrashlyticsReport.FilesPayload.File.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "filename":
          builder.setFilename(jsonReader.nextString());
          break;
        case "contents":
          builder.setContents(Base64.decode(jsonReader.nextString(), Base64.NO_WRAP));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.Session.User parseUser(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.Session.User.Builder builder = CrashlyticsReport.Session.User.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      if (name.equals("identifier")) {
        builder.setIdentifier(jsonReader.nextString());
      } else {
        jsonReader.skipValue();
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.Session.Application parseApp(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.Session.Application.Builder builder =
        CrashlyticsReport.Session.Application.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "identifier":
          builder.setIdentifier(jsonReader.nextString());
          break;
        case "version":
          builder.setVersion(jsonReader.nextString());
          break;
        case "displayVersion":
          builder.setDisplayVersion(jsonReader.nextString());
          break;
        case "installationUuid":
          builder.setInstallationUuid(jsonReader.nextString());
          break;
        case "developmentPlatform":
          builder.setDevelopmentPlatform(jsonReader.nextString());
          break;
        case "developmentPlatformVersion":
          builder.setDevelopmentPlatformVersion(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.Session.OperatingSystem parseOs(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.Session.OperatingSystem.Builder builder =
        CrashlyticsReport.Session.OperatingSystem.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "platform":
          builder.setPlatform(jsonReader.nextInt());
          break;
        case "version":
          builder.setVersion(jsonReader.nextString());
          break;
        case "buildVersion":
          builder.setBuildVersion(jsonReader.nextString());
          break;
        case "jailbroken":
          builder.setJailbroken(jsonReader.nextBoolean());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static CrashlyticsReport.Session.Device parseDevice(@NonNull JsonReader jsonReader)
      throws IOException {
    final CrashlyticsReport.Session.Device.Builder builder =
        CrashlyticsReport.Session.Device.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "arch":
          builder.setArch(jsonReader.nextInt());
          break;
        case "model":
          builder.setModel(jsonReader.nextString());
          break;
        case "cores":
          builder.setCores(jsonReader.nextInt());
          break;
        case "ram":
          builder.setRam(jsonReader.nextLong());
          break;
        case "diskSpace":
          builder.setDiskSpace(jsonReader.nextLong());
          break;
        case "simulator":
          builder.setSimulator(jsonReader.nextBoolean());
          break;
        case "state":
          builder.setState(jsonReader.nextInt());
          break;
        case "manufacturer":
          builder.setManufacturer(jsonReader.nextString());
          break;
        case "modelClass":
          builder.setModelClass(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static Event parseEvent(@NonNull JsonReader jsonReader) throws IOException {
    final Event.Builder builder = Event.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "timestamp":
          builder.setTimestamp(jsonReader.nextLong());
          break;
        case "type":
          builder.setType(jsonReader.nextString());
          break;
        case "app":
          builder.setApp(parseEventApp(jsonReader));
          break;
        case "device":
          builder.setDevice(parseEventDevice(jsonReader));
          break;
        case "log":
          builder.setLog(parseEventLog(jsonReader));
          break;
        case "rollouts":
          builder.setRollouts(parseEventRolloutsState(jsonReader));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application parseEventApp(@NonNull JsonReader jsonReader)
      throws IOException {
    final Event.Application.Builder builder = Event.Application.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "background":
          builder.setBackground(jsonReader.nextBoolean());
          break;
        case "uiOrientation":
          builder.setUiOrientation(jsonReader.nextInt());
          break;
        case "execution":
          builder.setExecution(parseEventExecution(jsonReader));
          break;
        case "customAttributes":
          builder.setCustomAttributes(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseCustomAttribute));
          break;
        case "internalKeys":
          builder.setInternalKeys(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseCustomAttribute));
          break;
        case "currentProcessDetails":
          builder.setCurrentProcessDetails(parseProcessDetails(jsonReader));
          break;
        case "appProcessDetails":
          builder.setAppProcessDetails(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseProcessDetails));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application.ProcessDetails parseProcessDetails(
      @NonNull JsonReader jsonReader) throws IOException {
    Event.Application.ProcessDetails.Builder builder = Event.Application.ProcessDetails.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "processName":
          builder.setProcessName(jsonReader.nextString());
          break;
        case "pid":
          builder.setPid(jsonReader.nextInt());
          break;
        case "importance":
          builder.setImportance(jsonReader.nextInt());
          break;
        case "defaultProcess":
          builder.setDefaultProcess(jsonReader.nextBoolean());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();

    return builder.build();
  }

  @NonNull
  private static Event.Application.Execution parseEventExecution(@NonNull JsonReader jsonReader)
      throws IOException {
    final Event.Application.Execution.Builder builder = Event.Application.Execution.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "threads":
          builder.setThreads(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseEventThread));
          break;
        case "exception":
          builder.setException(parseEventExecutionException(jsonReader));
          break;
        case "signal":
          builder.setSignal(parseEventSignal(jsonReader));
          break;
        case "binaries":
          builder.setBinaries(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseEventBinaryImage));
          break;
        case "appExitInfo":
          builder.setAppExitInfo(parseAppExitInfo(jsonReader));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application.Execution.Exception parseEventExecutionException(
      @NonNull JsonReader jsonReader) throws IOException {
    final Event.Application.Execution.Exception.Builder builder =
        Event.Application.Execution.Exception.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "causedBy":
          builder.setCausedBy(parseEventExecutionException(jsonReader));
          break;
        case "frames":
          builder.setFrames(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseEventFrame));
          break;
        case "overflowCount":
          builder.setOverflowCount(jsonReader.nextInt());
          break;
        case "type":
          builder.setType(jsonReader.nextString());
          break;
        case "reason":
          builder.setReason(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application.Execution.Signal parseEventSignal(@NonNull JsonReader jsonReader)
      throws IOException {
    final Event.Application.Execution.Signal.Builder builder =
        Event.Application.Execution.Signal.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "name":
          builder.setName(jsonReader.nextString());
          break;
        case "code":
          builder.setCode(jsonReader.nextString());
          break;
        case "address":
          builder.setAddress(jsonReader.nextLong());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application.Execution.BinaryImage parseEventBinaryImage(
      @NonNull JsonReader jsonReader) throws IOException {
    final Event.Application.Execution.BinaryImage.Builder builder =
        Event.Application.Execution.BinaryImage.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "name":
          builder.setName(jsonReader.nextString());
          break;
        case "baseAddress":
          builder.setBaseAddress(jsonReader.nextLong());
          break;
        case "size":
          builder.setSize(jsonReader.nextLong());
          break;
        case "uuid":
          builder.setUuidFromUtf8Bytes(Base64.decode(jsonReader.nextString(), Base64.NO_WRAP));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application.Execution.Thread parseEventThread(@NonNull JsonReader jsonReader)
      throws IOException {
    final Event.Application.Execution.Thread.Builder builder =
        Event.Application.Execution.Thread.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "importance":
          builder.setImportance(jsonReader.nextInt());
          break;
        case "name":
          builder.setName(jsonReader.nextString());
          break;
        case "frames":
          builder.setFrames(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseEventFrame));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Application.Execution.Thread.Frame parseEventFrame(
      @NonNull JsonReader jsonReader) throws IOException {
    final Event.Application.Execution.Thread.Frame.Builder builder =
        Event.Application.Execution.Thread.Frame.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "importance":
          builder.setImportance(jsonReader.nextInt());
          break;
        case "file":
          builder.setFile(jsonReader.nextString());
          break;
        case "offset":
          builder.setOffset(jsonReader.nextLong());
          break;
        case "pc":
          builder.setPc(jsonReader.nextLong());
          break;
        case "symbol":
          builder.setSymbol(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Device parseEventDevice(@NonNull JsonReader jsonReader) throws IOException {
    final Event.Device.Builder builder = Event.Device.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "batteryLevel":
          builder.setBatteryLevel(jsonReader.nextDouble());
          break;
        case "batteryVelocity":
          builder.setBatteryVelocity(jsonReader.nextInt());
          break;
        case "diskUsed":
          builder.setDiskUsed(jsonReader.nextLong());
          break;
        case "proximityOn":
          builder.setProximityOn(jsonReader.nextBoolean());
          break;
        case "orientation":
          builder.setOrientation(jsonReader.nextInt());
          break;
        case "ramUsed":
          builder.setRamUsed(jsonReader.nextLong());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.Log parseEventLog(@NonNull JsonReader jsonReader) throws IOException {
    final Event.Log.Builder builder = Event.Log.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      if (name.equals("content")) {
        builder.setContent(jsonReader.nextString());
      } else {
        jsonReader.skipValue();
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.RolloutsState parseEventRolloutsState(@NonNull JsonReader jsonReader)
      throws IOException {
    Event.RolloutsState.Builder builder = Event.RolloutsState.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "assignments":
          builder.setRolloutAssignments(
              parseArray(jsonReader, CrashlyticsReportJsonTransform::parseEventRolloutsAssignment));
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.RolloutAssignment parseEventRolloutsAssignment(
      @NonNull JsonReader jsonReader) throws IOException {
    Event.RolloutAssignment.Builder builder = Event.RolloutAssignment.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "rolloutVariant":
          builder.setRolloutVariant(parseRolloutAssignmentRolloutVariant(jsonReader));
          break;
        case "parameterKey":
          builder.setParameterKey(jsonReader.nextString());
          break;
        case "parameterValue":
          builder.setParameterValue(jsonReader.nextString());
          break;
        case "templateVersion":
          builder.setTemplateVersion(jsonReader.nextLong());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static Event.RolloutAssignment.RolloutVariant parseRolloutAssignmentRolloutVariant(
      @NonNull JsonReader jsonReader) throws IOException {
    Event.RolloutAssignment.RolloutVariant.Builder builder =
        Event.RolloutAssignment.RolloutVariant.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "rolloutId":
          builder.setRolloutId(jsonReader.nextString());
          break;
        case "variantId":
          builder.setVariantId(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static CustomAttribute parseCustomAttribute(@NonNull JsonReader jsonReader)
      throws IOException {
    final CustomAttribute.Builder builder = CustomAttribute.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "key":
          builder.setKey(jsonReader.nextString());
          break;
        case "value":
          builder.setValue(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static BuildIdMappingForArch parseBuildIdMappingForArch(@NonNull JsonReader jsonReader)
      throws IOException {
    BuildIdMappingForArch.Builder builder = BuildIdMappingForArch.builder();

    jsonReader.beginObject();
    while (jsonReader.hasNext()) {
      String name = jsonReader.nextName();
      switch (name) {
        case "libraryName":
          builder.setLibraryName(jsonReader.nextString());
          break;
        case "arch":
          builder.setArch(jsonReader.nextString());
          break;
        case "buildId":
          builder.setBuildId(jsonReader.nextString());
          break;
        default:
          jsonReader.skipValue();
          break;
      }
    }
    jsonReader.endObject();
    return builder.build();
  }

  @NonNull
  private static <T> List<T> parseArray(
      @NonNull JsonReader jsonReader, @NonNull ObjectParser<T> objectParser) throws IOException {
    final List<T> objects = new ArrayList<>();

    jsonReader.beginArray();
    while (jsonReader.hasNext()) {
      objects.add(objectParser.parse(jsonReader));
    }
    jsonReader.endArray();

    return Collections.unmodifiableList(objects);
  }

  private interface ObjectParser<T> {
    T parse(@NonNull JsonReader jsonReader) throws IOException;
  }
}
