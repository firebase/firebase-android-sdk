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
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.CompilationUnitContext.Header;
import com.google.firebase.crashlytics.buildtools.utils.io.ByteReader;
import java.io.IOException;
import java.util.List;

/**
 * Implementation of AttributesReader which reads a given set of attributes from a Debugging
 * Information Entry and returns data processed from the passed-in AttributeProcessor.
 */
public class DefaultAttributesReader<T> implements AttributesReader<T> {

  private final ByteReader reader;
  private final Header cuHeader;
  private final ReferenceBytesConverter referenceBytesConverter;
  private final AttributeProcessor<T> attributeProcessor;
  private final long debugStrOffset;

  /**
   * Constructs an attributes reader which will read attributes from the given ByteReader and send
   * them to the given attribute processor for further processing, returning the value returned
   * from the given attributeProcessor.
   * @param reader the source data stream for the DWARF data
   * @param cuHeader the header for the compilation unit which is the parent of the entry being read
   * @param referenceBytesConverter converter for transforming raw reference bytes into long values
   * @param attributeProcessor the data processor to which the read-in attribute data will be sent
   * @param debugStrOffset the offset of the .debug_str section in the file
   */
  public DefaultAttributesReader(
      ByteReader reader,
      Header cuHeader,
      ReferenceBytesConverter referenceBytesConverter,
      AttributeProcessor<T> attributeProcessor,
      long debugStrOffset) {
    this.reader = reader;
    this.cuHeader = cuHeader;
    this.referenceBytesConverter = referenceBytesConverter;
    this.attributeProcessor = attributeProcessor;
    this.debugStrOffset = debugStrOffset;
  }

  @Override
  public T readAttributes(List<DebugAbbrevEntry.Attribute> attributes) throws IOException {
    for (DebugAbbrevEntry.Attribute attribute : attributes) {
      processDebugInfoEntryAttribute(
          reader, cuHeader, referenceBytesConverter, attribute, attributeProcessor, debugStrOffset);
    }
    return attributeProcessor.finishProcessingAttributes();
  }

  private static void processDebugInfoEntryAttribute(
      ByteReader reader,
      Header cuHeader,
      ReferenceBytesConverter referenceBytesConverter,
      DebugAbbrevEntry.Attribute attribute,
      AttributeProcessor attributeProcessor,
      long debugStrOffset)
      throws IOException {
    switch (attribute.form) {
      case ADDR:
        attributeProcessor.processAttribute(attribute.name, reader.readLong(cuHeader.addressSize));
        break;
      case REF1:
        attributeProcessor.processAttribute(
            attribute.name,
            referenceBytesConverter.asLongValue(reader.readBytes(1)) + cuHeader.offset);
        break;
      case REF2:
        attributeProcessor.processAttribute(
            attribute.name,
            referenceBytesConverter.asLongValue(reader.readBytes(2)) + cuHeader.offset);
        break;
      case REF4:
        attributeProcessor.processAttribute(
            attribute.name,
            referenceBytesConverter.asLongValue(reader.readBytes(4)) + cuHeader.offset);
        break;
      case REF8:
        attributeProcessor.processAttribute(
            attribute.name,
            referenceBytesConverter.asLongValue(reader.readBytes(8)) + cuHeader.offset);
        break;
      case REF_UDATA:
        attributeProcessor.processAttribute(attribute.name, reader.readULEB128() + cuHeader.offset);
        break;
      case REF_ADDR:
        attributeProcessor.processAttribute(
            attribute.name,
            // DWARF 3+ changed this field from the size of an address on the target
            // system (addressSize) to either 4 or 8 bytes, depending on whether the 32 or
            // 64-bit DWARF format is in use (referenceSize).
            // See DWARF 4 spec section 1.5.
            reader.readLong(
                (cuHeader.version < 3) ? cuHeader.addressSize : cuHeader.referenceSize));
        break;
      case SEC_OFFSET:
        attributeProcessor.processAttribute(
            attribute.name, reader.readLong(cuHeader.referenceSize));
        break;
      case BLOCK1:
        attributeProcessor.processAttribute(
            attribute.name, attribute.form, readBytesWithBlockSize(reader, 1));
        break;
      case BLOCK2:
        attributeProcessor.processAttribute(
            attribute.name, attribute.form, readBytesWithBlockSize(reader, 2));
        break;
      case BLOCK4:
        attributeProcessor.processAttribute(
            attribute.name, attribute.form, readBytesWithBlockSize(reader, 4));
        break;
      case BLOCK:
      case EXPRLOC:
        attributeProcessor.processAttribute(
            attribute.name, attribute.form, readBytesWithBlockSize(reader));
        break;
      case DATA1:
        attributeProcessor.processAttribute(attribute.name, attribute.form, reader.readBytes(1));
        break;
      case DATA2:
        attributeProcessor.processAttribute(attribute.name, attribute.form, reader.readBytes(2));
        break;
      case DATA4:
        attributeProcessor.processAttribute(attribute.name, attribute.form, reader.readBytes(4));
        break;
      case DATA8:
      case REF_SIG8:
        attributeProcessor.processAttribute(attribute.name, attribute.form, reader.readBytes(8));
        break;
      case SDATA:
        attributeProcessor.processAttribute(attribute.name, reader.readSLEB128());
        break;
      case UDATA:
        attributeProcessor.processAttribute(attribute.name, reader.readULEB128());
        break;
      case FLAG:
        attributeProcessor.processAttribute(attribute.name, reader.readLong(1));
        break;
      case FLAG_PRESENT:
        attributeProcessor.processAttribute(attribute.name, 1L);
        break;
      case STRING:
        attributeProcessor.processAttribute(
            attribute.name, reader.readNullTerminatedString(Charsets.UTF_8));
        break;
      case STRP:
        attributeProcessor.processAttribute(
            attribute.name, readStringFromTable(reader, cuHeader.referenceSize, debugStrOffset));
        break;
      default:
        attributeProcessor.processAttribute(attribute.name, 0L);
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

  private static String readStringFromTable(
      ByteReader reader, int referenceSize, long debugStrOffset) throws IOException {
    long tableOffset = reader.readLong(referenceSize);
    long pos = reader.getCurrentOffset();

    reader.seek(debugStrOffset + tableOffset);
    String value = reader.readNullTerminatedString(Charsets.UTF_8);
    reader.seek(pos);

    return value;
  }
}
