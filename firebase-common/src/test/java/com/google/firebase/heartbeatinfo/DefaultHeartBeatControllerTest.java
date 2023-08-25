// Copyright 2021 Google LLC
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
import static com.google.firebase.heartbeatinfo.TaskWaiter.await;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableSet;
import com.google.firebase.platforminfo.UserAgentPublisher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

@RunWith(AndroidJUnit4.class)
public class DefaultHeartBeatControllerTest {
  private static final String DEFAULT_USER_AGENT = "agent1";
  private final Executor executor = Executors.newSingleThreadExecutor();

  private final HeartBeatInfoStorage storage = mock(HeartBeatInfoStorage.class);
  private final UserAgentPublisher publisher = mock(UserAgentPublisher.class);
  private final Context applicationContext = ApplicationProvider.getApplicationContext();
  private final Set<HeartBeatConsumer> logSources = ImmutableSet.of(new HeartBeatConsumer() {});
  private DefaultHeartBeatController heartBeatController;

  @Before
  public void setUp() {
    when(publisher.getUserAgent()).thenReturn(DEFAULT_USER_AGENT);
    heartBeatController =
        new DefaultHeartBeatController(
            () -> storage, logSources, executor, () -> publisher, applicationContext);
  }

  @Test
  public void whenNoSource_dontStoreHeartBeat() throws InterruptedException, TimeoutException {
    DefaultHeartBeatController controller =
        new DefaultHeartBeatController(
            () -> storage, new HashSet<>(), executor, () -> publisher, applicationContext);
    await(controller.registerHeartBeat());
    verify(storage, times(0)).storeHeartBeat(anyLong(), anyString());
  }

  @Test
  public void getHeartBeatCode_globalHeartBeat() {
    when(storage.shouldSendGlobalHeartBeat(anyLong())).thenReturn(Boolean.TRUE);
    assertThat(heartBeatController.getHeartBeatCode("fire-iid").getCode()).isEqualTo(2);
  }

  @Test
  public void getHeartBeatCode_noHeartBeat() {
    when(storage.shouldSendGlobalHeartBeat(anyLong())).thenReturn(Boolean.FALSE);
    assertThat(heartBeatController.getHeartBeatCode("fire-iid").getCode()).isEqualTo(0);
  }

  @Config(sdk = 29)
  @Test
  public void generateHeartBeat_oneHeartBeat() throws InterruptedException, TimeoutException {
    ArrayList<HeartBeatResult> returnResults = new ArrayList<>();
    returnResults.add(
        HeartBeatResult.create("test-agent", Collections.singletonList("2015-02-03")));
    when(storage.getAllHeartBeats()).thenReturn(returnResults);
    await(heartBeatController.registerHeartBeat());
    verify(storage, times(1)).storeHeartBeat(anyLong(), anyString());
    String str =
        "{\"heartbeats\":[{\"agent\":\"test-agent\",\"dates\":[\"2015-02-03\"]}],\"version\":\"2\"}";
    String expected = compress(str);
    assertThat(await(heartBeatController.getHeartBeatsHeader()).replace("\n", ""))
        .isEqualTo(expected);
  }

  @Config(sdk = 29)
  @Test
  public void firstNewThenOld_synchronizedCorrectly()
      throws InterruptedException, TimeoutException {
    Context context = ApplicationProvider.getApplicationContext();
    SharedPreferences heartBeatSharedPreferences =
        context.getSharedPreferences("testHeartBeat", Context.MODE_PRIVATE);
    HeartBeatInfoStorage heartBeatInfoStorage =
        new HeartBeatInfoStorage(heartBeatSharedPreferences);
    DefaultHeartBeatController controller =
        new DefaultHeartBeatController(
            () -> heartBeatInfoStorage, logSources, executor, () -> publisher, context);
    String emptyString =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("{\"heartbeats\":[],\"version\":\"2\"}".getBytes());
    await(controller.registerHeartBeat());
    String output = await(controller.getHeartBeatsHeader());
    assertThat(output.replace("\n", "")).isNotEqualTo(emptyString);
    int heartBeatCode = controller.getHeartBeatCode("test").getCode();
    assertThat(heartBeatCode).isEqualTo(0);
  }

