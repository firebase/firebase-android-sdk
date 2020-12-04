// Copyright 2020 Google LLC
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

package com.google.firebase.ml.modeldownloader.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.android.datatransport.Transport;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlStat.EventName;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataTransportMlStatsSenderTest {

  @Mock private Transport<FirebaseMlStat> mockTransport;

  private DataTransportMlStatsSender statsSender;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    statsSender = new DataTransportMlStatsSender(mockTransport);
  }

  @Test
  public void testSendStatsSuccessful() {
    doNothing().when(mockTransport).send(any());

    final FirebaseMlStat stat1 =
        FirebaseMlStat.builder().setEventName(EventName.MODEL_UPDATE).build();
    final FirebaseMlStat stat2 =
        FirebaseMlStat.builder().setEventName(EventName.MODEL_DOWNLOAD).build();

    statsSender.sendStats(stat1);
    statsSender.sendStats(stat2);

    verify(mockTransport, times(2)).send(any());
  }
}
