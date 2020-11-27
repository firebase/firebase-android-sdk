/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.remoteconfig.bandwagoner;

import androidx.test.espresso.idling.CountingIdlingResource;

/**
 * Manager for a singleton instance of {@link CountingIdlingResource}.
 *
 * @author Miraziz Yusupov
 */
public class IdlingResourceManager {
  private static CountingIdlingResource idlingResource;

  public static CountingIdlingResource getInstance() {
    if (idlingResource == null) {
      idlingResource =
          new CountingIdlingResource("BandwagonerIdlingResource", /*debugCounting=*/ true);
    }
    return idlingResource;
  }
}
