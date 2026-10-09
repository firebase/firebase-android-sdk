/*
 * Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.telemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RecordClickTest {
  private lateinit var exporter: InMemoryLogRecordExporter
  private lateinit var telemetry: FirebaseCrashlyticsTelemetry

  @BeforeEach
  fun setUp() {
    exporter = InMemoryLogRecordExporter.create()
    telemetry = createTestTelemetry(inMemoryOpenTelemetry(exporter))
  }

  @Test
  fun recordClick_emitsWidgetClickEvent() {
    telemetry.recordClick("checkout_button")

    val record = exporter.finishedLogRecordItems.single()
    assertThat(record.eventName).isEqualTo("app.widget.click")
    assertThat(record.attributes.get(stringKey("app.widget.id"))).isEqualTo("checkout_button")
    assertThat(record.attributes.get(stringKey("app.widget.name"))).isEqualTo("checkout_button")
  }

  @Test
  fun recordClick_withoutActiveScreen_omitsScreenAttributes() {
    telemetry.recordClick("checkout_button")

    val attributes = exporter.finishedLogRecordItems.single().attributes
    assertThat(attributes.get(stringKey("app.screen.name"))).isNull()
    assertThat(attributes.get(stringKey("app.screen.id"))).isNull()
  }

  @Test
  fun recordClick_withActiveScreen_includesScreenAttributes() {
    telemetry.recordScreenAppear("Cart", "com.example.CartRoute")
    exporter.reset()

    telemetry.recordClick("checkout_button")

    val attributes = exporter.finishedLogRecordItems.single().attributes
    assertThat(attributes.get(stringKey("app.screen.name"))).isEqualTo("Cart")
    assertThat(attributes.get(stringKey("app.screen.id"))).isEqualTo("com.example.CartRoute")
  }
}
