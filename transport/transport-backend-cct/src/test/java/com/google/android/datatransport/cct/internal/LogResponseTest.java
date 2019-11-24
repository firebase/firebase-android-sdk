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

package com.google.android.datatransport.cct.internal;

import static com.google.common.truth.Truth.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class LogResponseTest {

  @Test
  public void testLogRequestParsing_null() {
    assertThat(LogResponse.fromJson(null)).isNull();
  }

  @Test
  public void testLogRequestParsing_emptyJson() {
    assertThat(LogResponse.fromJson("")).isNull();
  }

  @Test
  public void testLogRequestParsing_onlyAwaitMillis() {
    String jsonInput = "{\"next_request_wait_millis\":1000}";
    assertThat(LogResponse.fromJson(jsonInput).getNextRequestAwaitMillis()).isEqualTo(1000);
  }

  @Test
  public void testLogRequestParsing_onlyFingerprint() {
    LogResponse expected =
        LogResponse.builder()
            .setNextRequestAwaitMillis(3000)
            .setQosTiersOverride(
                QosTiersOverride.builder().setQosTierFingerprint(123123123L).build())
            .build();
    String jsonInput =
        "{"
            + "\"next_request_wait_millis\": 3000,"
            + "\"qos_tier\": {"
            + "  \"qos_tier_fingerprint\": 123123123"
            + "}"
            + "}";

    assertThat(LogResponse.fromJson(jsonInput)).isEqualTo(expected);
  }

  @Test
  public void testLogRequestParsing_includeSingleConfiguration() {
    List<QosTierConfiguration> configurations = new ArrayList<>();
    configurations.add(QosTierConfiguration.builder().setLogSourceName("CustomSourceName").build());
    String jsonInput =
        "{"
            + "\"next_request_wait_millis\": 3000,"
            + "\"qos_tier\": {"
            + "  \"qos_tier_fingerprint\": 123123123,"
            + "  \"qos_tier_configuration\": ["
            + "    {"
            + "      \"log_source_name\": \"CustomSourceName\""
            + "    }"
            + "  ]"
            + "}"
            + "}";

    assertThat(LogResponse.fromJson(jsonInput).getQosTiersOverride().getQosTierConfigurations())
        .containsExactlyElementsIn(configurations);
  }

  @Test
  public void testLogRequestParsing_multipleSingleConfiguration() {
    List<QosTierConfiguration> configurations = new ArrayList<>();
    configurations.add(
        QosTierConfiguration.builder()
            .setLogSource(12)
            .setQosTier(QosTierConfiguration.QosTier.UNMETERED_ONLY)
            .build());
    configurations.add(
        QosTierConfiguration.builder()
            .setLogSourceName("name")
            .setQosTier(QosTierConfiguration.QosTier.UNMETERED_OR_DAILY)
            .build());
    String jsonInput =
        "{"
            + "\"next_request_wait_millis\": 3000,"
            + "\"qos_tier\": {"
            + "  \"qos_tier_fingerprint\": 123123123,"
            + "  \"qos_tier_configuration\": ["
            + "    {"
            + "      \"log_source\": 12,"
            + "      \"qos_tier\": \"UNMETERED_ONLY\""
            + "    },"
            + "    {"
            + "      \"log_source_name\": \"name\","
            + "      \"qos_tier\": \"UNMETERED_OR_DAILY\""
            + "    }"
            + "  ]"
            + "}"
            + "}";

    assertThat(LogResponse.fromJson(jsonInput).getQosTiersOverride().getQosTierConfigurations())
        .containsExactlyElementsIn(configurations);
  }

  // ----
  // Builder tests: ensure that valid partial objects can be built.
  // ----
  @Test
  public void testQosTierConfiguration_logSource() {
    assertThat(
            QosTierConfiguration.builder()
                .setLogSource(0)
                .setQosTier(QosTierConfiguration.QosTier.NEVER)
                .build())
        .isInstanceOf(QosTierConfiguration.class);
  }

  @Test
  public void testQosTierConfiguration_logSourceName() {
    assertThat(
            QosTierConfiguration.builder()
                .setLogSourceName("SourceName")
                .setQosTier(QosTierConfiguration.QosTier.NEVER)
                .build())
        .isInstanceOf(QosTierConfiguration.class);
  }

  @Test
  public void testQosTierConfiguration_noTier() {
    assertThat(QosTierConfiguration.builder().setLogSourceName("SourceName").build())
        .isInstanceOf(QosTierConfiguration.class);
  }

  @Test
  public void testQosTierOverride_emptyConfigurations() {
    assertThat(
            QosTiersOverride.builder()
                .setQosTierFingerprint(12345)
                .setQosTierConfigurations(Collections.emptyList())
                .build())
        .isInstanceOf(QosTiersOverride.class);
  }

  @Test
  public void testQosTierOverride_noConfigurations() {
    assertThat(QosTiersOverride.builder().setQosTierFingerprint(12345).build())
        .isInstanceOf(QosTiersOverride.class);
  }

  @Test
  public void testLogResponseFull() {
    List<QosTierConfiguration> configurations = new ArrayList<>();
    configurations.add(
        QosTierConfiguration.builder()
            .setLogSource(0)
            .setQosTier(QosTierConfiguration.QosTier.NEVER)
            .build());
    configurations.add(
        QosTierConfiguration.builder()
            .setLogSourceName("AnotherSourceNames")
            .setQosTier(QosTierConfiguration.QosTier.FAST_IF_RADIO_AWAKE)
            .build());

    assertThat(
            LogResponse.builder()
                .setNextRequestAwaitMillis(3000)
                .setQosTiersOverride(
                    QosTiersOverride.builder()
                        .setQosTierFingerprint(1234567890)
                        .setQosTierConfigurations(configurations)
                        .build())
                .build())
        .isInstanceOf(LogResponse.class);
  }

  @Test
  public void testLogResponse_noQosTierConfigurations() {
    assertThat(LogResponse.builder().setNextRequestAwaitMillis(1000).build())
        .isInstanceOf(LogResponse.class);
  }
}
