// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.bundle;

import androidx.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import org.json.JSONException;

/**
 * Reads the length-prefixed JSON stream for Bundles.
 *
 * <p>The class takes a bundle stream and presents abstractions to read bundled elements out of the
 * underlying content.
 */
public class BundleReader extends BundleElement {
  /** The capacity for the internal char buffer. */
  protected static final int BUFFER_CAPACITY = 1024;

  private final BundleSerializer serializer;
  private final InputStreamReader dataReader;

  @Nullable BundleMetadata metadata;
  private CharBuffer buffer;
  long bytesRead;

  public BundleReader(BundleSerializer serializer, InputStream data) {
    this.serializer = serializer;
    dataReader = new InputStreamReader(data);
    buffer = CharBuffer.allocate(BUFFER_CAPACITY);
  }

  /** Returns the metadata element from the bundle. */
  public BundleMetadata getBundleMetadata() throws IOException, JSONException {
    if (metadata != null) {
      return metadata;
    }
    BundleElement element = readNextElement();
    if (!(element instanceof BundleMetadata)) {
      throw new IllegalArgumentException(
          "Expected first element in bundle to be a metadata object");
    }
    metadata = (BundleMetadata) element;
    // We don't consider the metadata as part ot the bundle size, as it used to encode the size of
    // all remaining elements.
    bytesRead = 0;
    return metadata;
  }

  /**
   * Returns the next element from the bundle. Metadata elements can be accessed by invoking {@link
   * #getBundleMetadata} are not returned from this method.
   */
  public BundleElement getNextElement() throws IOException, JSONException {
    // Makes sure metadata is read before proceeding. The metadata element is the first element
    // in the bundle stream.
    getBundleMetadata();
    return readNextElement();
  }

  /** Returns the number of bytes processed so far. */
  public long getBytesRead() {
    return bytesRead;
  }

  public void close() throws IOException {
    dataReader.close();
  }

  /**
   * Reads from the head of internal buffer, Pulls more data from underlying stream until a complete
   * element is found (including the prefixed length and the JSON string).
   *
   * <p>Once a complete element is read, it is dropped from internal buffer.
   *
   * <p>Returns either the bundled element, or null if we have reached the end of the stream.
   */
  @Nullable
  private BundleElement readNextElement() throws IOException, JSONException {
    int length = readLength();
    if (length == -1) {
      return null;
    }

    String json = readJsonString(length);
    bytesRead += (int) (Math.log10(length) + 1) + length;
    return BundleElement.fromJson(serializer, json);
  }

  /**
   * Reads the length prefix from the beginning of the internal buffer until the first '{'. Returns
   * the integer-decoded length.
   *
   * <p>If it reached the end of the stream, returns -1.
   */
  private int readLength() throws IOException {
    int nextOpenBracket;

    while ((nextOpenBracket = indexOfOpenBracket()) == -1) {
      if (!pullMoreData()) {
        break;
      }
    }

    // We broke out of the loop because underlying stream is closed, and there happens to be no
    // more data to process.
    if (buffer.remaining() == 0) {
      return -1;
    }

    // We broke out of the loop because underlying stream is closed, but still cannot find an
    // open bracket.
    if (nextOpenBracket == -1) {
      raiseError("Reached the end of bundle when a length string is expected.");
    }

    char[] c = new char[nextOpenBracket];
    buffer.get(c);
    return Integer.parseInt(new String(c));
  }

  /** Returns the index of the first open bracket, or -1 if none is found. */
  private int indexOfOpenBracket() {
    buffer.mark();
    try {
      for (int i = 0; i < buffer.limit(); ++i) {
        if (buffer.get() == '{') {
          return i;
        }
      }
      return -1;
    } finally {
      buffer.reset();
    }
  }

  /**
   * Reads from a specified position from the internal buffer, for a specified number of bytes,
   * pulling more data from the underlying stream if needed.
   *
   * <p>Returns a string decoded from the read bytes.
   */
  private String readJsonString(int length) throws IOException {
    char[] c = new char[length];

    int read = Math.min(length, buffer.remaining());
    buffer.get(c, 0, read);

    while (read < length) {
      if (!pullMoreData()) {
        raiseError("Reached the end of bundle when more data was expected.");
      }
      int toRead = Math.min(length, buffer.remaining());
      buffer.get(c, read, toRead);
      read += toRead;
    }
    return new String(c);
  }

  /**
   * Pulls more data from underlying stream into the internal buffer.
   *
   * @return whether more data is available
   */
  private boolean pullMoreData() throws IOException {
    if (buffer.remaining() == 0) {
      buffer.compact();
    }

    int read;
    do {
      read = dataReader.read(buffer);
    } while (read > 0);
    buffer.flip();

    return buffer.remaining() > 0;
  }

  /** Closes the underlying stream and raises an IllegalArgumentException. */
  private void raiseError(String message) throws IOException {
    close();
    throw new IllegalArgumentException("Invalid bundle format: " + message);
  }
}
