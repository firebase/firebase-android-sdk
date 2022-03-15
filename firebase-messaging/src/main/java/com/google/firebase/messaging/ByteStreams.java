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

package com.google.firebase.messaging;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;

/** @hide */
public final class ByteStreams {

  private static final int BUFFER_SIZE = 8192;

  /** Creates a new byte array for buffering reads or writes. */
  static byte[] createBuffer() {
    return new byte[BUFFER_SIZE];
  }

  private static int saturatedCast(long value) {
    if (value > Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    if (value < Integer.MIN_VALUE) {
      return Integer.MIN_VALUE;
    }
    return (int) value;
  }

  private ByteStreams() {}

  /** Max array length on JVM. */
  private static final int MAX_ARRAY_LEN = Integer.MAX_VALUE - 8;

  /** Large enough to never need to expand, given the geometric progression of buffer sizes. */
  private static final int TO_BYTE_ARRAY_DEQUE_SIZE = 20;

  /**
   * Returns a byte array containing the bytes from the buffers already in {@code bufs} (which have
   * a total combined length of {@code totalLen} bytes) followed by all bytes remaining in the given
   * input stream.
   */
  private static byte[] toByteArrayInternal(InputStream in, Queue<byte[]> bufs, int totalLen)
      throws IOException {
    // Roughly size to match what has been read already. Some file systems, such as procfs, return 0
    // as their length. These files are very small, so it's wasteful to allocate an 8KB buffer.
    int initialBufferSize = min(BUFFER_SIZE, max(128, Integer.highestOneBit(totalLen) * 2));
    // Starting with an 8k buffer, double the size of each successive buffer. Smaller buffers
    // quadruple in size until they reach 8k, to minimize the number of small reads for longer
    // streams. Buffers are retained in a deque so that there's no copying between buffers while
    // reading and so all of the bytes in each new allocated buffer are available for reading from
    // the stream.
    for (int bufSize = initialBufferSize;
        totalLen < MAX_ARRAY_LEN;
        bufSize = saturatedCast((long) bufSize * (bufSize < 4096 ? 4 : 2))) {
      byte[] buf = new byte[min(bufSize, MAX_ARRAY_LEN - totalLen)];
      bufs.add(buf);
      int off = 0;
      while (off < buf.length) {
        // always OK to fill buf; its size plus the rest of bufs is never more than MAX_ARRAY_LEN
        int r = in.read(buf, off, buf.length - off);
        if (r == -1) {
          return combineBuffers(bufs, totalLen);
        }
        off += r;
        totalLen += r;
      }
    }

    // read MAX_ARRAY_LEN bytes without seeing end of stream
    if (in.read() == -1) {
      // oh, there's the end of the stream
      return combineBuffers(bufs, MAX_ARRAY_LEN);
    } else {
      throw new OutOfMemoryError("input is too large to fit in a byte array");
    }
  }

  private static byte[] combineBuffers(Queue<byte[]> bufs, int totalLen) {
    if (bufs.isEmpty()) {
      return new byte[0];
    }
    byte[] result = bufs.remove();
    if (result.length == totalLen) {
      return result;
    }
    int remaining = totalLen - result.length;
    result = Arrays.copyOf(result, totalLen);
    while (remaining > 0) {
      byte[] buf = bufs.remove();
      int bytesToCopy = min(remaining, buf.length);
      int resultOffset = totalLen - remaining;
      System.arraycopy(buf, 0, result, resultOffset, bytesToCopy);
      remaining -= bytesToCopy;
    }
    return result;
  }

  /**
   * Reads all bytes from an input stream into a byte array. Does not close the stream.
   *
   * @param in the input stream to read from
   * @return a byte array containing all the bytes from the stream
   * @throws IOException if an I/O error occurs
   */
  public static byte[] toByteArray(InputStream in) throws IOException {
    return toByteArrayInternal(in, new ArrayDeque<byte[]>(TO_BYTE_ARRAY_DEQUE_SIZE), 0);
  }

  public static InputStream limit(InputStream in, long limit) {
    return new LimitedInputStream(in, limit);
  }

  private static final class LimitedInputStream extends FilterInputStream {

    private long left;
    private long mark = -1;

    LimitedInputStream(InputStream in, long limit) {
      super(in);
      left = limit;
    }

    @Override
    public int available() throws IOException {
      return (int) Math.min(in.available(), left);
    }

    // it's okay to mark even if mark isn't supported, as reset won't work
    @Override
    public synchronized void mark(int readLimit) {
      in.mark(readLimit);
      mark = left;
    }

    @Override
    public int read() throws IOException {
      if (left == 0) {
        return -1;
      }

      int result = in.read();
      if (result != -1) {
        --left;
      }
      return result;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (left == 0) {
        return -1;
      }

      len = (int) Math.min(len, left);
      int result = in.read(b, off, len);
      if (result != -1) {
        left -= result;
      }
      return result;
    }

    @Override
    public synchronized void reset() throws IOException {
      if (!in.markSupported()) {
        throw new IOException("Mark not supported");
      }
      if (mark == -1) {
        throw new IOException("Mark not set");
      }

      in.reset();
      left = mark;
    }

    @Override
    public long skip(long n) throws IOException {
      n = Math.min(n, left);
      long skipped = in.skip(n);
      left -= skipped;
      return skipped;
    }
  }
}
