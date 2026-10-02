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

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.ProfilingTrigger;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ResumableUploadMetadataStore {
  private static final String METADATA_FILENAME = "out-of-band-uploads";

  private final Context context;

  private final FileStore fileStore;

  private final List<InProgressUploadMetadata> uploads;

  public static class InProgressUploadMetadata {

    @NonNull public final String sessionId;
    public final int type;

    @Nullable public String handle;

    @NonNull public final String path;

    public InProgressUploadMetadata(
        @NonNull String sessionId, int type, @NonNull String handle, @NonNull String path) {
      this.sessionId = sessionId;
      this.type = type;
      this.handle = handle;
      this.path = path;
    }

    public InProgressUploadMetadata(@NonNull String sessionId, int type, @NonNull String path) {
      this.sessionId = sessionId;
      this.type = type;
      this.handle = null;
      this.path = path;
    }

    public void setHandle(@Nullable String handle) {
      this.handle = handle;
    }

    public boolean isInProgress() {
      return handle != null;
    }

    public boolean isNew() {
      return handle == null;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof InProgressUploadMetadata)) return false;
      InProgressUploadMetadata metadata = (InProgressUploadMetadata) o;
      return type == metadata.type
          && Objects.equals(sessionId, metadata.sessionId)
          && Objects.equals(handle, metadata.handle)
          && Objects.equals(path, metadata.path);
    }

    @Override
    public int hashCode() {
      return Objects.hash(sessionId, type, handle, path);
    }
  }

  public ResumableUploadMetadataStore(Context context, FileStore fileStore) {
    this.context = context;
    this.fileStore = fileStore;

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
      this.uploads = new CopyOnWriteArrayList<>(getInProgressUploads(fileStore));
    } else {
      this.uploads = new CopyOnWriteArrayList<>();
    }
  }

  public Optional<InProgressUploadMetadata> getUpload() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && isDefaultProcess(context)) {
      // Find the oldest in-progress upload. An in progress upload will contain a non-null handle
      return uploads.reversed().stream()
          .filter(InProgressUploadMetadata::isInProgress)
          .findFirst()
          // If no uploads have a valid handle, get the oldest upload
          .or(() -> Optional.ofNullable(uploads.getLast()));
    }
    return Optional.empty();
  }

  public boolean addInProgressUpload(@NonNull String sessionId, int type, @NonNull String path) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN && isDefaultProcess(context)) {
      if (uploads.stream().noneMatch(upload -> upload.path.equals(path))) {
        InProgressUploadMetadata newUpload = new InProgressUploadMetadata(sessionId, type, path);
        uploads.add(
            0, newUpload); // Add to front (oldest first logic in getUpload expects this order)

        if (!flush(fileStore, uploads)) {
          Logger.getLogger().e("Unable to add a new in progress upload and flush");
          uploads.remove(newUpload);
          return false;
        }

        return true;
      }
    }

    return false;
  }

  public boolean updateHandle(@NonNull String path, @NonNull String handle) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
      return uploads.stream()
          .filter(upload -> upload.path.equals(path))
          .findFirst()
          .map(
              upload -> {
                upload.setHandle(handle);

                if (!flush(fileStore, uploads)) {
                  Logger.getLogger().e("Unable to update handle and flush an in progress upload");
                  upload.setHandle(null);
                  return false;
                }

                return true;
              })
          .orElse(false);
    }

    return false;
  }

  public boolean removeInProgressUpload(@NonNull String path) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
      if (uploads.removeIf(upload -> upload.path.equals(path))) {
        if (!flush(fileStore, uploads)) {
          Logger.getLogger().e("Unable to remove and flush an in progress upload");
          return false;
        }

        return true;
      }
    }

    return false;
  }

  @RequiresApi(api = 37)
  private static List<InProgressUploadMetadata> getInProgressUploads(FileStore fileStore) {
    File metadata = fileStore.getCommonFile(METADATA_FILENAME);

    return metadata.exists() ? parseInProgressUploadMetadata(metadata) : new ArrayList<>();
  }

  @SuppressWarnings({"BooleanMethodIsAlwaysInverted"})
  @RequiresApi(api = 37)
  private static boolean flush(FileStore fileStore, List<InProgressUploadMetadata> uploads) {
    File metadata = fileStore.getCommonFile(METADATA_FILENAME);

    if (!metadata.exists() && !touch(fileStore)) {
      return false;
    }

    try {
      String content = serialize(uploads);
      Files.write(metadata.toPath(), content.getBytes());
      return true;
    } catch (IOException e) {
      Logger.getLogger().e("Unable to flush the in progress upload metadata file!");
    }

    return false;
  }

  @RequiresApi(api = 37)
  private static boolean touch(FileStore fileStore) {
    try {
      File metadata = fileStore.getCommonFile(METADATA_FILENAME);
      return metadata.createNewFile();
    } catch (IOException e) {
      Logger.getLogger().e("Unable to create the in progress upload metadata file!");
    }

    return false;
  }

  @RequiresApi(api = 37)
  private static String serialize(List<InProgressUploadMetadata> uploads) {
    return uploads.stream()
        .map(ResumableUploadMetadataStore::serialize)
        .collect(Collectors.joining("\n"));
  }

  @RequiresApi(api = 37)
  @VisibleForTesting
  static String serialize(InProgressUploadMetadata upload) {
    return String.format("%s,%s,%s,%s", upload.sessionId, upload.type, upload.handle, upload.path);
  }

  @RequiresApi(api = 37)
  private static List<InProgressUploadMetadata> parseInProgressUploadMetadata(File metadataFile) {
    try (Stream<String> stream = Files.lines(metadataFile.toPath())) {
      return stream
          .map(ResumableUploadMetadataStore::parseInProgressUploadLine)
          .flatMap(Optional::stream)
          .collect(Collectors.toList());
    } catch (IOException e) {
      Logger.getLogger().e("Unable to read in-progress upload file");
    }

    return new ArrayList<>();
  }

  @RequiresApi(api = 37)
  @VisibleForTesting
  static Optional<InProgressUploadMetadata> parseInProgressUploadLine(String line) {
    // The format of each line is <session-id>,<trigger-type>,<scotty-handle>,<path>
    String[] tokenized = line.split(",");

    if (tokenized.length < 4
        || !Set.of(
                String.valueOf(ProfilingTrigger.TRIGGER_TYPE_ANOMALY),
                String.valueOf(ProfilingTrigger.TRIGGER_TYPE_OOM))
            .contains(tokenized[1])) {
      return Optional.empty();
    }

    try {
      String sessionId = tokenized[0];

      int type = Integer.parseInt(tokenized[1]);

      String handle = tokenized[2];
      String path = Arrays.stream(tokenized).skip(3).collect(Collectors.joining(","));

      return Optional.of(
          handle.equals("null")
              ? new InProgressUploadMetadata(sessionId, type, path)
              : new InProgressUploadMetadata(sessionId, type, handle, path));
    } catch (NumberFormatException e) {
      Logger.getLogger().e("Malformed line; ", e);
    }

    return Optional.empty();
  }

  @RequiresApi(api = Build.VERSION_CODES.CINNAMON_BUN)
  private boolean isDefaultProcess(Context context) {
    String currentProcess = Application.getProcessName();

    String defaultProcess = context.getApplicationInfo().processName;
    return defaultProcess != null
        ? currentProcess.equals(defaultProcess)
        : currentProcess.equals(context.getPackageName());
  }
}
