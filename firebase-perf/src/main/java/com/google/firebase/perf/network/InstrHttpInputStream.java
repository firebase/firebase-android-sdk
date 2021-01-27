// Copyright 2020 Google LLC
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

package com.google.firebase.perf.network;

import com.google.firebase.perf.impl.NetworkRequestMetricBuilder;
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import java.io.InputStream;

/** Instrument the Input Stream Response with UrlConnection */
public final class InstrHttpInputStream extends InputStream {

  private final InputStream mInputStream;
  private final NetworkRequestMetricBuilder mBuilder;
  private final Timer mTimer;
  private long mBytesRead = -1;
  private long mTimeToResponseInitiated;
  private long mTimeToResponseLastRead = -1;

  /**
   * Instrumented inputStream object
   *
   * @param inputStream
   * @param builder
   * @param timer
   */
  public InstrHttpInputStream(
      final InputStream inputStream, final NetworkRequestMetricBuilder builder, Timer timer) {
    mTimer = timer;
    mInputStream = inputStream;
    mBuilder = builder;
    mTimeToResponseInitiated = mBuilder.getTimeToResponseInitiatedMicros();
  }

  @Override
  public int available() throws IOException {
    try {
      return mInputStream.available();
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    long tempTime = mTimer.getDurationMicros();
    if (mTimeToResponseLastRead == -1) {
      mTimeToResponseLastRead = tempTime;
    }

    try {
      mInputStream.close();
      if (mBytesRead != -1) {
        mBuilder.setResponsePayloadBytes(mBytesRead);
      }
      if (mTimeToResponseInitiated != -1) {
        mBuilder.setTimeToResponseInitiatedMicros(mTimeToResponseInitiated);
      }

      mBuilder.setTimeToResponseCompletedMicros(mTimeToResponseLastRead);
      mBuilder.build();
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void mark(final int readlimit) {
    mInputStream.mark(readlimit);
  }

  @Override
  public boolean markSupported() {
    return mInputStream.markSupported();
  }

  @Override
  public int read() throws IOException {
    try {
      final int bytesRead = mInputStream.read();
      long tempTime = mTimer.getDurationMicros();
      if (mTimeToResponseInitiated == -1) {
        mTimeToResponseInitiated = tempTime;
      }
      if (bytesRead == -1 && mTimeToResponseLastRead == -1) {
        mTimeToResponseLastRead = tempTime;
        mBuilder.setTimeToResponseCompletedMicros(mTimeToResponseLastRead);
        mBuilder.build();
      } else {
        mBytesRead++;
        mBuilder.setResponsePayloadBytes(mBytesRead);
      }
      return bytesRead;
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public int read(final byte[] buffer, final int byteOffset, final int byteCount)
      throws IOException {
    try {
      final int bytesRead = mInputStream.read(buffer, byteOffset, byteCount);
      long tempTime = mTimer.getDurationMicros();
      if (mTimeToResponseInitiated == -1) {
        mTimeToResponseInitiated = tempTime;
      }
      if (bytesRead == -1 && mTimeToResponseLastRead == -1) {
        mTimeToResponseLastRead = tempTime;
        mBuilder.setTimeToResponseCompletedMicros(mTimeToResponseLastRead);
        mBuilder.build();
      } else {
        mBytesRead += bytesRead;
        mBuilder.setResponsePayloadBytes(mBytesRead);
      }
      return bytesRead;
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public int read(final byte[] buffer) throws IOException {
    try {
      final int bytesRead = mInputStream.read(buffer);
      long tempTime = mTimer.getDurationMicros();
      if (mTimeToResponseInitiated == -1) {
        mTimeToResponseInitiated = tempTime;
      }
      if (bytesRead == -1 && mTimeToResponseLastRead == -1) {
        mTimeToResponseLastRead = tempTime;
        mBuilder.setTimeToResponseCompletedMicros(mTimeToResponseLastRead);
        mBuilder.build();
      } else {
        mBytesRead += bytesRead;
        mBuilder.setResponsePayloadBytes(mBytesRead);
      }
      return bytesRead;
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void reset() throws IOException {
    try {
      mInputStream.reset();
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public long skip(final long byteCount) throws IOException {
    try {
      final long skipped = mInputStream.skip(byteCount);
      long tempTime = mTimer.getDurationMicros();
      if (mTimeToResponseInitiated == -1) {
        mTimeToResponseInitiated = tempTime;
      }
      if (skipped == -1 && mTimeToResponseLastRead == -1) {
        mTimeToResponseLastRead = tempTime;
        mBuilder.setTimeToResponseCompletedMicros(mTimeToResponseLastRead);
      } else {
        mBytesRead += skipped;
        mBuilder.setResponsePayloadBytes(mBytesRead);
      }
      return skipped;
    } catch (final IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }
}
