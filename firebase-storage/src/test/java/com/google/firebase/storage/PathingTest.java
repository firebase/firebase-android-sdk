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

package com.google.firebase.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.testing.FirebaseAppRule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Tests for {@link FirebaseStorage}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class PathingTest {

  @Rule public RetryRule retryRule = new RetryRule(3);
  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  FirebaseApp app;

  @Before
  public void setUp() throws Exception {
    RobolectricThreadFix.install();
    MockClockHelper.install();
    app =
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application.getApplicationContext(),
            new FirebaseOptions.Builder().setApiKey("fooey").setApplicationId("fooey").build());
  }

  @After
  public void tearDown() {
    FirebaseStorageComponent component = app.get(FirebaseStorageComponent.class);
    component.clearInstancesForTesting();
  }

  @Test
  public void emptyTest() throws Exception {
    assertTrue(true);
  }

  @Test
  public void equalsTest() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child");
    StorageReference ref2 =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child");

    assertEquals(ref, ref2);
  }

  @Test
  public void bucketNotEnforced() throws Exception {
    FirebaseStorage storage = FirebaseStorage.getInstance();
    storage.getReferenceFromUrl("gs://benwu-test1.storage.firebase.com/child");
    storage.getReferenceFromUrl("gs://benwu-test2.storage.firebase.com/child");
  }

  @Test
  public void bucketEnforced() throws Exception {
    FirebaseStorage storage = FirebaseStorage.getInstance("gs://benwu-test1.storage.firebase.com");
    storage.getReferenceFromUrl("gs://benwu-test1.storage.firebase.com/child");
    try {
      storage.getReferenceFromUrl("gs://benwu-test2.storage.firebase.com/child");
      Assert.fail("Bucket name in Url doesn't match bucket in initialization");
    } catch (IllegalArgumentException ignore) {
      //
    }
  }

  @Test
  public void parentReturnsRoot() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child");
    assertEquals(ref.getRoot(), ref.getParent());
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void parentParentReturnsRoot() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child/child/");
    assertEquals(ref.getRoot(), ref.getParent().getParent());
  }

  @Test
  public void rootParentIsNull() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/");
    assertEquals(null, ref.getParent());
  }

  @Test
  public void parentTest() throws Exception {

    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child/child2");
    StorageReference ref2 =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child/");

    assertEquals(ref.getParent(), ref2);
  }

  @Test
  public void rootEndingSlash() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com");
    StorageReference ref2 =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/");

    assertEquals(ref, ref2);
  }

  @Test
  public void endingSlash() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child");
    StorageReference ref2 =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child/");

    assertEquals(ref, ref2);
  }

  @Test
  public void dotdotTest() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child/../");

    assertEquals("..", ref.getName());
  }

  @Test
  public void slashslashTest() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child//");
    assertEquals(
        ref.getParent(),
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase.com/"));
  }

  @Test
  public void nameTest() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child/image.png");

    assertEquals("image.png", ref.getName());
  }

  @Test
  public void nameRootTest() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/image.png");

    assertEquals("image.png", ref.getName());
  }

  @Test
  public void childTest() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/");
    StorageReference ref2 =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/child");

    assertEquals(ref.child("child"), ref2);
  }

  @Test
  public void missingPathTest() throws Exception {
    try {
      FirebaseStorage.getInstance().getReferenceFromUrl("http://foo-bar.appspot.com/");
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Firebase Storage URLs must point to an object in your Storage Bucket. Please "
              + "obtain a URL using the Firebase Console or getDownloadUrl().",
          e.getMessage());
    }
  }

  @Test
  public void badPathTest() throws Exception {
    try {
      FirebaseStorage.getInstance().getReferenceFromUrl("http://foo-bar.appspot.com/foo");
      fail("Expected exception");
    } catch (IllegalArgumentException e) {
      assertEquals(
          "Firebase Storage URLs must point to an object in your Storage Bucket. Please "
              + "obtain a URL using the Firebase Console or getDownloadUrl().",
          e.getMessage());
    }
  }

  @Test
  public void compatibilityTest() {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1" + ".storage.firebase.com/");

    // TODO, synchronize this behavior
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/foo/bar", ref.child("/foo///////bar/").toString());
    assertEquals("gs://benwu-test1.storage.firebase.com/images", ref.child("images").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo", ref.child("images/foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo", ref.child("images//foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("images").child("foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("/images").child("foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("/images").child("/foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("images/").child("foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("images/").child("/foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("images//").child("foo").toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        ref.child("images//").child("foo/").toString());

    assertEquals(
        "gs://benwu-test1.storage.firebase.com/foo/bar",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com//foo///////bar/")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images/foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images//foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images/foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com//images/foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com//images//foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images/foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images//foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images//foo")
            .toString());
    assertEquals(
        "gs://benwu-test1.storage.firebase.com/images/foo",
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://benwu-test1.storage.firebase" + ".com/images//foo/")
            .toString());
  }
}
