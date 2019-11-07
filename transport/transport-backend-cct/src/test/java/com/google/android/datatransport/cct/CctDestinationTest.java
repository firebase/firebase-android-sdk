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

package com.google.android.datatransport.cct;

import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.runtime.TransportRuntime;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class CctDestinationTest {

  @Before
  public void setUp() {
    TransportRuntime.initialize(ApplicationProvider.getApplicationContext());
  }

  private static Transport<String> getTransport(Encoding encoding) {
    return TransportRuntime.getInstance()
        .newFactory(CCTDestination.INSTANCE)
        .getTransport("TRANSPORT_NAME", String.class, encoding, String::getBytes);
  }

  @Test
  public void cctDestination_shouldSupportProtoAndJson() {
    for (Encoding encoding : Arrays.asList(Encoding.of("proto"), Encoding.of("json"))) {
      getTransport(encoding);
    }
  }

  @Test
  public void cctDestination_shouldOnlySupportProtoAndJson() {
    assertThrows(IllegalArgumentException.class, () -> getTransport(Encoding.of("unsupported")));
  }
}
