// Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.internal.persistence;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.ProfilingTrigger;
import androidx.test.filters.SdkSuppress;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.Test;

public class ResumableUploadMetadataStoreTest extends CrashlyticsTestCase {
  FileStore fileStore = mock(FileStore.class);
  Context context = getContext();

  @Test
  public void testSerializeDeserialize_noHandle() {
    String sessionId = "sessionId";
    int type = ProfilingTrigger.TRIGGER_TYPE_ANOMALY;
    String path = "heapdump.perfetto";

    ResumableUploadMetadataStore.InProgressUploadMetadata metadata =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(sessionId, type, path);

    String serialized = ResumableUploadMetadataStore.serialize(metadata);

    assertThat(serialized).isEqualTo("sessionId,8,null,heapdump.perfetto");

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> deserialized =
        ResumableUploadMetadataStore.parseInProgressUploadLine(serialized);

    assertThat(deserialized).hasValue(metadata);
  }

  @Test
  public void testSerializeDeserialize_withHandle() {
    String sessionId = "sessionId";
    int type = ProfilingTrigger.TRIGGER_TYPE_ANOMALY;
    String path = "heapdump.perfetto";
    String handle = "http://some.upload.url/for/scotty";

    ResumableUploadMetadataStore.InProgressUploadMetadata metadata =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(sessionId, type, handle, path);

    String serialized = ResumableUploadMetadataStore.serialize(metadata);

    assertThat(serialized)
        .isEqualTo("sessionId,8,http://some.upload.url/for/scotty,heapdump.perfetto");

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> deserialized =
        ResumableUploadMetadataStore.parseInProgressUploadLine(serialized);

    assertThat(deserialized).hasValue(metadata);
  }

