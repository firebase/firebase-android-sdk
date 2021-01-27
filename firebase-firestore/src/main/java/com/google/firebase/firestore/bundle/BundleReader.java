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
import com.google.firebase.firestore.util.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads the length-prefixed JSON stream for Bundles.
 *
 * <p>The class takes a bundle stream and presents abstractions to read bundled elements out of the
 * underlying content.
 */
public class BundleReader {
  /** The capacity for the internal char buffer. */
  protected static final int BUFFER_CAPACITY = 1024;

  private final BundleSerializer serializer;
  private final InputStreamReader dataReader;
  private final Charset charset = Charset.forName("UTF-8");

  @Nullable BundleMetadata metadata;
  private CharBuffer buffer;
  long bytesRead;

  public BundleReader(BundleSerializer serializer, InputStream data) {
    this.serializer = serializer;
    dataReader = new InputStreamReader(data, charset);
    buffer = CharBuffer.allocate(BUFFER_CAPACITY);

    buffer.flip(); // Start the buffer in "reading mode"
  }

  /** Returns the metadata element from the bundle. */
  public BundleMetadata getBundleMetadata() throws IOException, JSONException {
    if (metadata != null) {
      return metadata;
    }
    BundleElement element = readNextElement();
    if (!(element instanceof BundleMetadata)) {
      throw abort("Expected first element in bundle to be a metadata object");
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
    String lengthPrefix = readLengthPrefix();
    if (lengthPrefix == null) {
      return null;
    }

    String json = readJsonString(Integer.parseInt(lengthPrefix));
    bytesRead += lengthPrefix.length() + json.getBytes(charset).length;
    return decodeBundleElement(json);
  }

  /**
   * Reads the length prefix from the beginning of the internal buffer until the first '{'. Returns
   * the integer-decoded length.
   *
   * <p>If it reached the end of the stream, returns null.
   */
  private @Nullable String readLengthPrefix() throws IOException {
    int nextOpenBracket;

    while ((nextOpenBracket = indexOfOpenBracket()) == -1) {
      if (!pullMoreData()) {
        break;
      }
    }

    // We broke out of the loop because underlying stream is closed, and there happens to be no
    // more data to process.
    if (buffer.remaining() == 0) {
      return null;
    }

    // We broke out of the loop because underlying stream is closed, but still cannot find an
    // open bracket.
    if (nextOpenBracket == -1) {
      throw abort("Reached the end of bundle when a length string is expected.");
    }

    char[] c = new char[nextOpenBracket];
    buffer.get(c);
    return new String(c);
  }

  /** Returns the index of the first open bracket, or -1 if none is found. */
  private int indexOfOpenBracket() {
    buffer.mark();
    try {
      for (int i = 0; i < buffer.remaining(); ++i) {
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
    StringBuilder json = new StringBuilder(length);

    int remaining = length;
    while (remaining > 0) {
      if (buffer.remaining() == 0 && !pullMoreData()) {
        throw abort("Reached the end of bundle when more data was expected.");
      }

      int read = Math.min(remaining, buffer.remaining());
      json.append(buffer, 0, read);
      buffer.position(buffer.position() + read);

      remaining -= read;
    }

    return json.toString();
  }

  /**
   * Pulls more data from underlying stream into the internal buffer.
   *
   * @return whether more data was read.
   */
  private boolean pullMoreData() throws IOException {
    buffer.compact();
    int read = dataReader.read(buffer);
    buffer.flip();
    return read > 0;
  }

  /** Converts a JSON-encoded bundle element into its model class. */
  private BundleElement decodeBundleElement(String json) throws JSONException, IOException {
    JSONObject object = new JSONObject(json);

    if (object.has("metadata")) {
      BundleMetadata metadata = serializer.decodeBundleMetadata(object.getJSONObject("metadata"));
      Logger.debug("BundleElement", "BundleMetadata element loaded");
      return metadata;
    } else if (object.has("namedQuery")) {
      NamedQuery namedQuery = serializer.decodeNamedQuery(object.getJSONObject("namedQuery"));
      Logger.debug("BundleElement", "Query loaded: " + namedQuery.getName());
      return namedQuery;
    } else if (object.has("documentMetadata")) {
      BundledDocumentMetadata documentMetadata =
          serializer.decodeBundledDocumentMetadata(object.getJSONObject("documentMetadata"));
      Logger.debug("BundleElement", "Document metadata loaded: " + documentMetadata.getKey());
      return documentMetadata;
    } else if (object.has("document")) {
      BundleDocument document = serializer.decodeDocument(object.getJSONObject("document"));
      Logger.debug("BundleElement", "Document loaded: " + document.getKey());
      return document;
    } else {
      throw abort("Cannot decode unknown Bundle element: " + json);
    }
  }

  /** Closes the underlying stream and raises an IllegalArgumentException. */
  private IllegalArgumentException abort(String message) throws IOException {
    close();
    throw new IllegalArgumentException("Invalid bundle: " + message);
  }
}
