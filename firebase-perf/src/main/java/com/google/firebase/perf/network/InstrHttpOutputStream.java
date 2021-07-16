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
import java.io.OutputStream;

/** Instrument the Output Stream Request with UrlConnection */
public final class InstrHttpOutputStream extends OutputStream {

  private final OutputStream outputStream;
  private final Timer timer;

  NetworkRequestMetricBuilder networkMetricBuilder;

  long bytesWritten = -1;

  public InstrHttpOutputStream(
      final OutputStream outputStream, NetworkRequestMetricBuilder builder, Timer timer) {
    this.outputStream = outputStream;
    networkMetricBuilder = builder;
    this.timer = timer;
  }

  @Override
  public void close() throws IOException {
    if (bytesWritten != -1) {
      networkMetricBuilder.setRequestPayloadBytes(bytesWritten);
    }
    networkMetricBuilder.setTimeToRequestCompletedMicros(timer.getDurationMicros());
    try {
      outputStream.close();
    } catch (IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void flush() throws IOException {
    try {
      outputStream.flush();
    } catch (IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void write(int b) throws IOException {
    try {
      outputStream.write(b);
      bytesWritten++;
      networkMetricBuilder.setRequestPayloadBytes(bytesWritten);
    } catch (IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    try {
      outputStream.write(b);
      bytesWritten += b.length;
      networkMetricBuilder.setRequestPayloadBytes(bytesWritten);
    } catch (IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    try {
      outputStream.write(b, off, len);
      bytesWritten += len;
      networkMetricBuilder.setRequestPayloadBytes(bytesWritten);
    } catch (IOException e) {
      networkMetricBuilder.setTimeToResponseCompletedMicros(timer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(networkMetricBuilder);
      throw e;
    }
  }
}
