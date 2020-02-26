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

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/** Use file locking to acquire a lock that will also block other processes. */
class CrossProcessLock {
  private final FileChannel channel;
  private final FileLock lock;

  private CrossProcessLock(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  static CrossProcessLock acquire(Context appContext, String lockName) {
    try {
      File file = new File(appContext.getFilesDir(), lockName);
      FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
      // Use the file channel to create a lock on the file.
      // This method blocks until it can retrieve the lock.
      FileLock lock = channel.lock();
      return new CrossProcessLock(channel, lock);
    } catch (FileNotFoundException e) {
      // Firebase Installations will silently fail if the disk is full or can not be accessed for other reasons.
      // This has been done to temporarily quick-fix apps from crash-looping on devices with full disk.
      // Presumably the SDK will throw error (instead of an exception) later when it cannot persist the FID and data.
    } catch (IOException e) {
      throw new IllegalStateException("exception while using file locks, should never happen", e);
    }
  }

  /** Release a previously acquired lock and free any underlying resources. */
  void releaseAndClose() {
    try {
      lock.release();
      channel.close();
    } catch (IOException e) {
      throw new IllegalStateException("exception while using file locks, should never happen", e);
    }
  }
}
