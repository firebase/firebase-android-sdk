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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test cases for the {@link StorageUnit} class.
 *
 * <p>Reference: /javatests/com/google/android/libraries/stitch/util/StorageUnitTest.java
 */
@RunWith(JUnit4.class)
public class StorageUnitTest {

  @Test
  public void testToBytes() {
    assertEquals(1L, StorageUnit.BYTES.toBytes(1L));
    assertEquals(55L, StorageUnit.BYTES.toBytes(55L));
    assertEquals(1024L, StorageUnit.BYTES.toBytes(1024L));

    assertEquals(1024L, StorageUnit.KILOBYTES.toBytes(1L));

    assertEquals(16L * 1024L * 1024L, StorageUnit.MEGABYTES.toBytes(16L));

    assertEquals(305L * 1024L * 1024L * 1024L, StorageUnit.GIGABYTES.toBytes(305L));

    assertEquals(17L * 1024L * 1024L * 1024L * 1024L, StorageUnit.TERABYTES.toBytes(17));
  }

  @Test
  public void testConvert() {
    assertEquals(500L, StorageUnit.GIGABYTES.convert(500L, StorageUnit.GIGABYTES));

    assertEquals(107L * 1024L, StorageUnit.MEGABYTES.convert(107L, StorageUnit.GIGABYTES));

    assertEquals(17L * 1024L * 1024L, StorageUnit.KILOBYTES.convert(17L, StorageUnit.GIGABYTES));

    assertEquals(17000L * 1024L, StorageUnit.GIGABYTES.convert(17000L, StorageUnit.TERABYTES));

    assertEquals(2L, StorageUnit.TERABYTES.convert(2L * 1024L, StorageUnit.GIGABYTES));

    assertEquals(34L * 1024L * 1024L, StorageUnit.BYTES.convert(34L, StorageUnit.MEGABYTES));
  }
}
