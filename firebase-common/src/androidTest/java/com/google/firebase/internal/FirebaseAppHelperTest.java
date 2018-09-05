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

package com.google.firebase.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApiNotAvailableException;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseApp.IdTokenListener;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Unit tests for {@link FirebaseAppHelper}. */
@RunWith(AndroidJUnit4.class)
public class FirebaseAppHelperTest {

  @Test
  public void testGetTokenResult() {
    FirebaseApp app = mock(FirebaseApp.class);
    FirebaseAppHelper.getToken(app, true);
    verify(app).getToken(true);

    FirebaseAppHelper.getToken(app, false);
    verify(app).getToken(false);
  }

  @Test
  public void testAddIdTokenListener() {
    FirebaseApp app = mock(FirebaseApp.class);
    IdTokenListener listener = mock(IdTokenListener.class);
    FirebaseAppHelper.addIdTokenListener(app, listener);
    verify(app).addIdTokenListener(listener);
  }

  @Test
  public void testRemoveIdTokenListener() {
    FirebaseApp app = mock(FirebaseApp.class);
    IdTokenListener listener = mock(IdTokenListener.class);
    FirebaseAppHelper.removeIdTokenListener(app, listener);
    verify(app).removeIdTokenListener(listener);
  }

  @Test
  public void testGetUid() throws FirebaseApiNotAvailableException {
    FirebaseApp app = mock(FirebaseApp.class);
    FirebaseAppHelper.getUid(app);
    verify(app).getUid();
  }
}
