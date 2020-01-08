// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.ndk;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.ndk.BinaryImagesConverter.FileIdStrategy;
import com.google.firebase.crashlytics.test.R;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.json.JSONException;
import org.json.JSONObject;

public class BinaryImagesConverterTest extends TestCase {

  private static final String FILE_ID = "BinaryImagesConverterTestFileId";

  private static Context getContext() {
    return ApplicationProvider.getApplicationContext();
  }

  private BinaryImagesConverter converter;

  protected void setUp() throws Exception {
    converter = new BinaryImagesConverter(getContext(), new TestFileIdStrategy());
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }

  public void testConvertBinaryLibsJson() throws Exception {
    final InputStream rawStream =
        getContext().getResources().openRawResource(R.raw.test_binary_libs);
    final String rawJson = CommonUtils.streamToString(rawStream);

    final InputStream convertedStream =
        getContext().getResources().openRawResource(R.raw.test_binary_images_output);
    final String expectedConvertedJson = CommonUtils.streamToString(convertedStream);

    final String convertedJson = new String(converter.convert(rawJson));

    try {
      // Confirm the converted JSON is parse-able
      final JSONObject convertedJsonObj = new JSONObject(convertedJson);
    } catch (JSONException e) {
      fail("Could not parse converted JSON");
    }

    Assert.assertEquals(expectedConvertedJson, convertedJson);
  }

  public void testConvertMapsJson() throws Exception {
    final InputStream rawStream = getContext().getResources().openRawResource(R.raw.test_maps);

    final InputStream convertedStream =
        getContext().getResources().openRawResource(R.raw.test_binary_images_output);
    final String expectedConvertedJson = CommonUtils.streamToString(convertedStream);

    final String convertedJson =
        new String(converter.convert(new BufferedReader(new InputStreamReader(rawStream))));

    try {
      // Confirm the converted JSON is parse-able
      final JSONObject convertedJsonObj = new JSONObject(convertedJson);
    } catch (JSONException e) {
      fail("Could not parse converted JSON");
    }

    Assert.assertEquals(expectedConvertedJson, convertedJson);
  }

  private static class TestFileIdStrategy implements FileIdStrategy {

    @Override
    public String createId(File file) throws IOException {
      return FILE_ID;
    }
  }
}
