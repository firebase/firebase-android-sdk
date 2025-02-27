/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.ktx

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.initialize
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.FirebasePerformance.HttpMethod
import com.google.firebase.perf.application.AppStateMonitor
import com.google.firebase.perf.metrics.HttpMetric
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.metrics.getTraceCounter
import com.google.firebase.perf.metrics.getTraceCounterCount
import com.google.firebase.perf.transport.TransportManager
import com.google.firebase.perf.util.Clock
import com.google.firebase.perf.util.Timer
import com.google.firebase.perf.v1.ApplicationProcessState
import com.google.firebase.perf.v1.NetworkRequestMetric
import com.google.firebase.perf.v1.TraceMetric
import com.google.firebase.platforminfo.UserAgentPublisher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

const val APP_ID = "1:149208680807:android:0000000000000000"
const val API_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

const val EXISTING_APP = "existing"

abstract class BaseTestCase {
  @Before
  open fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val shadowPackageManager = Shadows.shadowOf(context.packageManager)
    val packageInfo = shadowPackageManager.getInternalMutablePackageInfo(context.packageName)
    packageInfo.versionName = "1.0.0"

    Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder()
        .setApplicationId(APP_ID)
        .setApiKey(API_KEY)
        .setProjectId("123")
        .build()
    )

    Firebase.initialize(
      ApplicationProvider.getApplicationContext(),
      FirebaseOptions.Builder()
        .setApplicationId(APP_ID)
        .setApiKey(API_KEY)
        .setProjectId("123")
        .build(),
      EXISTING_APP
    )
  }

  @After
  fun cleanUp() {
    FirebaseApp.clearInstancesForTest()
  }
}

@RunWith(RobolectricTestRunner::class)
class PerformanceTests : BaseTestCase() {

  @Mock lateinit var transportManagerMock: TransportManager

  @Mock lateinit var timerMock: Timer

  @Mock lateinit var mockTransportManager: TransportManager

  @Mock lateinit var mockClock: Clock

  @Mock lateinit var mockAppStateMonitor: AppStateMonitor

  @Captor lateinit var argMetricCaptor: ArgumentCaptor<NetworkRequestMetric>

  @Captor lateinit var argumentsCaptor: ArgumentCaptor<TraceMetric>

  var currentTime: Long = 1

  @Before
  override fun setUp() {
    super.setUp()
    initMocks(this)

    `when`(timerMock.getMicros()).thenReturn(1000L)
    `when`(timerMock.getDurationMicros()).thenReturn(2000L).thenReturn(3000L)
    doAnswer { Timer(currentTime) }.`when`(mockClock).getTime()
  }

  @Test
  fun `performance should delegate to FirebasePerformance#getInstance()`() {
    assertThat(Firebase.performance).isSameInstanceAs(FirebasePerformance.getInstance())
  }

  @Test
  fun `httpMetric wrapper test `() {
    val metric =
      HttpMetric("https://www.google.com/", HttpMethod.GET, transportManagerMock, timerMock)
    metric.trace { setHttpResponseCode(200) }

    verify(transportManagerMock)
      .log(
        argMetricCaptor.capture(),
        ArgumentMatchers.nullable(ApplicationProcessState::class.java)
      )

    val metricValue = argMetricCaptor.getValue()
    assertThat(metricValue.getHttpResponseCode()).isEqualTo(200)
  }

  @Test
  fun `trace wrapper test`() {
    val trace = Trace("trace_1", mockTransportManager, mockClock, mockAppStateMonitor)
    trace.trace { incrementMetric("metric_1", 5) }

    assertThat(getTraceCounter(trace)).hasSize(1)
    assertThat(getTraceCounterCount(trace, "metric_1")).isEqualTo(5)
    verify(mockTransportManager)
      .log(argumentsCaptor.capture(), nullable(ApplicationProcessState::class.java))
  }
}

@RunWith(RobolectricTestRunner::class)
class LibraryVersionTest : BaseTestCase() {
  @Test
  fun `library version should be registered with runtime`() {
    val publisher = Firebase.app.get(UserAgentPublisher::class.java)
  }
}
