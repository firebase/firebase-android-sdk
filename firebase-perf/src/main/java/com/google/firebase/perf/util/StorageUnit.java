// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.util;

/**
 * A StorageUnit represents storage at a given unit and provides utilities methods for converting
 * from one StorageUnit to another. Useful when you want storage constant values.
 *
 * <p>Similar to {@link java.util.concurrent.TimeUnit}, but for storage quantities.
 *
 * <p>Reference: {@link com.google.android.libraries.stitch.util.StorageUnit}
 */
public enum StorageUnit {
  TERABYTES(1024L * 1024L * 1024L * 1024L) {
    @Override
    public long convert(long quantity, StorageUnit sourceUnit) {
      return sourceUnit.toTerabytes(quantity);
    }
  },
  GIGABYTES(1024L * 1024L * 1024L) {
    @Override
    public long convert(long quantity, StorageUnit sourceUnit) {
      return sourceUnit.toGigabytes(quantity);
    }
  },
  MEGABYTES(1024L * 1024L) {
    @Override
    public long convert(long quantity, StorageUnit sourceUnit) {
      return sourceUnit.toMegabytes(quantity);
    }
  },
  KILOBYTES(1024L) {
    @Override
    public long convert(long quantity, StorageUnit sourceUnit) {
      return sourceUnit.toKilobytes(quantity);
    }
  },
  BYTES(1L) {
    @Override
    public long convert(long quantity, StorageUnit sourceUnit) {
      return sourceUnit.toBytes(quantity);
    }
  };

  long numBytes;

  StorageUnit(long numBytes) {
    this.numBytes = numBytes;
  }

  /**
   * Convert a quantity in this unit to Bytes
   *
   * @param quantity the quantity of storage
   * @return quantity measured in Bytes
   */
  public long toBytes(long quantity) {
    return quantity * numBytes;
  }

  /**
   * Convert a quantity in this unit to Kilobytes
   *
   * @param quantity the quantity of storage
   * @return quantity measured in Kilobytes
   */
  public long toKilobytes(long quantity) {
    return quantity * numBytes / KILOBYTES.numBytes;
  }

  /**
   * Convert a quantity in this unit to Megabytes
   *
   * @param quantity the quantity of storage
   * @return quantity measured in Megabytes
   */
  public long toMegabytes(long quantity) {
    return quantity * numBytes / MEGABYTES.numBytes;
  }

  /**
   * Convert a quantity in this unit to Gigabytes
   *
   * @param quantity the quantity of storage
   * @return quantity measured in Gigabytes
   */
  public long toGigabytes(long quantity) {
    return quantity * numBytes / GIGABYTES.numBytes;
  }

  /**
   * Convert a quantity in this unit to Terabytes
   *
   * @param quantity the quantity of storage
   * @return quantity measured in Terabytes
   */
  public long toTerabytes(long quantity) {
    return quantity * numBytes / TERABYTES.numBytes;
  }

  /**
   * Convert a given storage amount in the given unit to this unit.
   *
   * <p>For example, to convert 5 gigabytes to kilobytes, use: {@code
   * StorageUnit.KILOBYTES.convert(5L, StorageUnit.GIGABYTES)}
   *
   * @param quantity the storage quantity in the given sourceUnit
   * @param sourceUnit the unit of the quantity argument
   * @return the converted storage quantity in this unit
   */
  public abstract long convert(long quantity, StorageUnit sourceUnit);
}
