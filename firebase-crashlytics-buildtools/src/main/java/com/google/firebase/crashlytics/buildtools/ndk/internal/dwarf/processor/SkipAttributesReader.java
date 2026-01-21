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

package com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor;

import com.google.common.base.Charsets;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DebugAbbrevEntry;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of AttributesReader which skips over the appropriate bytes for the given set of
 * attributes, positioning the given reader's offset at the next byte after the attribute data, as
 * if it had been read and processed.
 */
public class SkipAttributesReader implements AttributesReader<Void> {

  private final ByteReader reader;
  private final CompilationUnitContext.Header cuHeader;

  /**
   * Constructs a new SkipAttributesReader which will read the attributes from the passed-in
   * ByteReader but skip processing them.
   * @param reader the source data stream for the DWARF data
   * @param cuHeader the header for the compilation unit which is the parent of the entry being read
   */
  public SkipAttributesReader(ByteReader reader, CompilationUnitContext.Header cuHeader) {
    this.reader = reader;
    this.cuHeader = cuHeader;
  }

  @Override
  public Void readAttributes(List<DebugAbbrevEntry.Attribute> attributes) throws IOException {
    for (DebugAbbrevEntry.Attribute attribute : attributes) {
      skipDebugInfoEntryAttribute(reader, attribute, cuHeader);
    }
    return null;
  }

  private static void skipDebugInfoEntryAttribute(
      ByteReader reader,
      DebugAbbrevEntry.Attribute attribute,
      CompilationUnitContext.Header cuHeader)
      throws IOException {
    switch (attribute.form) {
      case ADDR:
        reader.readLong(cuHeader.addressSize);
        break;
      case FLAG:
      case DATA1:
      case REF1:
        reader.readBytes(1);
        break;
      case REF2:
      case DATA2:
        reader.readBytes(2);
        break;
      case REF4:
      case DATA4:
        reader.readBytes(4);
        break;
      case REF8:
      case DATA8:
      case REF_SIG8:
        reader.readBytes(8);
        break;
      case UDATA:
      case REF_UDATA:
        reader.readULEB128();
        break;
      case REF_ADDR:
        // DWARF 3+ changed this field from the size of an address on the target
        // system (addressSize) to either 4 or 8 bytes, depending on whether the 32 or
        // 64-bit DWARF format is in use (referenceSize).
        // See DWARF 4 spec section 1.5.
        reader.readBytes((cuHeader.version < 3) ? cuHeader.addressSize : cuHeader.referenceSize);
        break;
      case SEC_OFFSET:
      case STRP:
        reader.readBytes(cuHeader.referenceSize);
        break;
      case BLOCK1:
        readBytesWithBlockSize(reader, 1);
        break;
      case BLOCK2:
        readBytesWithBlockSize(reader, 2);
        break;
      case BLOCK4:
        readBytesWithBlockSize(reader, 4);
        break;
      case BLOCK:
      case EXPRLOC:
        readBytesWithBlockSize(reader);
        break;
      case SDATA:
        reader.readSLEB128();
        break;
      case STRING:
        reader.readNullTerminatedString(Charsets.UTF_8);
        break;
      case FLAG_PRESENT:
      default:
        break;
    }
  }

  private static byte[] readBytesWithBlockSize(ByteReader reader, int numBytes) throws IOException {
    int blockSize = reader.readInt(numBytes);
    return reader.readBytes(blockSize);
  }

  private static byte[] readBytesWithBlockSize(ByteReader reader) throws IOException {
    int blockSize = reader.readULEB128();
    return reader.readBytes(blockSize);
  }
}