  @Test
  public void testSerializeDeserialize_withHandleAndCommaPath() {
    String sessionId = "sessionId";
    int type = ProfilingTrigger.TRIGGER_TYPE_ANOMALY;
    String path = "/,/oops/,/heapdump.perfetto";
    String handle = "http://some.upload.url/for/scotty";

    ResumableUploadMetadataStore.InProgressUploadMetadata metadata =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(sessionId, type, handle, path);

    String serialized = ResumableUploadMetadataStore.serialize(metadata);

    assertThat(serialized)
        .isEqualTo("sessionId,8,http://some.upload.url/for/scotty,/,/oops/,/heapdump.perfetto");

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> deserialized =
        ResumableUploadMetadataStore.parseInProgressUploadLine(serialized);

    assertThat(deserialized.isPresent()).isTrue();
    assertThat(deserialized.get().sessionId).isEqualTo(sessionId);
    assertThat(deserialized.get().type).isEqualTo(type);
    assertThat(deserialized.get().path).isEqualTo(path);
    assertThat(deserialized.get().handle).isEqualTo(handle);

    assertThat(deserialized).hasValue(metadata);
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testGetUpload_noDuplicatesMetadataFileDoesNotExist() throws IOException {
    ResumableUploadMetadataStore.InProgressUploadMetadata existing =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId1", ProfilingTrigger.TRIGGER_TYPE_OOM, "path1");

    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    File mockFile = mock(File.class);
    when(mockFile.exists()).thenReturn(false);

    when(fileStore.getCommonFile("out-of-band-uploads"))
        .thenReturn(mockFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    assertThat(store.addInProgressUpload(existing.sessionId, existing.type, existing.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing.sessionId, existing.type, existing.path))
        .isFalse();
    assertThat(store.addInProgressUpload(existing.sessionId, existing.type, existing.path))
        .isFalse();
    assertThat(store.addInProgressUpload(existing.sessionId, existing.type, existing.path))
        .isFalse();
    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();

    assertThat(current.isPresent()).isTrue();
    verify(fileStore, times(2)).getCommonFile("out-of-band-uploads");

    assertThat(current.get().sessionId).isEqualTo(existing.sessionId);
    assertThat(current.get().path).isEqualTo(existing.path);
    assertThat(current.get().type).isEqualTo(existing.type);
    assertThat(current.get().handle).isNull();
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testGetUpload_oneItemNoHandleMetadataFileDoesNotExist() throws IOException {
    ResumableUploadMetadataStore.InProgressUploadMetadata existing =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId1", ProfilingTrigger.TRIGGER_TYPE_OOM, "path1");

    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    File mockFile = mock(File.class);
    when(mockFile.exists()).thenReturn(false);

    when(fileStore.getCommonFile("out-of-band-uploads"))
        .thenReturn(mockFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    assertThat(store.addInProgressUpload(existing.sessionId, existing.type, existing.path))
        .isTrue();
    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();

    assertThat(current.isPresent()).isTrue();
    verify(fileStore, times(2)).getCommonFile("out-of-band-uploads");

    assertThat(current.get().sessionId).isEqualTo(existing.sessionId);
    assertThat(current.get().path).isEqualTo(existing.path);
    assertThat(current.get().type).isEqualTo(existing.type);
    assertThat(current.get().handle).isNull();
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testGetUpload_manyItemsNoHandleMetadataFileDoesNotExist() throws IOException {
    ResumableUploadMetadataStore.InProgressUploadMetadata existing1 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId1", ProfilingTrigger.TRIGGER_TYPE_OOM, "path1");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing2 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId2", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "path2");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing3 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId3", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "path3");

    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    File mockFile = mock(File.class);
    when(mockFile.exists()).thenReturn(false);

    when(fileStore.getCommonFile("out-of-band-uploads"))
        .thenReturn(mockFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    assertThat(store.addInProgressUpload(existing1.sessionId, existing1.type, existing1.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing2.sessionId, existing2.type, existing2.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing3.sessionId, existing3.type, existing3.path))
        .isTrue();

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();

    assertThat(current.isPresent()).isTrue();
    verify(fileStore, times(4)).getCommonFile("out-of-band-uploads");

    assertThat(current.get().sessionId).isEqualTo(existing1.sessionId);
    assertThat(current.get().path).isEqualTo(existing1.path);
    assertThat(current.get().type).isEqualTo(existing1.type);
    assertThat(current.get().handle).isNull();
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testGetUpload_manyItemsWithHandleMetadataFileDoesNotExist() throws IOException {
    ResumableUploadMetadataStore.InProgressUploadMetadata existing1 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId1", ProfilingTrigger.TRIGGER_TYPE_OOM, "path1");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing2 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId2", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "handle2", "path2");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing3 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId3", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "path3");

    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    File mockFile = mock(File.class);
    when(mockFile.exists()).thenReturn(false);

    when(fileStore.getCommonFile("out-of-band-uploads"))
        .thenReturn(mockFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    assertThat(store.addInProgressUpload(existing1.sessionId, existing1.type, existing1.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing2.sessionId, existing2.type, existing2.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing3.sessionId, existing3.type, existing3.path))
        .isTrue();

    assertThat(store.updateHandle(existing2.path, existing2.handle)).isTrue();

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();

    assertThat(current.isPresent()).isTrue();
    verify(fileStore, times(5)).getCommonFile("out-of-band-uploads");

    assertThat(current.get().sessionId).isEqualTo(existing2.sessionId);
    assertThat(current.get().path).isEqualTo(existing2.path);
    assertThat(current.get().type).isEqualTo(existing2.type);
    assertThat(current.get().handle).isEqualTo(existing2.handle);
  }

  @Test
  public void testAddInProgressUpload_returnsFalseForNonMainProcess()
      throws NoSuchFieldException, IllegalAccessException {
    ResumableUploadMetadataStore.InProgressUploadMetadata existing1 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId1", ProfilingTrigger.TRIGGER_TYPE_OOM, "path1");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing2 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId2", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "handle2", "path2");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing3 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId3", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "path3");

    File mockFile = mock(File.class);
    when(mockFile.exists()).thenReturn(false);

    when(fileStore.getCommonFile("out-of-band-uploads")).thenReturn(mockFile);

    Context context = mock(Context.class);
    ApplicationInfo applicationInfo = mock(ApplicationInfo.class);

    Field field = ApplicationInfo.class.getDeclaredField("processName");
    field.setAccessible(true);
    field.set(applicationInfo, "NotTheMainProcessProcess");

    when(context.getApplicationInfo()).thenReturn(applicationInfo);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    assertThat(store.addInProgressUpload(existing1.sessionId, existing1.type, existing1.path))
        .isFalse();
    assertThat(store.addInProgressUpload(existing2.sessionId, existing2.type, existing2.path))
        .isFalse();
    assertThat(store.addInProgressUpload(existing3.sessionId, existing3.type, existing3.path))
        .isFalse();
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testRemoveInProgressUpload_uploadExists() throws IOException {
    ResumableUploadMetadataStore.InProgressUploadMetadata existing1 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId1", ProfilingTrigger.TRIGGER_TYPE_OOM, "path1");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing2 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId2", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "handle2", "path2");
    ResumableUploadMetadataStore.InProgressUploadMetadata existing3 =
        new ResumableUploadMetadataStore.InProgressUploadMetadata(
            "sessionId3", ProfilingTrigger.TRIGGER_TYPE_ANOMALY, "path3");

    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    File mockFile = mock(File.class);
    when(mockFile.exists()).thenReturn(false);

    when(fileStore.getCommonFile("out-of-band-uploads"))
        .thenReturn(mockFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile)
        .thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    assertThat(store.addInProgressUpload(existing1.sessionId, existing1.type, existing1.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing2.sessionId, existing2.type, existing2.path))
        .isTrue();
    assertThat(store.addInProgressUpload(existing3.sessionId, existing3.type, existing3.path))
        .isTrue();

    assertThat(store.updateHandle(existing2.path, existing2.handle)).isTrue();

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();

    assertThat(current.isPresent()).isTrue();
    verify(fileStore, times(5)).getCommonFile("out-of-band-uploads");

    assertThat(current.get().sessionId).isEqualTo(existing2.sessionId);
    assertThat(current.get().path).isEqualTo(existing2.path);
    assertThat(current.get().type).isEqualTo(existing2.type);
    assertThat(current.get().handle).isEqualTo(existing2.handle);

    assertThat(store.removeInProgressUpload(existing2.path)).isTrue();

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> newCurrent = store.getUpload();

    assertThat(newCurrent.isPresent()).isTrue();

    assertThat(newCurrent.get().sessionId).isEqualTo(existing1.sessionId);
    assertThat(newCurrent.get().path).isEqualTo(existing1.path);
    assertThat(newCurrent.get().type).isEqualTo(existing1.type);
    assertThat(newCurrent.get().handle).isNull();
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testParseMalformedMetadataFile() throws IOException {
    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    // Write malformed content
    // Valid trigger types are 7 (OOM) and 8 (ANOMALY) based on ProfilingTrigger
    // Let's use 1 for valid and something else for invalid.
    String content =
        "malformed,content\nvalidSession,7,handle,path\ninvalidType,99,handle,path\ntooFewTokens,1,handle";
    Files.write(metadataFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

    when(fileStore.getCommonFile("out-of-band-uploads")).thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();
    assertThat(current.isPresent()).isTrue();
    assertThat(current.get().sessionId).isEqualTo("validSession");
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testEmptyMetadataFile() throws IOException {
    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    when(fileStore.getCommonFile("out-of-band-uploads")).thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    Optional<ResumableUploadMetadataStore.InProgressUploadMetadata> current = store.getUpload();
    assertThat(current.isPresent()).isFalse();
  }

  @Test
  @SdkSuppress(minSdkVersion = 37)
  public void testFlushFailureRecovery() throws IOException {
    // Simulate write failure by making the file read-only
    Path metadataFilePath = Files.createTempFile("test", ".test");
    File metadataFile = metadataFilePath.toFile();
    metadataFile.deleteOnExit();

    // Make it read-only to cause IOException on write
    assertThat(metadataFile.setWritable(false)).isTrue();

    when(fileStore.getCommonFile("out-of-band-uploads")).thenReturn(metadataFile);

    ResumableUploadMetadataStore store = new ResumableUploadMetadataStore(context, fileStore);

    // Try to add upload, should fail and return false
    boolean result = store.addInProgressUpload("sessionId", 1, "path");
    assertThat(result).isFalse();

    // Reset permissions so it can be deleted
    assertThat(metadataFile.setWritable(true)).isTrue();
  }
}
