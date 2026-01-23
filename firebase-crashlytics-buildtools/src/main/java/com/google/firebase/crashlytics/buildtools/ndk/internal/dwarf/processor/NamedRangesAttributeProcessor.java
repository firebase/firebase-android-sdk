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

import com.google.common.base.Optional;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DWAttribute;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.DWForm;
import com.google.firebase.crashlytics.buildtools.ndk.internal.dwarf.NamedRange;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of AttributeProcessor specific to processing Debugging Information Entries with
 * the tag DW_TAG_subprogram or DW_TAG_inlined_subroutine.
 *
 * Processes the data in these entries in order to create a list of Named Ranges: memory ranges
 * with their function names appropriately resolved.
 */
public class NamedRangesAttributeProcessor implements AttributeProcessor<List<NamedRange>> {

  private final long offset;
  private final CompilationUnitContext cuContext;
  private final NamedRangesResolver namedRangesResolver;

  // State for the current entry
  private String name;
  private String linkageName;
  private boolean isDeclaration;
  private boolean isInline;
  private long specification = -1;
  private long abstractOrigin = -1;
  private long highPc = -1;
  private boolean isHighPcAddress;
  private long lowPc = -1;
  private long rangesOffset = -1;

  /**
   * Constructs a new NamedRangesAttributeProcessor which will build up state through calls to
   * its processAttribute methods, then return a list of named ranges based on that state when
   * its finishProcessingAttributes method is called.
   * @param offset
   * @param cuContext
   * @param namedRangesResolver
   */
  public NamedRangesAttributeProcessor(
      long offset, CompilationUnitContext cuContext, NamedRangesResolver namedRangesResolver) {
    this.offset = offset;
    this.cuContext = cuContext;
    this.namedRangesResolver = namedRangesResolver;
  }

  @Override
  public void processAttribute(DWAttribute attribute, DWForm form, byte[] value) {
    switch (attribute) {
      case HIGH_PC:
        highPc = cuContext.fileContext.referenceBytesConverter.asLongValue(value);
        isHighPcAddress = false;
        break;
      case INLINE:
        isInline = true;
        break;
      default:
        break;
    }
  }

  @Override
  public void processAttribute(DWAttribute attribute, long value) {
    switch (attribute) {
      case DECLARATION:
        isDeclaration = (value == 1);
        break;
      case SPECIFICATION:
        specification = value;
        break;
      case ABSTRACT_ORIGIN:
        abstractOrigin = value;
        break;
      case LOW_PC:
        lowPc = value;
        break;
      case HIGH_PC:
        highPc = value;
        isHighPcAddress = true;
        break;
      case RANGES:
        rangesOffset = value;
        break;
      default:
        break;
    }
  }

  @Override
  public void processAttribute(DWAttribute attribute, String value) {
    switch (attribute) {
      case NAME:
        name = value;
        break;
      case LINKAGE_NAME:
        linkageName = value;
        break;
      default:
        break;
    }
  }

  @Override
  public List<NamedRange> finishProcessingAttributes() {
    String resolvedName =
        Optional.fromNullable(linkageName).or(Optional.fromNullable(name)).orNull();

    if (resolvedName == null) {
      resolvedName =
          (specification >= 0)
              ? cuContext.specificationMap.get(specification)
              : (abstractOrigin >= 0) ? cuContext.abstractOriginMap.get(abstractOrigin) : null;
    }

    if (resolvedName != null) {
      if (isDeclaration) {
        cuContext.specificationMap.put(offset, resolvedName);
      }
      if (isInline) {
        cuContext.abstractOriginMap.put(offset, resolvedName);
      }
    } else {
      // In this case, the name entry has most likely not been read yet.
      // For now, add a placeholder.
      resolvedName = "<unknown>";
    }

    if (lowPc >= 0 && highPc >= 0) {
      // DWARF 4 spec section 2.17.2:
      // "If the value of the DW_AT_high_pc is of class address, it is the relocated address of the
      // first location past the last instruction associated with the entity; if it is of class
      // constant, the value is an unsigned integer offset which when added to the low PC gives
      // the address of the first location past the last instruction associated with the entity."
      if (!isHighPcAddress) {
        highPc += lowPc;
      }
      return Collections.singletonList(new NamedRange(resolvedName, lowPc, highPc));
    } else if (rangesOffset >= 0) {
      return namedRangesResolver.resolveNamedRanges(
          rangesOffset, resolvedName, cuContext.getLowPc());
    }

    return Collections.emptyList();
  }
}
