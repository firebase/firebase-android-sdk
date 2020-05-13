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

package com.google.firebase.installations;

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/** Use file locking to acquire a lock that will also block other processes. */
class CrossProcessLock {
  private static final String TAG = "CrossProcessLock";

  private final String lockFileName;
  private final File fileDir;

  CrossProcessLock(File fileDir, String lockFileName) {
    this.lockFileName = lockFileName;
    this.fileDir = fileDir;
  }

  interface Producer<T> {
    T produce();
  }

  /**
   * Create a lock that is exclusive across processes. If another process has the lock then this
   * call will block until it is released.
   *
   * @return a CrossProcessLock if success. If the lock failed to acquire (maybe due to disk full or
   *     other unexpected and unsupported permissions) then null will be returned.
   */
  <T> T synchronize(Producer<T> persistedInstallation) {
    File file = new File(fileDir, lockFileName);
    try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");
        FileChannel channel = randomAccessFile.getChannel();
        // Use the file channel to create a lock on the file.
        // This method blocks until it can retrieve the lock.
        FileLock lock = channel.lock()) {

      return persistedInstallation.produce();
    } catch (IOException | Error e) {
      // Certain conditions can cause file locking to fail, such as out of disk or bad permissions.
      // In any case, the acquire will fail and return null instead of a held lock.
      // NOTE: In Java 7 & 8, FileKey creation failure might wrap IOException into Error. See
      // https://bugs.openjdk.java.net/browse/JDK-8025619 for details.
      Log.e(TAG, "Error while creating and acquiring the lock.", e);

      return null;
    }
  }
}
