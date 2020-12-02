// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CustomModelTest {

  public static final String MODEL_NAME = "ModelName";
  public static final String MODEL_HASH = "dsf324";

  public static final String MODEL_URL = "https://project.firebase.com/modelName/23424.jpg";
  private static final long URL_EXPIRATION = 604800L;
  CustomModel CUSTOM_MODEL = new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0);

  CustomModel CUSTOM_MODEL_URL =
      new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION);

  @Test
  public void customModel_getName() {
    assertEquals(CUSTOM_MODEL.getName(), MODEL_NAME);
  }

  @Test
  public void customModel_getModelHash() {
    assertEquals(CUSTOM_MODEL.getModelHash(), MODEL_HASH);
  }

  @Test
  public void customModel_getFileSize() {
    assertEquals(CUSTOM_MODEL.getSize(), 100);
  }

  @Test
  public void customModel_getDownloadId() {
    assertEquals(CUSTOM_MODEL.getDownloadId(), 0);
  }

  @Test
  public void customModel_getFile_downloadIncomplete() {
    assertNull(CUSTOM_MODEL.getFile());
  }

  @Test
  public void customModel_getDownloadUrl() {
    assertEquals(CUSTOM_MODEL_URL.getDownloadUrl(), MODEL_URL);
  }

  @Test
  public void customModel_getDownloadUrlExpiry() {
    assertEquals(CUSTOM_MODEL_URL.getDownloadUrlExpiry(), URL_EXPIRATION);
  }

  @Test
  public void customModel_equals() {
    // downloading models
    assertEquals(CUSTOM_MODEL, new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0));
    assertNotEquals(CUSTOM_MODEL, new CustomModel(MODEL_NAME, MODEL_HASH, 101, 0));
    assertNotEquals(CUSTOM_MODEL, new CustomModel(MODEL_NAME, MODEL_HASH, 100, 101));
    // get model details models
    assertEquals(
        CUSTOM_MODEL_URL, new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION));
    assertNotEquals(
        CUSTOM_MODEL_URL, new CustomModel(MODEL_NAME, MODEL_HASH, 101, MODEL_URL, URL_EXPIRATION));
    assertNotEquals(
        CUSTOM_MODEL_URL,
        new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION + 10L));
  }

  @Test
  public void customModel_hashCode() {
    assertEquals(
        CUSTOM_MODEL.hashCode(), new CustomModel(MODEL_NAME, MODEL_HASH, 100, 0).hashCode());
    assertNotEquals(
        CUSTOM_MODEL.hashCode(), new CustomModel(MODEL_NAME, MODEL_HASH, 101, 0).hashCode());
    assertNotEquals(
        CUSTOM_MODEL.hashCode(), new CustomModel(MODEL_NAME, MODEL_HASH, 100, 101).hashCode());

    assertEquals(
        CUSTOM_MODEL_URL.hashCode(),
        new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION).hashCode());
    assertNotEquals(
        CUSTOM_MODEL_URL.hashCode(),
        new CustomModel(MODEL_NAME, MODEL_HASH, 101, MODEL_URL, URL_EXPIRATION).hashCode());
    assertNotEquals(
        CUSTOM_MODEL_URL.hashCode(),
        new CustomModel(MODEL_NAME, MODEL_HASH, 100, MODEL_URL, URL_EXPIRATION + 10L).hashCode());
  }
}
