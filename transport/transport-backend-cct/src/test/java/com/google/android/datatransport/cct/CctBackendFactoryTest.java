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

package com.google.android.datatransport.cct;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.runtime.backends.CreationContext;
import com.google.android.datatransport.runtime.time.TestClock;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class CctBackendFactoryTest {
  private static final long INITIAL_WALL_TIME = 200L;
  private static final long INITIAL_UPTIME = 10L;
  private TestClock wallClock = new TestClock(INITIAL_WALL_TIME);
  private TestClock uptimeClock = new TestClock(INITIAL_UPTIME);

  @Test
  public void create_returnCCTBackend_WhenBackendNameIsCCT() throws MalformedURLException {
    CctBackendFactory cctBackendFactory = new CctBackendFactory();
    CreationContext creationContext =
        CreationContext.create(
            RuntimeEnvironment.application,
            wallClock,
            uptimeClock,
            CCTDestination.DESTINATION_NAME);

    CctTransportBackend backend = (CctTransportBackend) cctBackendFactory.create(creationContext);
    assertThat(backend.endPoint).isEqualTo(new URL(CCTDestination.DEFAULT_END_POINT));
  }
}
