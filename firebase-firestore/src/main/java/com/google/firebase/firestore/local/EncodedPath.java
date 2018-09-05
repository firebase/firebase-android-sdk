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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.model.BasePath;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helpers for dealing with paths stored in SQLite.
 *
 * <p>Paths in their canonical string form do not sort as the server sorts them. Specifically the
 * server splits paths into segments first and then sorts, putting end-of-segment before any
 * character. In a UTF-8 string encoding the slash ('/') or dot ('.') that denotes the
 * end-of-segment naturally comes after other characters so the intent here is to encode the path
 * delimiters in such a way that the resulting strings sort naturally.
 *
 * <p>Paths are also used for prefix scans so it's important to distinguish whole segments from any
 * longer segments of which they might be a prefix. For example, it's important to make it possible
 * to scan documents in a collection "foo" without encountering documents in a collection "foobar".
 *
 * <p>Separate from the concerns about path ordering and separation, SQLite imposes additional
 * restrictions since it does not handle TEXT fields with embedded NUL bytes particularly well.
 * Rather than deal with these limitations, this implementation sidesteps the issue entirely by
 * avoiding NUL bytes in the output altogether.
 *
 * <p>Taken together this means paths when encoded for storage in SQLite have the following
 * characteristics:
 *
 * <ul>
 *   <li>Segment separators ("/" or ".") sort before everything else.
 *   <li>All paths have a trailing separator.
 *   <li>NUL bytes do not exist in the output, since SQLite doesn't treat them well.
 * </ul>
 *
 * <p>Therefore paths are encoded into string form using the following rules:
 *
 * <ul>
 *   <li>'\x01' is used as an escape character.
 *   <li>Path separators are encoded as "\x01\x01"
 *   <li>NUL bytes are encoded as "\x01\x10"
 *   <li>'\x01' is encoded as "\x01\x11"
 * </ul>
 *
 * <p>This encoding leaves some room between path separators and the NUL byte just in case we decide
 * to support integer document ids after all.
 *
 * <p>Note that characters treated specially by the backend (e.g. '.', '/', and '~') are not treated
 * specially here. This class assumes that any unescaping of path strings into actual Path objects
 * will handle these characters there.
 */
final class EncodedPath {

  private static final char ESCAPE = '\u0001';

  private static final char ENCODED_SEPARATOR = '\u0001';
  private static final char ENCODED_NUL = '\u0010';
  private static final char ENCODED_ESCAPE = '\u0011';

  /** Encodes a path into a SQLite-compatible string form. */
  static <B extends BasePath<B>> String encode(B path) {
    StringBuilder result = new StringBuilder();
    for (int i = 0, length = path.length(); i < length; i++) {
      if (result.length() > 0) {
        encodeSeparator(result);
      }
      encodeSegment(path.getSegment(i), result);
    }
    encodeSeparator(result);
    return result.toString();
  }

  /** Encodes a single segment of a path into the given StringBuilder. */
  private static void encodeSegment(String segment, StringBuilder result) {
    for (int i = 0, length = segment.length(); i < length; i++) {
      char c = segment.charAt(i);
      switch (c) {
        case '\0':
          result.append(ESCAPE).append(ENCODED_NUL);
          break;
        case ESCAPE:
          result.append(ESCAPE).append(ENCODED_ESCAPE);
          break;
        default:
          result.append(c);
      }
    }
  }

  /** Encodes a path separator into the given StringBuilder. */
  private static void encodeSeparator(StringBuilder result) {
    result.append(ESCAPE).append(ENCODED_SEPARATOR);
  }

  /**
   * Decodes the given SQLite-compatible string form of a resource path into a ResourcePath
   * instance. Note that this method is not suitable for use with decoding resource names from the
   * server; those are One Platform format strings.
   */
  static ResourcePath decodeResourcePath(String path) {
    return ResourcePath.fromSegments(decode(path));
  }

  static FieldPath decodeFieldPath(String path) {
    return FieldPath.fromSegments(decode(path));
  }

  private static List<String> decode(String path) {
    // Even the empty path must encode as a path of at least length 2. A path with length of exactly
    // 2 must be the empty path.
    int length = path.length();
    hardAssert(length >= 2, "Invalid path \"%s\"", path);
    if (length == 2) {
      hardAssert(
          path.charAt(0) == ESCAPE && path.charAt(1) == ENCODED_SEPARATOR,
          "Non-empty path \"%s\" had length 2",
          path);
      return Collections.emptyList();
    }

    // Escape characters cannot exist past the second-to-last position in the source value.
    int lastReasonableEscapeIndex = path.length() - 2;

    List<String> segments = new ArrayList<>();
    StringBuilder segmentBuilder = new StringBuilder();

    for (int start = 0; start < length; ) {
      // The last two characters of a valid encoded path must be a separator, so there must be an
      // end to this segment.
      int end = path.indexOf(ESCAPE, start);
      if (end < 0 || end > lastReasonableEscapeIndex) {
        throw new IllegalArgumentException("Invalid encoded resource path: \"" + path + "\"");
      }

      char next = path.charAt(end + 1);
      switch (next) {
        case ENCODED_SEPARATOR:
          String currentPiece = path.substring(start, end);
          String segment;
          if (segmentBuilder.length() == 0) {
            // Avoid copying for the common case of a segment that excludes \0 and \001.
            segment = currentPiece;
          } else {
            segmentBuilder.append(currentPiece);
            segment = segmentBuilder.toString();
            segmentBuilder.setLength(0);
          }

          segments.add(segment);
          break;

        case ENCODED_NUL:
          segmentBuilder.append(path.substring(start, end));
          segmentBuilder.append('\0');
          break;

        case ENCODED_ESCAPE:
          // The escape character can be use used in the output to encode itself.
          segmentBuilder.append(path.substring(start, end + 1));
          break;

        default:
          throw new IllegalArgumentException("Invalid encoded resource path: \"" + path + "\"");
      }

      start = end + 2;
    }

    return segments;
  }

  /**
   * Computes the prefix successor of the given path, computed by encode above. A prefix successor
   * is the first key that cannot be prefixed by the given path. It's useful for defining the end of
   * a prefix scan such that all keys in the scan have the same prefix.
   *
   * <p>Note that this is not a general prefix successor implementation, which is tricky to get
   * right with Strings, given that they encode down to UTF-8. Instead this relies on the fact that
   * all paths encoded by this class are always terminated with a separator, and so a successor can
   * always be cheaply computed by incrementing the last character of the path.
   */
  static String prefixSuccessor(String path) {
    StringBuilder result = new StringBuilder(path);
    int pos = result.length() - 1;
    char c = result.charAt(pos);

    // TODO: this really should be a general thing, but not worth it right now
    hardAssert(c == ENCODED_SEPARATOR, "successor may only operate on paths generated by encode");
    result.setCharAt(pos, (char) ((int) c + 1));
    return result.toString();
  }
}
