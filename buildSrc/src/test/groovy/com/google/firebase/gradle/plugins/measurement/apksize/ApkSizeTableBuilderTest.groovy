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


package com.google.firebase.gradle.plugins.measurement.apksize

import static org.junit.Assert.assertEquals

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4.class)
public class ApkSizeTableBuilderTest {

  private static final String HEADER =
      "|------------------        APK Sizes        ------------------|\n" +
      "|--    project    --|--  build type   --|--  size in bytes  --|\n"

  @Test(expected = IllegalStateException.class)
  public void toTableString_throwsWhenZeroAdded() {
    def builder = new ApkSizeTableBuilder()
    builder.toTableString()
  }

  @Test
  public void toTableString_withOneMeasurement() {
    def expected = HEADER +
        "|firebase foo       |debug              |255000               |"

    def builder = new ApkSizeTableBuilder()
    builder.addApkSize("firebase foo", "debug", 255000)

    assertEquals(expected, builder.toTableString())
  }

  @Test
  public void toTableString_withThreeMeasurements() {
    def expected = HEADER +
        "|firebase foo       |debug              |255000               |\n" +
        "|google loo         |release            |4000                 |\n" +
        "|Appy Snap App      |Snappy             |781000               |"

    def builder = new ApkSizeTableBuilder()
    builder.addApkSize("firebase foo", "debug", 255000)
    builder.addApkSize("google loo", "release", 4000)
    builder.addApkSize("Appy Snap App", "Snappy", 781000)

    assertEquals(expected, builder.toTableString())
  }
}
