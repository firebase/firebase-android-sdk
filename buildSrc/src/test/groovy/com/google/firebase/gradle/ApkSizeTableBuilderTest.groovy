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


package com.google.firebase.gradle

import static org.junit.Assert.assertNotNull

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4.class)
public class ApkSizeTableBuilderTest {

  @Test
  public void toTableString_throwsWhenZeroAdded() {
    def builder = new ApkSizeTableBuilder()

    try {
      builder.toTableString()
    } catch (IllegalStateException x) {
      // Thrown and caught; as expected.
      return
    }

    throw new AssertionError("IllegalStateException not thrown")
  }

  @Test
  public void toTableString_returnsNonNull() {
    def builder = new ApkSizeTableBuilder()
    builder.addApkSize("firebase foo", "debug", 255000)

    assertNotNull(builder.toTableString())
  }
}
