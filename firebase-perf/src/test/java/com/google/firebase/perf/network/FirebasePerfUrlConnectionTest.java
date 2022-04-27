// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.network;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.firebase.perf.FirebasePerformanceTestBase;
import com.google.firebase.perf.transport.TransportManager;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.perf.util.URLWrapper;
import com.google.firebase.perf.v1.ApplicationProcessState;
import com.google.firebase.perf.v1.NetworkRequestMetric;
import com.google.firebase.perf.v1.NetworkRequestMetric.NetworkClientErrorReason;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

/** Unit tests for {@link com.google.firebase.perf.network.FirebasePerfUrlConnection}. */
@RunWith(RobolectricTestRunner.class)
public class FirebasePerfUrlConnectionTest extends FirebasePerformanceTestBase {

  @Mock private TransportManager transportManager;
  @Mock private Timer timer;
  @Captor private ArgumentCaptor<NetworkRequestMetric> networkArgumentCaptor;

  @Before
  public void setUp() {
    initMocks(this);
    when(timer.getMicros()).thenReturn((long) 1000);
    when(timer.getDurationMicros()).thenReturn((long) 2000);
  }

  @Test
  public void testOpenStreamHttpConnectionError() throws IOException {
    URLWrapper wrapper = mock(URLWrapper.class);
    when(wrapper.toString()).thenReturn("www.google.com");
    when(wrapper.openConnection()).thenThrow(IOException.class);

    try {
      FirebasePerfUrlConnection.openStream(wrapper, transportManager, timer);
      fail("expected IOException");
    } catch (IOException e) {
      verify(transportManager)
          .log(
              networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
      NetworkRequestMetric metric = networkArgumentCaptor.getValue();
      verifyNetworkRequestMetric(metric);
    }
  }

  @Test
  public void testGetContentError() throws IOException {
    URLWrapper wrapper = mock(URLWrapper.class);
    when(wrapper.toString()).thenReturn("www.google.com");
    when(wrapper.openConnection()).thenThrow(IOException.class);

    try {
      FirebasePerfUrlConnection.getContent(wrapper, transportManager, timer);
      fail("expected IOException");
    } catch (IOException e) {
      verify(transportManager)
          .log(
              networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
      NetworkRequestMetric metric = networkArgumentCaptor.getValue();
      verifyNetworkRequestMetric(metric);
    }
  }

  @Test
  public void testGetContentClasses() throws IOException {
    @SuppressWarnings("rawtypes")
    Class[] classes = {TransportManager.class};
    URLWrapper wrapper = mock(URLWrapper.class);
    when(wrapper.toString()).thenReturn("www.google.com");
    when(wrapper.openConnection()).thenThrow(IOException.class);

    try {
      FirebasePerfUrlConnection.getContent(wrapper, classes, transportManager, timer);
      fail("expected IOException");
    } catch (IOException e) {
      verify(transportManager)
          .log(
              networkArgumentCaptor.capture(), ArgumentMatchers.any(ApplicationProcessState.class));
      NetworkRequestMetric metric = networkArgumentCaptor.getValue();
      verifyNetworkRequestMetric(metric);
    }
  }

  private static void verifyNetworkRequestMetric(NetworkRequestMetric metric) {
    assertEquals(1000, metric.getClientStartTimeUs());
    assertEquals("www.google.com", metric.getUrl());
    assertEquals(2000, metric.getTimeToResponseCompletedUs());
    assertFalse(metric.hasHttpMethod());
    assertFalse(metric.hasHttpResponseCode());
    assertFalse(metric.hasResponsePayloadBytes());
    assertFalse(metric.hasResponseContentType());
    assertEquals(
        NetworkClientErrorReason.GENERIC_CLIENT_ERROR, metric.getNetworkClientErrorReason());
  }
}
