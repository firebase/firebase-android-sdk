// Copyright 2018 Google LLC
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

package com.google.firebase.storage.internal;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;

/**
 * AdaptiveStreamBuffer is a wrapper around InputStream that reads all data into a local buffer. It
 * provides access to the underlying buffer and methods to manipulate its state.
 *
 * <p>This class is not thread-safe.
 */
public class AdaptiveStreamBuffer {
  private static final String TAG = "AdaptiveStreamBuffer";
  private static final Runtime runtime = Runtime.getRuntime();
  private final InputStream source;
  private byte[] buffer;
  private int availableBytes;
  private boolean reachedEnd;
  private boolean adaptiveMode;

  public AdaptiveStreamBuffer(InputStream source, int initialBufferSize) {
    this.source = source;
    this.buffer = new byte[initialBufferSize];
    this.availableBytes = 0;
    this.adaptiveMode = true;
    this.reachedEnd = false;
  }

  /** Returns the number of available bytes in the buffer. */
  public int available() {
    return availableBytes;
  }

  /** Returns a direct pointer to the underlying buffer. */
  public byte[] get() {
    return buffer;
  }

  /**
   * Moves the buffer forward by 'bytes' and disregards its data.
   *
   * @param bytes Number of bytes to advance.
   * @return The number of bytes we were able to advance.
   */
  public int advance(int bytes) throws IOException {
    int bytesAdvanced;

    if (bytes <= availableBytes) {
      availableBytes -= bytes;
      System.arraycopy(buffer, bytes, buffer, 0, availableBytes);
      bytesAdvanced = bytes;
    } else {
      // We disregard all bytes in the buffer before advancing the underlying stream.
      availableBytes = 0;
      bytesAdvanced = availableBytes;

      while (bytesAdvanced < bytes) {
        int currentSkip = (int) source.skip(bytes - bytesAdvanced);

        if (currentSkip > 0) {
          bytesAdvanced += currentSkip;
        } else if (currentSkip == 0) {
          // skip() can return 0 when it has no more data cached locally, even though the stream
          // has not yet reached its end.
          if (source.read() == -1) {
            break;
          } else {
            ++bytesAdvanced;
          }
        }
      }
    }

    return bytesAdvanced;
  }

  /**
   * Load the buffer with up to 'targetSize' number of bytes. Actual load may be higher or lower
   * than requested.
   *
   * @param targetSize Number of bytes that should be loaded into the buffer.
   * @return Number of bytes actually in buffer.
   */
  public int fill(int targetSize) throws IOException {
    if (targetSize > buffer.length) {
      targetSize = Math.min(targetSize, resize(targetSize));
    }

    while (availableBytes < targetSize) {
      int currentRead = source.read(buffer, availableBytes, targetSize - availableBytes);
      if (currentRead == -1) {
        reachedEnd = true;
        break;
      } else {
        availableBytes += currentRead;
      }
    }

    return availableBytes;
  }

  private int resize(int targetSize) {
    int newBufferSize = Math.max(buffer.length * 2, targetSize);

    long currentFootprint = runtime.totalMemory() - runtime.freeMemory();
    long availableMemory = runtime.maxMemory() - currentFootprint;

    if (adaptiveMode && newBufferSize < availableMemory) {
      try {
        byte[] chunkBuffer = new byte[newBufferSize];
        System.arraycopy(buffer, 0, chunkBuffer, 0, availableBytes);
        buffer = chunkBuffer;
      } catch (OutOfMemoryError e) {
        Log.w(TAG, "Turning off adaptive buffer resizing due to low memory.");
        adaptiveMode = false;
      }
    } else {
      Log.w(TAG, "Turning off adaptive buffer resizing to conserve memory.");
    }

    return buffer.length;
  }

  /**
   * Whether we have reached the end of the stream and there is no more data to put into the buffer.
   *
   * @return Stream end reached.
   */
  public boolean isFinished() {
    return reachedEnd;
  }

  /** Close the underlying stream. */
  public void close() throws IOException {
    source.close();
  }
}
