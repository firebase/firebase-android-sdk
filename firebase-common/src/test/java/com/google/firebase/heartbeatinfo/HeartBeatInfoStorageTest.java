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

package com.google.firebase.heartbeatinfo;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
public class HeartBeatInfoStorageTest {
  private final String testSdk = "testSdk";
  private final String GLOBAL = "fire-global";
  private static final int HEART_BEAT_COUNT_LIMIT = 30;
  private static Context applicationContext = ApplicationProvider.getApplicationContext();
  private static SharedPreferences heartBeatSharedPreferences =
      applicationContext.getSharedPreferences("testHeartBeat", Context.MODE_PRIVATE);
  private HeartBeatInfoStorage heartBeatInfoStorage =
      new HeartBeatInfoStorage(heartBeatSharedPreferences);

  @Before
  public void setUp() {
    heartBeatSharedPreferences.edit().clear().apply();
  }

  @After
  public void tearDown() {
    heartBeatSharedPreferences.edit().clear().apply();
  }

  @Config(sdk = 29)
  @Test
  public void storeOneHeartbeat_storesProperly() {
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    heartBeatInfoStorage.storeHeartBeat(0, "test-agent");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(1);
    ArrayList<HeartBeatResult> results =
        (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).getUserAgent()).isEqualTo("test-agent");
    assertThat(results.get(0).getUsedDates())
        .isEqualTo(new ArrayList<String>(Collections.singleton("1970-01-01")));
    heartBeatInfoStorage.storeHeartBeat(10, "test-agent");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(1);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(1);
    heartBeatInfoStorage.storeHeartBeat(100, "test-agent-1");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(1);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(1);
    heartBeatInfoStorage.deleteAllHeartBeats();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(0);
  }

  @Config(sdk = 29)
  @Test
  public void storeOneHeartbeat_updatesProperly() {
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    heartBeatInfoStorage.storeHeartBeat(0, "test-agent");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(1);
    heartBeatInfoStorage.storeHeartBeat(10, "test-agent-1");
    ArrayList<HeartBeatResult> results =
        (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).getUserAgent()).isEqualTo("test-agent-1");
    assertThat(results.get(0).getUsedDates())
        .isEqualTo(new ArrayList<String>(Collections.singleton("1970-01-01")));
    heartBeatInfoStorage.deleteAllHeartBeats();
    heartBeatInfoStorage.storeHeartBeat(100, "test-agent-2");
    heartBeatInfoStorage.storeHeartBeat(1000, "test-agent-1");
    // Since the heartbeat is already sent for today no new heartbeats should be sent.
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(0);
  }

  @Config(sdk = 29)
  @Test
  public void storeTwoHeartbeat_storesProperly() {
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    heartBeatInfoStorage.storeHeartBeat(0, "test-agent");
    heartBeatInfoStorage.storeHeartBeat(86400001, "test-agent-1");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(2);
    ArrayList<HeartBeatResult> results =
        (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(2);
    int userAgentTest = 0, userAgentTest1 = 1;
    if (results.get(0).getUserAgent().contains("-1")) {
      userAgentTest = 1;
      userAgentTest1 = 0;
    }
    assertThat(results.get(userAgentTest).getUserAgent()).isEqualTo("test-agent");
    assertThat(results.get(userAgentTest).getUsedDates())
        .isEqualTo(new ArrayList<String>(Collections.singleton("1970-01-01")));
    assertThat(results.get(userAgentTest1).getUserAgent()).isEqualTo("test-agent-1");
    assertThat(results.get(userAgentTest1).getUsedDates())
        .isEqualTo(new ArrayList<String>(Collections.singleton("1970-01-02")));
    heartBeatInfoStorage.deleteAllHeartBeats();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(0);
  }

  @Config(sdk = 29)
  @Test
  public void storeExcessHeartBeats_cleanUpProperly() {
    for (int i = 0; i < HEART_BEAT_COUNT_LIMIT - 1; i++) {
      heartBeatInfoStorage.storeHeartBeat(i * (86400001L), "test-agent");
    }
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(HEART_BEAT_COUNT_LIMIT - 1);
    ArrayList<HeartBeatResult> results =
        (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).getUsedDates().size()).isEqualTo(HEART_BEAT_COUNT_LIMIT - 1);
    assertThat(results.get(0).getUsedDates()).contains("1970-01-01");
    assertThat(results.get(0).getUsedDates()).contains("1970-01-02");

    heartBeatInfoStorage.storeHeartBeat((HEART_BEAT_COUNT_LIMIT - 1) * (86400001L), "test-agent");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(HEART_BEAT_COUNT_LIMIT - 1);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(1);
    assertThat(results.get(0).getUsedDates().size()).isEqualTo(HEART_BEAT_COUNT_LIMIT - 1);
    assertThat(results.get(0).getUsedDates()).doesNotContain("1970-01-01");
    assertThat(results.get(0).getUsedDates()).contains("1970-01-02");

    heartBeatInfoStorage.storeHeartBeat((HEART_BEAT_COUNT_LIMIT) * (86400001L), "test-agent-1");
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(2);
    int testAgentIndex = 0;
    if (results.get(1).getUserAgent().equals("test-agent")) {
      testAgentIndex = 1;
    }
    assertThat(results.get(testAgentIndex).getUsedDates().size())
        .isEqualTo(HEART_BEAT_COUNT_LIMIT - 2);
    assertThat(results.get(testAgentIndex).getUsedDates()).doesNotContain("1970-01-01");
    assertThat(results.get(testAgentIndex).getUsedDates()).doesNotContain("1970-01-02");

    heartBeatInfoStorage.deleteAllHeartBeats();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(0);
  }

  @Test
  public void shouldSendSdkHeartBeat_answerIsYes() {
    long currentTime = System.currentTimeMillis();
    assertThat(heartBeatInfoStorage.shouldSendSdkHeartBeat(testSdk, 1)).isTrue();
    assertThat(heartBeatSharedPreferences.getLong(testSdk, -1)).isEqualTo(1);
    assertThat(heartBeatInfoStorage.shouldSendSdkHeartBeat(testSdk, currentTime)).isTrue();
    assertThat(heartBeatSharedPreferences.getLong(testSdk, -1)).isEqualTo(currentTime);
  }

  @Test
  public void shouldSendGlobalHeartBeat_answerIsNo() {
    heartBeatSharedPreferences.edit().putLong(GLOBAL, 1).apply();
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(1)).isFalse();
  }

  @Test
  public void currentDayHeartbeatNotSent_updatesCorrectly() {
    long millis = System.currentTimeMillis();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    heartBeatInfoStorage.storeHeartBeat(millis, "test-agent");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(1);
    assertThat(heartBeatInfoStorage.getAllHeartBeats().size()).isEqualTo(0);
    heartBeatInfoStorage.deleteAllHeartBeats();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(1);
    assertThat(heartBeatSharedPreferences.getStringSet("test-agent", new HashSet<>())).isNotEmpty();
    heartBeatInfoStorage.storeHeartBeat(millis, "test-agent-1");
    assertThat(heartBeatSharedPreferences.getStringSet("test-agent", new HashSet<>())).isEmpty();
    assertThat(heartBeatSharedPreferences.getStringSet("test-agent-1", new HashSet<>()))
        .isNotEmpty();
  }

  @Test
  public void postHeartBeatCleanUp_worksCorrectly() {
    long millis = System.currentTimeMillis();
    // Store using new method
    heartBeatInfoStorage.storeHeartBeat(millis, "test-agent");
    // Get global heartbeat using old method
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(millis)).isTrue();
    assertThat(heartBeatInfoStorage.getAllHeartBeats().size()).isEqualTo(0);
    heartBeatInfoStorage.postHeartBeatCleanUp();
    assertThat(heartBeatInfoStorage.getAllHeartBeats().size()).isEqualTo(0);
    // Try storing using new method again.
    heartBeatInfoStorage.storeHeartBeat(millis, "test-agent-1");
    assertThat(heartBeatInfoStorage.getAllHeartBeats().size()).isEqualTo(0);
  }

  @Test
  public void isSameDate_returnsCorrectly() {
    assertThat(heartBeatInfoStorage.isSameDateUtc(0, 1000000000)).isFalse();
    assertThat(heartBeatInfoStorage.isSameDateUtc(0, 0)).isTrue();
    assertThat(heartBeatInfoStorage.isSameDateUtc(1000000000, 1000001000)).isTrue();
  }

  @Test
  public void shouldSendGlobalHeartBeat_answerIsYes() {
    long currentTime = System.currentTimeMillis();
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(1)).isTrue();
    assertThat(heartBeatSharedPreferences.getLong(GLOBAL, -1)).isEqualTo(1);
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(currentTime)).isTrue();
    assertThat(heartBeatSharedPreferences.getLong(GLOBAL, -1)).isEqualTo(currentTime);
  }
}
