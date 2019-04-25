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

import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4.class)
public class ApkSizeJsonBuilderTest {

  @Test(expected = IllegalStateException.class)
  public void toJsonString_ThrowsWhenZeroAdded() {
    def builder = new ApkSizeJsonBuilder(13)
    builder.toJsonString()
  }

  @Test
  public void toJsonString_hasPullRequestTable() {
    def builder = new ApkSizeJsonBuilder(117)
    builder.addApkSize(5, 145000)
    def json = new JSONObject(builder.toJsonString())

    def prTable = json.getJSONArray("tables").getJSONObject(0)
    def prColumns = prTable.getJSONArray("column_names")
    def prMeasurements = prTable.getJSONArray("replace_measurements")

    assertEquals("Bad table name", "PullRequests", prTable.getString("table_name"))
    assertEquals("Bad column name", "pull_request_id", prColumns.getString(0))
    assertEquals("Bad pull request number", 117, prMeasurements.getJSONArray(0).getInt(0))
    assertEquals("Too many columns", 1, prColumns.length())
    assertEquals("Too many pull requests", 1, prMeasurements.length())
    assertEquals("Too many pull request numbers", 1, prMeasurements.getJSONArray(0).length())
  }

  @Test
  public void toJsonString_hasApkSizeTableWithOneMeasurement() {
    def builder = new ApkSizeJsonBuilder(2115)
    builder.addApkSize(37, 577000)
    def json = new JSONObject(builder.toJsonString())

    def asTable = json.getJSONArray("tables").getJSONObject(1)
    def asColumns = asTable.getJSONArray("column_names")
    def asMeasurements = asTable.getJSONArray("replace_measurements")

    assertEquals("Bad table name", "ApkSizes", asTable.getString("table_name"))
    assertEquals("Bad column name", "pull_request_id", asColumns.getString(0))
    assertEquals("Bad column name", "sdk_id", asColumns.getString(1))
    assertEquals("Bad column name", "apk_size", asColumns.getString(2))
    assertEquals("Bad pull request number", 2115, asMeasurements.getJSONArray(0).getInt(0))
    assertEquals("Bad SDK ID", 37, asMeasurements.getJSONArray(0).getInt(1))
    assertEquals("Bad APK size", 577000, asMeasurements.getJSONArray(0).getInt(2))
    assertEquals("Too many columns", 3, asColumns.length())
    assertEquals("Too many measurements", 1, asMeasurements.length())
    assertEquals("Too many values", 3, asMeasurements.getJSONArray(0).length())
  }

  @Test
  public void toJsonString_hasApkSizeTableWithThreeMeasurements() {
    def builder = new ApkSizeJsonBuilder(5)
    builder.addApkSize(37, 577000)
    builder.addApkSize(14, 819000)
    builder.addApkSize(92, 133000)
    def json = new JSONObject(builder.toJsonString())

    def asTable = json.getJSONArray("tables").getJSONObject(1)
    def asColumns = asTable.getJSONArray("column_names")
    def asMeasurements = asTable.getJSONArray("replace_measurements")

    // The table itself.
    assertEquals("Bad table name", "ApkSizes", asTable.getString("table_name"))
    assertEquals("Bad column name", "pull_request_id", asColumns.getString(0))
    assertEquals("Bad column name", "sdk_id", asColumns.getString(1))
    assertEquals("Bad column name", "apk_size", asColumns.getString(2))
    assertEquals("Too many columns", 3, asColumns.length())
    assertEquals("Too many measurements", 3, asMeasurements.length())

    // First measurement.
    assertEquals("Bad pull request number", 5, asMeasurements.getJSONArray(0).getInt(0))
    assertEquals("Bad SDK ID", 37, asMeasurements.getJSONArray(0).getInt(1))
    assertEquals("Bad APK size", 577000, asMeasurements.getJSONArray(0).getInt(2))
    assertEquals("Too many values", 3, asMeasurements.getJSONArray(0).length())

    // Second measurement.
    assertEquals("Bad pull request number", 5, asMeasurements.getJSONArray(1).getInt(0))
    assertEquals("Bad SDK ID", 14, asMeasurements.getJSONArray(1).getInt(1))
    assertEquals("Bad APK size", 819000, asMeasurements.getJSONArray(1).getInt(2))
    assertEquals("Too many values", 3, asMeasurements.getJSONArray(1).length())

    // Third measurement.
    assertEquals("Bad pull request number", 5, asMeasurements.getJSONArray(2).getInt(0))
    assertEquals("Bad SDK ID", 92, asMeasurements.getJSONArray(2).getInt(1))
    assertEquals("Bad APK size", 133000, asMeasurements.getJSONArray(2).getInt(2))
    assertEquals("Too many values", 3, asMeasurements.getJSONArray(2).length())
  }
}
