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

package com.google.firebase.storage.internal;

import android.net.Uri;
import android.os.Build;
import com.google.firebase.emulators.EmulatedServiceSettings;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class StorageReferenceUriTest {

  private static final EmulatedServiceSettings EMULATOR_SETTINGS =
      new EmulatedServiceSettings("10.0.2.2", 1234);

  @Test
  public void testBucketRootUrl() {
    StorageReferenceUri uri = new StorageReferenceUri(Uri.parse("gs://bucket"));
    Assert.assertEquals(uri.getGsUri().toString(), "gs://bucket");
    Assert.assertEquals(
        uri.getHttpBaseUri().toString(), "https://firebasestorage.googleapis.com/v0");
    Assert.assertEquals(
        uri.getHttpUri().toString(), "https://firebasestorage.googleapis.com/v0/b/bucket");
  }

  @Test
  public void testBucketRootUrl_emulator() {
    StorageReferenceUri uri = new StorageReferenceUri(Uri.parse("gs://bucket"), EMULATOR_SETTINGS);
    Assert.assertEquals(uri.getGsUri().toString(), "gs://bucket");
    Assert.assertEquals(uri.getHttpBaseUri().toString(), "http://10.0.2.2:1234/v0");
    Assert.assertEquals(uri.getHttpUri().toString(), "http://10.0.2.2:1234/v0/b/bucket");
  }

  @Test
  public void testBucketObjectUrl_simple() {
    StorageReferenceUri uri = new StorageReferenceUri(Uri.parse("gs://bucket/object"));
    Assert.assertEquals(uri.getGsUri().toString(), "gs://bucket/object");
    Assert.assertEquals(
        uri.getHttpBaseUri().toString(), "https://firebasestorage.googleapis.com/v0");
    Assert.assertEquals(
        uri.getHttpUri().toString(), "https://firebasestorage.googleapis.com/v0/b/bucket/o/object");
  }

  @Test
  public void testBucketObjectUrl_simple_emulator() {
    StorageReferenceUri uri =
        new StorageReferenceUri(Uri.parse("gs://bucket/object"), EMULATOR_SETTINGS);
    Assert.assertEquals(uri.getGsUri().toString(), "gs://bucket/object");
    Assert.assertEquals(uri.getHttpBaseUri().toString(), "http://10.0.2.2:1234/v0");
    Assert.assertEquals(uri.getHttpUri().toString(), "http://10.0.2.2:1234/v0/b/bucket/o/object");
  }

  @Test
  public void testBucketObjectUrl_deep() {
    StorageReferenceUri uri = new StorageReferenceUri(Uri.parse("gs://bucket/object/path/child"));
    Assert.assertEquals(uri.getGsUri().toString(), "gs://bucket/object/path/child");
    Assert.assertEquals(
        uri.getHttpBaseUri().toString(), "https://firebasestorage.googleapis.com/v0");
    Assert.assertEquals(
        uri.getHttpUri().toString(),
        "https://firebasestorage.googleapis.com/v0/b/bucket/o/object%2Fpath%2Fchild");
  }

  @Test
  public void testBucketObjectUrl_deep_emulator() {
    StorageReferenceUri uri =
        new StorageReferenceUri(Uri.parse("gs://bucket/object/path/child"), EMULATOR_SETTINGS);
    Assert.assertEquals(uri.getGsUri().toString(), "gs://bucket/object/path/child");
    Assert.assertEquals(uri.getHttpBaseUri().toString(), "http://10.0.2.2:1234/v0");
    Assert.assertEquals(
        uri.getHttpUri().toString(), "http://10.0.2.2:1234/v0/b/bucket/o/object%2Fpath%2Fchild");
  }
}
