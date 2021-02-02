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
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HeartBeatInfoStorageTest {
  private final String testSdk = "testSdk";
  private final String GLOBAL = "fire-global";
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
  public void getLastGlobalHeartBeat_returnsCorrectly() {
    sharedPreferences.edit().putLong(GLOBAL, 1).apply();
    assertThat(heartBeatInfoStorage.getLastGlobalHeartBeat()).isEqualTo(1);
  }

  @Test
  public void whenHeartBeatStorageExcess_cleanUpHeartBeat() {
    for (int i = 0; i < 202; i++) {
      heartBeatInfoStorage.storeHeartBeatInformation(testSdk, i + 1);
    }
    // HeartBeat size went to 201 halved and then 1 more was added.
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(101);
    assertThat(heartBeatSharedPreferences.getAll().entrySet().size()).isEqualTo(101);
  }

  @Test
  public void isSameDate_returnsCorrectly() {
    assertThat(HeartBeatInfoStorage.isSameDateUtc(0, 1000000000)).isTrue();
    assertThat(HeartBeatInfoStorage.isSameDateUtc(0, 0)).isFalse();
    assertThat(HeartBeatInfoStorage.isSameDateUtc(1000000000, 1000001000)).isFalse();
  }

  @Test
  public void storeHeartBeatInformation_storesProperly() {
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 200);
    assertThat(heartBeatSharedPreferences.getString("200", "-1")).isEqualTo(testSdk);
  }

  @Test
  public void getStoredHeartBeat_returnsThreeStoredHeartBeats_noClear() {
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 200);
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 198);
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 199);
    List<SdkHeartBeatResult> result = heartBeatInfoStorage.getStoredHeartBeats(false);
    assertThat(result.size()).isEqualTo(3);
    assertThat(result.get(0)).isEqualTo(SdkHeartBeatResult.create(testSdk, 198));
    assertThat(result.get(1)).isEqualTo(SdkHeartBeatResult.create(testSdk, 199));
    assertThat(result.get(2)).isEqualTo(SdkHeartBeatResult.create(testSdk, 200));
    result = heartBeatInfoStorage.getStoredHeartBeats(false);
    assertThat(result.size()).isEqualTo(3);
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(3);
  }

  @Test
  public void getStoredHeartBeat_returnsThreeStoredHeartBeats_withClear() {
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 200);
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 198);
    heartBeatInfoStorage.storeHeartBeatInformation(testSdk, 199);
    List<SdkHeartBeatResult> result = heartBeatInfoStorage.getStoredHeartBeats(true);
    assertThat(result.size()).isEqualTo(3);
    assertThat(result.get(0)).isEqualTo(SdkHeartBeatResult.create(testSdk, 198));
    assertThat(result.get(1)).isEqualTo(SdkHeartBeatResult.create(testSdk, 199));
    assertThat(result.get(2)).isEqualTo(SdkHeartBeatResult.create(testSdk, 200));
    result = heartBeatInfoStorage.getStoredHeartBeats(false);
    assertThat(result.size()).isEqualTo(0);
    assertThat(heartBeatInfoStorage.getHeartBeatCount()).isEqualTo(0);
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
