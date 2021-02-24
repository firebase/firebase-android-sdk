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

package com.google.firebase.storage;

import android.os.Build;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.internal.MockClockHelper;
import com.google.firebase.storage.internal.RobolectricThreadFix;
import com.google.firebase.storage.network.GetNetworkRequest;
import com.google.firebase.storage.network.ListNetworkRequest;
import com.google.firebase.storage.network.NetworkRequest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.LOLLIPOP_MR1)
public class NetworkRequestTest {

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
    app.delete();
  }

  @Test
  public void getRequest_returnsProductionUrl() {
    FirebaseStorage storage = FirebaseStorage.getInstance(app);

    StorageReference reference = storage.getReference("child");
    NetworkRequest request =
        new GetNetworkRequest(reference.getStorageReferenceUri(), reference.getApp(), 0);

    Assert.assertEquals(
        "https://firebasestorage.googleapis.com/v0/b/fooey.appspot.com/o/child",
        request.getURL().toString());
  }

  @Test
  public void getRequest_returnsEmulatorUrl() {
    FirebaseStorage storage = FirebaseStorage.getInstance(app);
    storage.useEmulator("10.0.2.2", 9199);

    StorageReference reference = storage.getReference("child");
    NetworkRequest request =
        new GetNetworkRequest(reference.getStorageReferenceUri(), reference.getApp(), 0);

    Assert.assertEquals(
        "http://10.0.2.2:9199/v0/b/fooey.appspot.com/o/child", request.getURL().toString());
  }

  @Test
  public void listRequest_returnsProductionUrl() {
    FirebaseStorage storage = FirebaseStorage.getInstance(app);

    StorageReference ref = storage.getReference();
    NetworkRequest request =
        new ListNetworkRequest(ref.getStorageReferenceUri(), ref.getApp(), 1, null);

    Assert.assertEquals(
        "https://firebasestorage.googleapis.com/v0/b/fooey.appspot.com/o",
        request.getURL().toString());
  }

  @Test
  public void listRequest_returnsEmulatorUrl() {
    FirebaseStorage storage = FirebaseStorage.getInstance(app);
    storage.useEmulator("10.0.2.2", 9199);

    StorageReference ref = storage.getReference();
    NetworkRequest request =
        new ListNetworkRequest(ref.getStorageReferenceUri(), ref.getApp(), 1, null);

    Assert.assertEquals(
        "http://10.0.2.2:9199/v0/b/fooey.appspot.com/o", request.getURL().toString());
  }
}
