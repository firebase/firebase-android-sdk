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

package com.google.firebase.firestore.index;

import com.google.firebase.firestore.model.FieldIndex;
import com.google.protobuf.ByteString;

/**
 * Implements {@link DirectionalIndexByteEncoder} using {@link OrderedCodeWriter} for the actual
 * encoding.
 */
public class IndexByteEncoder {

  class AscendingIndexByteEncoder extends DirectionalIndexByteEncoder {

    @Override
    public void writeBytes(ByteString val) {
      orderedCode.writeBytesAscending(val);
    }

    @Override
    public void writeString(String val) {
      orderedCode.writeUtf8Ascending(val);
    }

    @Override
    public void writeLong(long val) {
      orderedCode.writeSignedLongAscending(val);
    }

    @Override
    public void writeDouble(double val) {
      orderedCode.writeDoubleAscending(val);
    }

    @Override
    public void writeInfinity() {
      orderedCode.writeInfinityAscending();
    }
  }

  class DescendingIndexByteEncoder extends DirectionalIndexByteEncoder {

    @Override
    public void writeBytes(ByteString val) {
      orderedCode.writeBytesDescending(val);
    }

    @Override
    public void writeString(String val) {
      orderedCode.writeUtf8Descending(val);
    }

    @Override
    public void writeLong(long val) {
      orderedCode.writeSignedLongDescending(val);
    }

    @Override
    public void writeDouble(double val) {
      orderedCode.writeDoubleDescending(val);
    }

    @Override
    public void writeInfinity() {
      orderedCode.writeInfinityDescending();
    }
  }

  private final OrderedCodeWriter orderedCode;
  private final AscendingIndexByteEncoder ascending;
  private final DescendingIndexByteEncoder descending;

  public IndexByteEncoder() {
    this.orderedCode = new OrderedCodeWriter();
    this.ascending = new AscendingIndexByteEncoder();
    this.descending = new DescendingIndexByteEncoder();
  }

  public void seed(byte[] encodedBytes) {
    orderedCode.seed(encodedBytes);
  }

  public DirectionalIndexByteEncoder forKind(FieldIndex.Segment.Kind kind) {
    if (kind.equals(FieldIndex.Segment.Kind.DESCENDING)) {
      return descending;
    } else {
      return ascending;
    }
  }

  public byte[] getEncodedBytes() {
    return orderedCode.encodedBytes();
  }

  public void reset() {
    orderedCode.reset();
  }
}
