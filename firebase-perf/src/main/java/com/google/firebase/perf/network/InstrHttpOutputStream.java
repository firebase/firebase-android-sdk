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
import java.io.OutputStream;

/** Instrument the Output Stream Request with UrlConnection */
public final class InstrHttpOutputStream extends OutputStream {

  private OutputStream mOutputStream;
  long mBytesWritten = -1;
  NetworkRequestMetricBuilder mBuilder;
  private final Timer mTimer;

  public InstrHttpOutputStream(
      final OutputStream outputStream, NetworkRequestMetricBuilder builder, Timer timer) {
    mOutputStream = outputStream;
    mBuilder = builder;
    mTimer = timer;
  }

  @Override
  public void close() throws IOException {
    if (mBytesWritten != -1) {
      mBuilder.setRequestPayloadBytes(mBytesWritten);
    }
    mBuilder.setTimeToRequestCompletedMicros(mTimer.getDurationMicros());
    try {
      mOutputStream.close();
    } catch (IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void flush() throws IOException {
    try {
      mOutputStream.flush();
    } catch (IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void write(int b) throws IOException {
    try {
      mOutputStream.write(b);
      mBytesWritten++;
      mBuilder.setRequestPayloadBytes(mBytesWritten);
    } catch (IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void write(byte[] b) throws IOException {
    try {
      mOutputStream.write(b);
      mBytesWritten += b.length;
      mBuilder.setRequestPayloadBytes(mBytesWritten);
    } catch (IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    try {
      mOutputStream.write(b, off, len);
      mBytesWritten += len;
      mBuilder.setRequestPayloadBytes(mBytesWritten);
    } catch (IOException e) {
      mBuilder.setTimeToResponseCompletedMicros(mTimer.getDurationMicros());
      NetworkRequestMetricBuilderUtil.logError(mBuilder);
      throw e;
    }
  }
}
