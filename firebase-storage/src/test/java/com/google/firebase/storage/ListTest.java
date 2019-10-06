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

package com.google.firebase.storage;

import static org.junit.Assert.fail;

import android.os.Build;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.storage.network.MockConnectionFactory;
import com.google.firebase.storage.network.NetworkLayerMock;
import com.google.firebase.testing.FirebaseAppRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link FirebaseStorage}. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class ListTest {

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
  public void validateListOptions() {
    StorageReference reference = FirebaseStorage.getInstance().getReference();

    try {
      reference.list(-1);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }

    try {
      reference.list(1001);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }

    try {
      reference.list(1000, null);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected.
    }
  }

  @Test
  public void listResultsWithSinglePage() throws InterruptedException {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("listSinglePage", true);
    Task<StringBuilder> task = TestCommandHelper.listFiles(/* pageSize= */ 10, /* pageCount= */ 1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("listSinglePage", task.getResult().toString());
  }

  @Test
  public void listResultsWithMultiplePages() throws InterruptedException {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("listMultiplePages", true);
    Task<StringBuilder> task = TestCommandHelper.listFiles(/* pageSize= */ 10, /* pageCount= */ 10);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("listMultiplePages", task.getResult().toString());
  }

  @Test
  public void listFailure() throws InterruptedException {
    MockConnectionFactory factory =
        NetworkLayerMock.ensureNetworkMock("listSinglePageFailed", false);
    Task<StringBuilder> task = TestCommandHelper.listFiles(/* pageSize= */ 10, /* pageCount= */ 1);

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("listSinglePageFailed", task.getResult().toString());
  }

  @Test
  public void listAll() throws InterruptedException {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("listAll", true);
    Task<StringBuilder> task = TestCommandHelper.listAllFiles();

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("listAll", task.getResult().toString());
  }

  @Test
  public void listAllWithFailure() throws InterruptedException {
    MockConnectionFactory factory = NetworkLayerMock.ensureNetworkMock("listAllFailed", false);
    Task<StringBuilder> task = TestCommandHelper.listAllFiles();

    TestUtil.await(task);

    factory.verifyOldMock();
    TestUtil.verifyTaskStateChanges("listAllFailed", task.getResult().toString());
  }
}
