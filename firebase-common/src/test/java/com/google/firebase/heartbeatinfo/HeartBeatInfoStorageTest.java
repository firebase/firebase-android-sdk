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
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HeartBeatInfoStorageTest {
  private final String testSdk = "testSdk";
  private final String GLOBAL = "fire-global";
  private static final int HEART_BEAT_COUNT_LIMIT = 30;
  private static Context applicationContext = ApplicationProvider.getApplicationContext();
  private static SharedPreferences sharedPreferences =
      applicationContext.getSharedPreferences("test", Context.MODE_PRIVATE);
  private static SharedPreferences heartBeatSharedPreferences =
      applicationContext.getSharedPreferences("testHeartBeat", Context.MODE_PRIVATE);
  private HeartBeatInfoStorage heartBeatInfoStorage =
      new HeartBeatInfoStorage(sharedPreferences, heartBeatSharedPreferences);

  @After
  public void tearDown() {
    sharedPreferences.edit().clear().apply();
    heartBeatSharedPreferences.edit().clear().apply();
  }

  @Test
  public void shouldSendSdkHeartBeat_answerIsNo() {
    sharedPreferences.edit().putLong(testSdk, 1).apply();
    assertThat(heartBeatInfoStorage.shouldSendSdkHeartBeat(testSdk, 1)).isFalse();
  }

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

  @Test
  public void storeTwoHeartbeat_storesProperly() {
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    heartBeatInfoStorage.storeHeartBeat(0, "test-agent");
    heartBeatInfoStorage.storeHeartBeat(86400001, "test-agent-1");
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(2);
    ArrayList<HeartBeatResult> results =
        (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(2);
    assertThat(results.get(0).getUserAgent()).isEqualTo("test-agent");
    assertThat(results.get(0).getUsedDates())
        .isEqualTo(new ArrayList<String>(Collections.singleton("1970-01-01")));
    assertThat(results.get(1).getUserAgent()).isEqualTo("test-agent-1");
    assertThat(results.get(1).getUsedDates())
        .isEqualTo(new ArrayList<String>(Collections.singleton("1970-01-02")));
    heartBeatInfoStorage.deleteAllHeartBeats();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(0);
  }

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
    assertThat(results.get(0).getUsedDates().size()).isEqualTo(HEART_BEAT_COUNT_LIMIT - 2);
    assertThat(results.get(0).getUsedDates()).doesNotContain("1970-01-01");
    assertThat(results.get(0).getUsedDates()).doesNotContain("1970-01-02");

    heartBeatInfoStorage.deleteAllHeartBeats();
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
    results = (ArrayList<HeartBeatResult>) heartBeatInfoStorage.getAllHeartBeats();
    assertThat(results.size()).isEqualTo(0);
  }

  @Test
  public void shouldSendSdkHeartBeat_answerIsYes() {
    long currentTime = System.currentTimeMillis();
    assertThat(heartBeatInfoStorage.shouldSendSdkHeartBeat(testSdk, 1)).isTrue();
    assertThat(sharedPreferences.getLong(testSdk, -1)).isEqualTo(1);
    assertThat(heartBeatInfoStorage.shouldSendSdkHeartBeat(testSdk, currentTime)).isTrue();
    assertThat(sharedPreferences.getLong(testSdk, -1)).isEqualTo(currentTime);
  }

  @Test
  public void shouldSendGlobalHeartBeat_answerIsNo() {
    sharedPreferences.edit().putLong(GLOBAL, 1).apply();
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(1)).isFalse();
  }

  @Test
  public void isSameDate_returnsCorrectly() {
    assertThat(HeartBeatInfoStorage.isSameDateUtc(0, 1000000000)).isTrue();
    assertThat(HeartBeatInfoStorage.isSameDateUtc(0, 0)).isFalse();
    assertThat(HeartBeatInfoStorage.isSameDateUtc(1000000000, 1000001000)).isFalse();
  }

  @Test
  public void shouldSendGlobalHeartBeat_answerIsYes() {
    long currentTime = System.currentTimeMillis();
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(1)).isTrue();
    assertThat(sharedPreferences.getLong(GLOBAL, -1)).isEqualTo(1);
    assertThat(heartBeatInfoStorage.shouldSendGlobalHeartBeat(currentTime)).isTrue();
    assertThat(sharedPreferences.getLong(GLOBAL, -1)).isEqualTo(currentTime);
  }
}
