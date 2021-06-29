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

package com.google.firebase.appcheck.debug.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import java.util.concurrent.ExecutionException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FirebaseAppCheckTest {
  private final DebugAppCheckTestHelper debugAppCheckTestHelper =
      DebugAppCheckTestHelper.fromInstrumentationArgs();

  private FirebaseAppCheck firebaseAppCheck;

  @Before
  public void setUp() {
    FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext());
    firebaseAppCheck = FirebaseAppCheck.getInstance();
  }

  @After
  public void tearDown() {
    FirebaseApp.clearInstancesForTest();
  }

  @Test
  public void exchangeDebugSecretForAppCheckToken_interopApi() throws Exception {
    debugAppCheckTestHelper.withDebugProvider(
        () -> {
          Task<AppCheckTokenResult> tokenResultTask = firebaseAppCheck.getToken(true);
          Tasks.await(tokenResultTask);
          AppCheckTokenResult result = tokenResultTask.getResult();
          assertThat(result.getToken()).isNotEmpty();
          assertThat(result.getError()).isNull();
        });
  }

  @Test
  public void exchangeDebugSecretForAppCheckToken_publicApi() throws Exception {
    debugAppCheckTestHelper.withDebugProvider(
        () -> {
          Task<AppCheckToken> tokenTask = firebaseAppCheck.getAppCheckToken(true);
          Tasks.await(tokenTask);
          AppCheckToken result = tokenTask.getResult();
          assertThat(result.getToken()).isNotEmpty();
        });
  }

  @Test
  @Ignore("TODO: Enable once we have a project with enforcement enabled in CI.")
  public void firebaseStorageListFiles_withValidAppCheckToken_success() throws Exception {
    debugAppCheckTestHelper.withDebugProvider(
        () -> {
          FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
          Task<ListResult> listResultTask = firebaseStorage.getReference().listAll();

          Tasks.await(listResultTask);
        });
  }

  @Test
  @Ignore("TODO: Enable once we have a project with enforcement enabled in CI.")
  public void firebaseStorageListFiles_withAppCheckDummyHeader_fails() throws Exception {
    FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
    Task<ListResult> listResultTask = firebaseStorage.getReference().listAll();
    assertThrows(ExecutionException.class, () -> Tasks.await(listResultTask));
  }
}
