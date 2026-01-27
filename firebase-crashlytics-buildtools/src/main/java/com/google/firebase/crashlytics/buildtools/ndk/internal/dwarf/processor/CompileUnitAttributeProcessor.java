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

import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DWAttribute;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DWForm;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.processor.CompilationUnitContext.EntryData;

/**
 * Implementation of AttributeProcessor specific to processing Debugging Information Entries with
 * the tag DW_TAG_compile_unit.
 */
public class CompileUnitAttributeProcessor implements AttributeProcessor<EntryData> {

  private final ReferenceBytesConverter referenceBytesConverter;

  private long lowPc;
  private long stmtList;

  /**
   * Constructs an attributes processor with the given entry data builder for capturing
   * relevant values from the DW_TAG_compile_unit entry's attributes.
   * @param referenceBytesConverter converter for transforming raw reference bytes into long values
   */
  public CompileUnitAttributeProcessor(ReferenceBytesConverter referenceBytesConverter) {
    this.referenceBytesConverter = referenceBytesConverter;
  }

  @Override
  public void processAttribute(DWAttribute attribute, DWForm form, byte[] value) {
    switch (attribute) {
      case STMT_LIST: // DWARF 2/3
        stmtList = referenceBytesConverter.asLongValue(value);
        break;
      default:
        break;
    }
  }

  @Override
  public void processAttribute(DWAttribute attribute, long value) {
    switch (attribute) {
      case LOW_PC:
        lowPc = value;
        break;
      case STMT_LIST: // DWARF 4
        stmtList = value;
        break;
      default:
        break;
    }
  }

  @Override
  public void processAttribute(DWAttribute attribute, String value) {}

  @Override
  public EntryData finishProcessingAttributes() {
    return new EntryData(lowPc, stmtList);
  }
}
