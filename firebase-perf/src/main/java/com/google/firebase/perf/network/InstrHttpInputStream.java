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

import com.google.firebase.perf.metrics.NetworkRequestMetricBuilder;
import com.google.firebase.perf.util.Timer;
import java.io.IOException;
import java.io.InputStream;

/** Instrument the Input Stream Response with UrlConnection */
public final class InstrHttpInputStream extends InputStream {

  private final InputStream inputStream;
  private final NetworkRequestMetricBuilder networkMetricBuilder;
  private final Timer timer;

  private long bytesRead = -1;
  private long timeToResponseInitiated;
  private long timeToResponseLastRead = -1;

  /**
   * Instrumented inputStream object
   *
   * @param inputStream
   * @param builder
   * @param timer
   */
  public InstrHttpInputStream(
      final InputStream inputStream, final NetworkRequestMetricBuilder builder, Timer timer) {
    this.timer = timer;
    this.inputStream = inputStream;
    networkMetricBuilder = builder;
    timeToResponseInitiated = networkMetricBuilder.getTimeToResponseInitiatedMicros();
  }

  @Override
  public int available() throws IOException {
    try {
      return inputStream.available();
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    long tempTime = timer.getDurationMicros();
    if (timeToResponseLastRead == -1) {
      timeToResponseLastRead = tempTime;
    }

    try {
      inputStream.close();
      if (bytesRead != -1) {
        networkMetricBuilder.setResponsePayloadBytes(bytesRead);
      }
      if (timeToResponseInitiated != -1) {
        networkMetricBuilder.setTimeToResponseInitiatedMicros(timeToResponseInitiated);
      }

      networkMetricBuilder.setTimeToResponseCompletedMicros(timeToResponseLastRead);
      networkMetricBuilder.build();
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void mark(final int readlimit) {
    inputStream.mark(readlimit);
  }

  @Override
  public boolean markSupported() {
    return inputStream.markSupported();
  }

  @Override
  public int read() throws IOException {
    try {
      final int bytesRead = inputStream.read();
      long tempTime = timer.getDurationMicros();
      if (timeToResponseInitiated == -1) {
        timeToResponseInitiated = tempTime;
      }
      if (bytesRead == -1 && timeToResponseLastRead == -1) {
        timeToResponseLastRead = tempTime;
        networkMetricBuilder.setTimeToResponseCompletedMicros(timeToResponseLastRead);
        networkMetricBuilder.build();
      } else {
        this.bytesRead++;
        networkMetricBuilder.setResponsePayloadBytes(this.bytesRead);
      }
      return bytesRead;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public int read(final byte[] buffer, final int byteOffset, final int byteCount)
      throws IOException {
    try {
      final int bytesRead = inputStream.read(buffer, byteOffset, byteCount);
      long tempTime = timer.getDurationMicros();
      if (timeToResponseInitiated == -1) {
        timeToResponseInitiated = tempTime;
      }
      if (bytesRead == -1 && timeToResponseLastRead == -1) {
        timeToResponseLastRead = tempTime;
        networkMetricBuilder.setTimeToResponseCompletedMicros(timeToResponseLastRead);
        networkMetricBuilder.build();
      } else {
        this.bytesRead += bytesRead;
        networkMetricBuilder.setResponsePayloadBytes(this.bytesRead);
      }
      return bytesRead;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public int read(final byte[] buffer) throws IOException {
    try {
      final int bytesRead = inputStream.read(buffer);
      long tempTime = timer.getDurationMicros();
      if (timeToResponseInitiated == -1) {
        timeToResponseInitiated = tempTime;
      }
      if (bytesRead == -1 && timeToResponseLastRead == -1) {
        timeToResponseLastRead = tempTime;
        networkMetricBuilder.setTimeToResponseCompletedMicros(timeToResponseLastRead);
        networkMetricBuilder.build();
      } else {
        this.bytesRead += bytesRead;
        networkMetricBuilder.setResponsePayloadBytes(this.bytesRead);
      }
      return bytesRead;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void reset() throws IOException {
    try {
      inputStream.reset();
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public long skip(final long byteCount) throws IOException {
    try {
      final long skipped = inputStream.skip(byteCount);
      long tempTime = timer.getDurationMicros();
      if (timeToResponseInitiated == -1) {
        timeToResponseInitiated = tempTime;
      }
      if (skipped == -1 && timeToResponseLastRead == -1) {
        timeToResponseLastRead = tempTime;
        networkMetricBuilder.setTimeToResponseCompletedMicros(timeToResponseLastRead);
      } else {
        bytesRead += skipped;
        networkMetricBuilder.setResponsePayloadBytes(bytesRead);
      }
      return skipped;
    } catch (final IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }
}
