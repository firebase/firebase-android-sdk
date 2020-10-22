// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import android.content.Context;
import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.VisibleForTesting;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * File-backed storage client for managing {@link ConfigContainer}s in disk.
 *
 * <p>At most one instance of this class exists for any given file, so all calls to {@link
 * #getInstance(Context, String)} with the same context and file name will return the same instance.
 *
 * <p>Since there's a one to one mapping between files and storage clients, and every method in the
 * client is synchronized, two threads in the same process should never write to the same file
 * simultaneously.
 *
 * @author Miraziz Yusupov
 */
@AnyThread
public class ConfigStorageClient {
  @GuardedBy("ConfigStorageClient.class")
  private static final Map<String, ConfigStorageClient> clientInstances = new HashMap<>();

  private static final String JSON_STRING_ENCODING = "UTF-8";

  private final Context context;
  private final String fileName;

  /** Creates a new storage client backed by the specified file. */
  private ConfigStorageClient(Context context, String fileName) {
    this.context = context;
    this.fileName = fileName;
  }

  /**
   * Writes the {@link ConfigContainer} to disk.
   *
   * <p>Writes are non-atomic, so, if the write fails, the disk will likely be corrupted.
   *
   * <p>Possible reasons for failures while writing include:
   *
   * <ul>
   *   <li>Out of disk space
   *   <li>Power outage
   *   <li>Process termination
   * </ul>
   *
   * @return {@link Void} because {@link com.google.android.gms.tasks.Tasks#call(Callable)} requires
   *     a non-void return value.
   * @throws IOException if the file write fails.
   */
  public synchronized Void write(ConfigContainer container) throws IOException {
    // TODO(issues/262): Consider using the AtomicFile class instead.
    FileOutputStream outputStream = context.openFileOutput(fileName, Context.MODE_PRIVATE);
    try {
      outputStream.write(container.toString().getBytes(JSON_STRING_ENCODING));
    } finally {
      outputStream.close();
    }
    return null;
  }

  /**
   * Reads and returns the {@link ConfigContainer} stored in disk.
   *
   * @return a valid {@link ConfigContainer} or null if the file was corrupt or not found.
   * @throws IOException if the file read fails.
   */
  @Nullable
  public synchronized ConfigContainer read() throws IOException {

    FileInputStream fileInputStream = null;
    try {
      fileInputStream = context.openFileInput(fileName);
      byte[] bytes = new byte[fileInputStream.available()];
      fileInputStream.read(bytes, 0, bytes.length);
      String containerJsonString = new String(bytes, JSON_STRING_ENCODING);

      JSONObject containerJson = new JSONObject(containerJsonString);
      return ConfigContainer.copyOf(containerJson);
    } catch (JSONException | FileNotFoundException e) {
      // File might not have been written to yet, so this not an irrecoverable error.
      return null;
    } finally {
      if (fileInputStream != null) fileInputStream.close();
    }
  }

  /**
   * Clears the {@link ConfigContainer} in disk.
   *
   * @return {@link Void} because {@link com.google.android.gms.tasks.Tasks#call(Callable)} requires
   *     a non-void return value.
   */
  public synchronized Void clear() {
    context.deleteFile(fileName);
    return null;
  }

  /**
   * Returns an instance of {@link ConfigStorageClient} for the given context and file name. The
   * same instance is always returned for all calls with the same file name.
   */
  public static synchronized ConfigStorageClient getInstance(Context context, String fileName) {
    if (!clientInstances.containsKey(fileName)) {
      clientInstances.put(fileName, new ConfigStorageClient(context, fileName));
    }
    return clientInstances.get(fileName);
  }

  @VisibleForTesting
  public static synchronized void clearInstancesForTest() {
    clientInstances.clear();
  }

  /** Returns the name of the file associated with this storage client. */
  String getFileName() {
    return fileName;
  }
}
