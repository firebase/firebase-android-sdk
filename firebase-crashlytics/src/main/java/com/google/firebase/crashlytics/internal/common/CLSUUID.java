// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A UUID whose lexicographical ordering is equivalent to a temporal ordering. Uniqueness is based
 * on timestamp, sequence identifier, processId, and a random hash.
 */
class CLSUUID {

  // Padding to ensure the id is long enough, has only valid hex characters, and increase entropy.
  private static final String ID_SHA =
      CommonUtils.sha1(UUID.randomUUID().toString() + System.currentTimeMillis());

  private static final AtomicLong sequenceNumber = new AtomicLong(0);
  private final String sessionId;

  CLSUUID() {
    byte[] bytes = new byte[10];

    this.populateTime(bytes);
    this.populateSequenceNumber(bytes);
    this.populatePID(bytes);

    String timeSeqPid = CommonUtils.hexify(bytes);

    sessionId =
        String.format(
                Locale.US,
                "%s%s%s%s",
                timeSeqPid.substring(0, 12),
                timeSeqPid.substring(12, 16),
                timeSeqPid.subSequence(16, 20),
                ID_SHA.substring(0, 12))
            .toUpperCase(Locale.US);
  }

  public String getSessionId() {
    return sessionId;
  }

  private void populateTime(byte[] bytes) {
    final Date date = new Date();
    final long time = date.getTime();
    final long tvSec = time / 1000;
    final long tvUsec = time % 1000;
    final byte[] timeBytes = convertLongToFourByteBuffer(tvSec);
    bytes[0] = timeBytes[0];
    bytes[1] = timeBytes[1];
    bytes[2] = timeBytes[2];
    bytes[3] = timeBytes[3];

    final byte[] msecsBytes = convertLongToTwoByteBuffer(tvUsec);
    bytes[4] = msecsBytes[0];
    bytes[5] = msecsBytes[1];
  }

  private void populateSequenceNumber(byte[] bytes) {
    byte[] sequenceBytes = convertLongToTwoByteBuffer(sequenceNumber.incrementAndGet());
    bytes[6] = sequenceBytes[0];
    bytes[7] = sequenceBytes[1];
  }

  private void populatePID(byte[] bytes) {
    final Integer pid = Integer.valueOf(android.os.Process.myPid());
    final byte[] pidBytes = convertLongToTwoByteBuffer(pid.shortValue());
    bytes[8] = pidBytes[0];
    bytes[9] = pidBytes[1];
  }

  private static byte[] convertLongToFourByteBuffer(long value) {
    final ByteBuffer buf = ByteBuffer.allocate(4);
    buf.putInt((int) value);
    buf.order(ByteOrder.BIG_ENDIAN);
    buf.position(0);

    return buf.array();
  }

  private static byte[] convertLongToTwoByteBuffer(long value) {
    final ByteBuffer buf = ByteBuffer.allocate(2);
    buf.putShort((short) value);
    buf.order(ByteOrder.BIG_ENDIAN);
    buf.position(0);

    return buf.array();
  }

  @Override
  public String toString() {
    return sessionId;
  }
}
