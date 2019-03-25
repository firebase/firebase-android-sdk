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

import static com.google.firebase.common.testutil.Assert.assertThrows;

import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.storage.network.MockConnectionFactory;
import com.google.firebase.storage.network.NetworkLayerMock;
import com.google.firebase.testing.FirebaseAppRule;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Tests for {@link FirebaseStorage}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class StorageReferenceTest {

  @Rule public RetryRule retryRule = new RetryRule(3);
  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  private FirebaseApp app;

  @Before
  public void setUp() throws Exception {
    RobolectricThreadFix.install();
    MockClockHelper.install();
    app = TestUtil.createApp();
  }

  @After
  public void tearDown() {
    FirebaseStorageComponent component = app.get(FirebaseStorageComponent.class);
    component.clearInstancesForTesting();
  }

  @Test
  public void emptyTest() throws Exception {
    Assert.assertTrue(true);
  }

  @Test
  public void retainsProperties() throws Exception {
    FirebaseStorage instance = FirebaseStorage.getInstance();
    instance.setMaxDownloadRetryTimeMillis(42);
    instance.setMaxUploadRetryTimeMillis(1337);
    Assert.assertEquals(42, instance.getReference().getStorage().getMaxDownloadRetryTimeMillis());
    Assert.assertEquals(1337, instance.getReference().getStorage().getMaxUploadRetryTimeMillis());
  }

  @Test
  public void defaultInitTest() throws Exception {
    StorageReference ref = FirebaseStorage.getInstance().getReference();
    Assert.assertEquals("gs://project-5516366556574091405.appspot.com/", ref.toString());
  }

  @Test
  public void initWithCustomUriTest() throws Exception {
    StorageReference ref = FirebaseStorage.getInstance("gs://foo-bar.appspot.com/").getReference();
    Assert.assertEquals("gs://foo-bar.appspot.com/", ref.toString());
  }

  @Test
  public void allStorageObjectsAreEqual() throws Exception {
    FirebaseStorage original = FirebaseStorage.getInstance();
    FirebaseStorage copy =
        FirebaseStorage.getInstance("gs://project-5516366556574091405.appspot.com/");
    Assert.assertSame(copy, original); // Pointer comparison intended
    copy = FirebaseStorage.getInstance("gs://project-5516366556574091405.appspot.com");
    Assert.assertSame(copy, original);
    copy =
        FirebaseStorage.getInstance(
            FirebaseApp.getInstance(), "gs://project-5516366556574091405.appspot.com/");
    Assert.assertSame(copy, original);
    Assert.assertEquals(
        "gs://project-5516366556574091405.appspot.com/",
        copy.getReference().getStorageUri().toString());
  }

  @Test
  public void initWithBadUriTest() throws Exception {
    try {
      FirebaseStorage.getInstance("http://foo-bar.appspot.com/");
      Assert.fail("Expected exception");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(
          "Please use a gs:// URL for your Firebase Storage bucket.", e.getMessage());
    }
  }

  @Test
  public void initWithPathTest() throws Exception {
    try {
      FirebaseStorage.getInstance("gs://project-5516366556574091405.appspot.com/foo");
      Assert.fail("Expected exception");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals("The storage Uri cannot contain a path element.", e.getMessage());
    }
  }

  @Test
  public void initWithNoSchemeTest() throws Exception {
    try {
      FirebaseStorage.getInstance("foo-bar.appspot.com");
      Assert.fail("Expected exception");
    } catch (IllegalArgumentException e) {
      Assert.assertEquals(
          "Please use a gs:// URL for your Firebase Storage bucket.", e.getMessage());
    }
  }

  @Test
  public void initWithDefaultAndCustomUriTest() throws Exception {
    StorageReference defaultRef = FirebaseStorage.getInstance().getReference();
    StorageReference customRef =
        FirebaseStorage.getInstance("gs://foo-bar.appspot.com/").getReference();
    Assert.assertEquals("gs://project-5516366556574091405.appspot.com/", defaultRef.toString());
    Assert.assertEquals("gs://foo-bar.appspot.com/", customRef.toString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void badInitTest() throws Exception {
    FirebaseStorage.getInstance()
        .getReference("gs://project-5516366556574091405.appspot.com/child");
  }

  @Test
  public void badInitTest2() throws Exception {
    FirebaseApp.clearInstancesForTest();
    assertThrows(IllegalStateException.class, () -> FirebaseStorage.getInstance().getReference());
  }

  @Test
  public void initWithApp() throws Exception {
    FirebaseApp app2 =
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApiKey("AIzaSyCkEhVjf3pduRDt6d1yKOMitrUEke8agEM")
                .setApplicationId("fooey")
                .setStorageBucket("benwu-test2.storage.firebase.com")
                .build(),
            "app2");

    StorageReference ref =
        FirebaseStorage.getInstance(app2)
            .getReferenceFromUrl("gs://benwu-test2.storage.firebase.com/child");

    Assert.assertEquals("child", ref.getName());
  }

  @Test(expected = IllegalArgumentException.class)
  public void badInitWithApp1() throws Exception {
    FirebaseStorage.getInstance().getReference("gs://bucket/child");
  }

  @SuppressWarnings("ConstantConditions")
  @Test(expected = IllegalArgumentException.class)
  public void badInitWithApp2() throws Exception {
    FirebaseStorage.getInstance().getReference(null);
  }

  @Test
  public void urlUriEquivalence() throws Exception {
    StorageReference ref =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl("gs://project-5516366556574091405.appspot.com/child/image.png");
    StorageReference ref2 =
        FirebaseStorage.getInstance()
            .getReferenceFromUrl(
                "https://firebasestorage.googleapis.com/v0/b/project-"
                    + "5516366556574091405.appspot.com/o/child%2Fimage.png?alt=media&"
                    + "token=42a1b22e-5e56-4337-bd61-f177233b40bc");

    Assert.assertEquals(ref, ref2);
    Assert.assertEquals("/child/image.png", ref.getPath());
    Assert.assertEquals("project-5516366556574091405.appspot.com", ref.getBucket());
  }

  @Test
  public void badGSUriScheme() throws Exception {
    boolean thrown = false;
    try {
      FirebaseStorage.getInstance()
          .getReferenceFromUrl("gs2://project-5516366556574091405.appspot.com/child/image.png");
    } catch (IllegalArgumentException e) {
      thrown = true;
    }
    Assert.assertTrue(thrown);
  }

  @Test
  public void badGSUriAuth1() throws Exception {
    boolean thrown = false;
    try {
      FirebaseStorage.getInstance()
          .getReferenceFromUrl("gs2://bucketstorage.firebase.com/child/image.png");
    } catch (IllegalArgumentException e) {
      thrown = true;
    }
    Assert.assertTrue(thrown);
  }

  @Test
  public void badGSUriAuth2() throws Exception {
    boolean thrown = false;
    try {
      FirebaseStorage.getInstance()
          .getReferenceFromUrl("gs2://bucket.storagefirebase.com/child/image.png");
    } catch (IllegalArgumentException e) {
      thrown = true;
    }
    Assert.assertTrue(thrown);
  }

  @Test
  public void badURL1() throws Exception {
    boolean thrown = false;
    try {
      FirebaseStorage.getInstance()
          .getReferenceFromUrl(
              "https://www.googleapis.com/v0/b/project-5516366556574091405.appspot"
                  + ".com/o/child%2Fimage.png?alt=media&token=42a1b22e-5e56-4337-bd61-f177233b40bc");
    } catch (IllegalArgumentException e) {
      thrown = true;
    }
    Assert.assertTrue(thrown);
  }

  @Test
  public void badURL2() throws Exception {
    boolean thrown = false;
    try {
      FirebaseStorage.getInstance()
          .getReferenceFromUrl(
              "https://firebasestorage.googleapis.com/v0/b/"
                  + "bucketstorage.firebase.com/o/child%2Fimage"
                  + ".png?alt=media&token=42a1b22e-5e56-4337-bd61-f177233b40bc");
    } catch (IllegalArgumentException e) {
      thrown = true;
    }
    Assert.assertTrue(thrown);
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  public void downloadUrl() throws Exception {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("downloadUrl", true);

    Task<StringBuilder> task = TestCommandHelper.testDownloadUrl();
    for (int i = 0; i < 3000; i++) {
      Robolectric.flushForegroundThreadScheduler();
      if (task.isComplete()) {
        // success!
        factory.verifyOldMock();
        TestUtil.verifyTaskStateChanges("downloadUrl", task.getResult().toString());
        return;
      }
      Thread.sleep(1);
    }
    Assert.fail();
  }
}
