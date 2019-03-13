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

import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.storage.network.MockConnectionFactory;
import com.google.firebase.storage.network.NetworkLayerMock;
import com.google.firebase.testing.FirebaseAppRule;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link FirebaseStorage}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class MetadataTest {

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

  @SuppressWarnings("ConstantConditions")
  @Test
  public void updateMetadata() throws Exception {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("updateMetadata", true);

    Task<StringBuilder> task = TestCommandHelper.testUpdateMetadata();
    for (int i = 0; i < 3000; i++) {
      Robolectric.flushForegroundThreadScheduler();
      if (task.isComplete()) {
        // success!
        factory.verifyOldMock();
        TestUtil.verifyTaskStateChanges("updateMetadata", task.getResult().toString());
        return;
      }
      Thread.sleep(1);
    }
    assert (false);
  }

  @Test
  public void unicodeMetadata() throws Exception {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("unicodeMetadata", true);

    Task<StringBuilder> task = TestCommandHelper.testUnicodeMetadata();
    for (int i = 0; i < 3000; i++) {
      Robolectric.flushForegroundThreadScheduler();
      if (task.isComplete()) {
        // success!
        factory.verifyOldMock();
        TestUtil.verifyTaskStateChanges("unicodeMetadata", task.getResult().toString());
        return;
      }
      Thread.sleep(1);
    }

    Assert.fail("Task did not complete");
  }

  @Test
  public void clearMetadata() throws Exception {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("clearMetadata", true);

    Task<StringBuilder> task = TestCommandHelper.testClearMetadata();
    for (int i = 0; i < 3000; i++) {
      Robolectric.flushForegroundThreadScheduler();
      if (task.isComplete()) {
        // success!
        factory.verifyOldMock();
        TestUtil.verifyTaskStateChanges("clearMetadata", task.getResult().toString());
        return;
      }
      Thread.sleep(1);
    }

    Assert.fail("Task did not complete");
  }
}
