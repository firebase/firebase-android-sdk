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

package com.google.firebase;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.testing.FirebaseAppRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link FirebaseApp} behavior with missing core APIs. */
@RunWith(AndroidJUnit4.class)
public class FirebaseAppNoCoreApisTest {

  protected static final String GOOGLE_APP_ID = "1:855246033427:android:6e48bff8253f3f6e6e";
  protected static final String GOOGLE_API_KEY = "AIzaSyD3asb-2pEZVqMkmL6M9N6nHZRR_znhrh0";

  protected static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setApplicationId(GOOGLE_APP_ID)
          .setApiKey(GOOGLE_API_KEY)
          .build();

  private Context targetContext;

  @Before
  public void setUp() {
    targetContext = InstrumentationRegistry.getTargetContext();
  }

  @Rule public FirebaseAppRule firebaseAppRule = new FirebaseAppRule();

  @Test()
  public void missingScion() {
    FirebaseApp.initializeApp(targetContext, OPTIONS);
  }
}