  @Config(sdk = 29)
  @Test
  public void firstOldThenNew_synchronizedCorrectly()
      throws InterruptedException, TimeoutException {
    Context context = ApplicationProvider.getApplicationContext();
    SharedPreferences heartBeatSharedPreferences =
        context.getSharedPreferences("testHeartBeat", Context.MODE_PRIVATE);
    HeartBeatInfoStorage heartBeatInfoStorage =
        new HeartBeatInfoStorage(heartBeatSharedPreferences);
    DefaultHeartBeatController controller =
        new DefaultHeartBeatController(
            () -> heartBeatInfoStorage, logSources, executor, () -> publisher, context);
    String emptyString = compress("{\"heartbeats\":[],\"version\":\"2\"}");
    await(controller.registerHeartBeat());
    int heartBeatCode = controller.getHeartBeatCode("test").getCode();
    assertThat(heartBeatCode).isEqualTo(2);
    String output = await(controller.getHeartBeatsHeader());
    assertThat(output.replace("\n", "")).isEqualTo(emptyString);

    await(controller.registerHeartBeat());
    await(controller.getHeartBeatsHeader());
    assertThat(output.replace("\n", "")).isEqualTo(emptyString);
  }

  @Config(sdk = 29)
  @Test
  public void generateHeartBeat_twoHeartBeatsSameUserAgent()
      throws InterruptedException, TimeoutException {
    ArrayList<HeartBeatResult> returnResults = new ArrayList<>();
    ArrayList<String> dateList = new ArrayList<>();
    dateList.add("2015-03-02");
    dateList.add("2015-03-01");
    returnResults.add(HeartBeatResult.create("test-agent", dateList));
    when(storage.getAllHeartBeats()).thenReturn(returnResults);
    await(heartBeatController.registerHeartBeat());
    await(heartBeatController.registerHeartBeat());
    verify(storage, times(2)).storeHeartBeat(anyLong(), anyString());

    String str =
        "{\"heartbeats\":[{\"agent\":\"test-agent\",\"dates\":[\"2015-03-02\",\"2015-03-01\"]}],\"version\":\"2\"}";
    String expected = compress(str);
    assertThat(await(heartBeatController.getHeartBeatsHeader()).replace("\n", ""))
        .isEqualTo(expected);
  }

  private static String base64Encode(byte[] input) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
  }

  private static byte[] gzip(String input) {
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    try {
      try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteOutputStream)) {
        gzipOutputStream.write(input.getBytes(UTF_8));
      }
      byte[] gzipped = byteOutputStream.toByteArray();
      byteOutputStream.close();
      return gzipped;
    } catch (IOException e) {
      return null;
    }
  }

  private String compress(String str) {
    return base64Encode(gzip(str));
  }

  @Config(sdk = 29)
  @Test
  public void generateHeartBeat_twoHeartBeatstwoUserAgents()
      throws InterruptedException, TimeoutException {
    ArrayList<HeartBeatResult> returnResults = new ArrayList<>();
    returnResults.add(
        HeartBeatResult.create("test-agent", Collections.singletonList("2015-03-02")));
    returnResults.add(
        HeartBeatResult.create("test-agent-1", Collections.singletonList("2015-03-03")));
    when(storage.getAllHeartBeats()).thenReturn(returnResults);
    await(heartBeatController.registerHeartBeat());
    await(heartBeatController.registerHeartBeat());

    verify(storage, times(2)).storeHeartBeat(anyLong(), anyString());
    String str =
        "{\"heartbeats\":[{\"agent\":\"test-agent\",\"dates\":[\"2015-03-02\"]},{\"agent\":\"test-agent-1\",\"dates\":[\"2015-03-03\"]}],\"version\":\"2\"}";
    String expected = compress(str);
    assertThat(await(heartBeatController.getHeartBeatsHeader()).replace("\n", ""))
        .isEqualTo(expected);
  }
}
