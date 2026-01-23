/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.buildtools.utils.io;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * SeekableInputStream whose underlying source is a RandomAccessFile.
 *
 * This stream is buffered using an 8KB buffer to improve upon the limitations of the RandomAccessFileInputStream
 * implementation.
 */
public class RandomAccessFileInputStream extends SeekableInputStream {

  private static final int BUFFER_SIZE = 8192;

  private RandomAccessFile _file;
  private byte[] _buffer;

  private long _filePointer; // Actual position of the file pointer.

  private int _bufferPos = 0;
  private int _bufferCount = 0;

  public RandomAccessFileInputStream(File file) throws IOException {
    _file = new RandomAccessFile(file, "r");
    _buffer = new byte[BUFFER_SIZE];
    _filePointer = _file.getFilePointer();
  }

  @Override
  public void seek(long offset) throws IOException {
    validateOpen();
    if (offset < 0) {
      throw new IOException("Seek offset must be greater than 0");
    }

    long beginOffset = _filePointer - _bufferCount;

    if (offset >= beginOffset && offset < _filePointer) {
      // Seek within the buffer.
      // Cast is safe here because offset - beginOffset will always be < BUFFER_SIZE.
      _bufferPos = (int) (offset - beginOffset);
      return;
    }

    _file.seek(offset);
    _bufferCount = 0; // Invalidate the buffer
    _filePointer = _file.getFilePointer();
  }

  @Override
  public long getCurrentOffset() throws IOException {
    validateOpen();
    int remaining = _bufferCount - _bufferPos;
    return _filePointer - Math.max(remaining, 0);
  }

  @Override
  public void readFully(byte[] buffer, int offset, int length) throws IOException {
    validateOpen();
    int totalBytes = 0;
    do {
      int bytesRead = this.read(buffer, offset + totalBytes, length - totalBytes);
      if (bytesRead < 0) {
        throw new EOFException();
      }
      totalBytes += bytesRead;
    } while (totalBytes < length);
  }

  @Override
  public int read() throws IOException {
    validateOpen();
    if (_bufferPos >= _bufferCount) {
      fillBuffer();
      if (_bufferPos >= _bufferCount) {
        return -1;
      }
    }
    return _buffer[_bufferPos++] & 0xff;
  }

  @Override
  public int read(byte[] bytes) throws IOException {
    validateOpen();
    return read(bytes, 0, bytes.length);
  }

  @Override
  public int read(byte[] bytes, int off, int len) throws IOException {
    if ((off < 0)
        || (off > bytes.length)
        || (len < 0)
        || ((off + len) > bytes.length)
        || ((off + len) < 0)) {
      throw new IndexOutOfBoundsException();
    } else if (len == 0) {
      return len;
    }

    validateOpen();

    int totalBytes = 0;
    while (true) {
      int bytesRead = readOnce(bytes, off + totalBytes, len - totalBytes);
      if (bytesRead <= 0) {
        // EOF
        return (totalBytes == 0) ? bytesRead : totalBytes;
      }
      totalBytes += bytesRead;
      if (totalBytes >= len) {
        // Done!
        return totalBytes;
      }
    }
  }

  @Override
  public long skip(long n) throws IOException {
    if (n <= 0) {
      return 0;
    }

    validateOpen();

    int remaining = _bufferCount - _bufferPos;

    if (n <= remaining) {
      // Skip within the buffer.
      _bufferPos += n;
      return n;
    }

    long pos = getCurrentOffset();
    long fileLen = _file.length();
    long newPos = pos + n;

    if (newPos > fileLen) {
      newPos = fileLen;
    }

    seek(newPos);
    return newPos - pos;
  }

  @Override
  public void close() throws IOException {
    _file.close();
    _file = null;
    _buffer = null;
  }

  private int readOnce(byte[] bytes, int offset, int len) throws IOException {
    int remaining = _bufferCount - _bufferPos;
    if (remaining <= 0) {
      if (len >= _buffer.length) {
        // Just read straight from the file. No use in copying to the buffer.
        return readFromFile(bytes, offset, len);
      }
      fillBuffer();
      remaining = _bufferCount - _bufferPos;
      if (remaining <= 0) {
        return -1;
      }
    }
    // Read from the buffer - might not read all requested.
    int numBytesRead = (remaining < len) ? remaining : len;
    System.arraycopy(_buffer, _bufferPos, bytes, offset, numBytesRead);
    _bufferPos += numBytesRead;
    return numBytesRead;
  }

  private void fillBuffer() throws IOException {
    // Throw away the buffer
    _bufferPos = 0;
    _bufferCount = 0;
    int numRead = readFromFile(_buffer, 0, _buffer.length);
    if (numRead > 0) {
      _bufferCount = numRead;
    }
  }

  private int readFromFile(byte[] bytes, int offset, int len) throws IOException {
    int bytesRead = _file.read(bytes, offset, len);
    _filePointer = _file.getFilePointer();
    return bytesRead;
  }

  private void validateOpen() throws IOException {
    if (_file == null || _buffer == null) {
      throw new IOException("Stream closed.");
    }
  }
}
