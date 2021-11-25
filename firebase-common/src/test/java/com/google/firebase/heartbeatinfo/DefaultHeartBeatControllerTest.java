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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.platforminfo.UserAgentPublisher;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultHeartBeatControllerTest {
  private ExecutorService executor;
  private TestOnCompleteListener<Void> storeOnCompleteListener;
  private TestOnCompleteListener<String> getOnCompleteListener;
  private final String DEFAULT_USER_AGENT = "agent1";
  private HeartBeatInfoStorage storage = mock(HeartBeatInfoStorage.class);
  private UserAgentPublisher publisher = mock(UserAgentPublisher.class);
  private final Set<HeartBeatConsumer> logSources =
      new HashSet<HeartBeatConsumer>() {
        {
          add(new HeartBeatConsumer() {});
        }
      };
  private DefaultHeartBeatController heartBeatController;

  @Before
  public void setUp() {
    executor = new ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    when(publisher.getUserAgent()).thenReturn(DEFAULT_USER_AGENT);
    storeOnCompleteListener = new TestOnCompleteListener<>();
    getOnCompleteListener = new TestOnCompleteListener<>();
    heartBeatController =
        new DefaultHeartBeatController(() -> storage, logSources, executor, () -> publisher);
  }

  @Test
  public void whenNoSource_dontStoreHeartBeat() throws ExecutionException, InterruptedException {
    DefaultHeartBeatController controller =
        new DefaultHeartBeatController(() -> storage, new HashSet<>(), executor, () -> publisher);
    controller.registerHeartBeat().addOnCompleteListener(executor, storeOnCompleteListener);
    storeOnCompleteListener.await();
    verify(storage, times(0)).storeHeartBeat(anyLong(), anyString());
  }

  @Test
  public void generateHeartBeat_oneHeartBeat()
      throws ExecutionException, InterruptedException, JSONException {
    ArrayList<HeartBeatResult> returnResults = new ArrayList<>();
    returnResults.add(
        HeartBeatResult.create(
            "test-agent", new ArrayList<String>(Collections.singleton("2015-02-03"))));
    when(storage.getAllHeartBeats()).thenReturn(returnResults);
    heartBeatController
        .registerHeartBeat()
        .addOnCompleteListener(executor, storeOnCompleteListener);
    storeOnCompleteListener.await();
    verify(storage, times(1)).storeHeartBeat(anyLong(), anyString());
    heartBeatController
        .getHeartBeatsHeader()
        .addOnCompleteListener(executor, getOnCompleteListener);
    String expected =
        Base64.getEncoder()
            .encodeToString(
                "{\"heartbeats\":[{\"date\":[\"2015-02-03\"],\"agent\":\"test-agent\"}],\"version\":\"2\"}"
                    .getBytes());
    assertThat(getOnCompleteListener.await()).isEqualTo(expected);
  }

  @Test
  public void generateHeartBeat_twoHeartBeatsSameUserAgent()
      throws ExecutionException, InterruptedException, JSONException {
    ArrayList<HeartBeatResult> returnResults = new ArrayList<>();
    ArrayList<String> dateList = new ArrayList<>();
    dateList.add("2015-03-02");
    dateList.add("2015-03-01");
    returnResults.add(HeartBeatResult.create("test-agent", dateList));
    when(storage.getAllHeartBeats()).thenReturn(returnResults);
    heartBeatController
        .registerHeartBeat()
        .addOnCompleteListener(executor, storeOnCompleteListener);
    storeOnCompleteListener.await();
    heartBeatController
        .registerHeartBeat()
        .addOnCompleteListener(executor, storeOnCompleteListener);
    storeOnCompleteListener.await();
    verify(storage, times(2)).storeHeartBeat(anyLong(), anyString());
    heartBeatController
        .getHeartBeatsHeader()
        .addOnCompleteListener(executor, getOnCompleteListener);
    String expected =
        Base64.getEncoder()
            .encodeToString(
                "{\"heartbeats\":[{\"date\":[\"2015-03-02\",\"2015-03-01\"],\"agent\":\"test-agent\"}],\"version\":\"2\"}"
                    .getBytes());
    assertThat(getOnCompleteListener.await()).isEqualTo(expected);
  }

  @Test
  public void generateHeartBeat_twoHeartBeatstwoUserAgents()
      throws ExecutionException, InterruptedException, JSONException {
    ArrayList<HeartBeatResult> returnResults = new ArrayList<>();
    returnResults.add(
        HeartBeatResult.create(
            "test-agent", new ArrayList<String>(Collections.singleton("2015-03-02"))));
    returnResults.add(
        HeartBeatResult.create(
            "test-agent-1", new ArrayList<String>(Collections.singleton("2015-03-03"))));
    when(storage.getAllHeartBeats()).thenReturn(returnResults);
    heartBeatController
        .registerHeartBeat()
        .addOnCompleteListener(executor, storeOnCompleteListener);
    storeOnCompleteListener.await();
    heartBeatController
        .registerHeartBeat()
        .addOnCompleteListener(executor, storeOnCompleteListener);
    storeOnCompleteListener.await();
    verify(storage, times(2)).storeHeartBeat(anyLong(), anyString());
    heartBeatController
        .getHeartBeatsHeader()
        .addOnCompleteListener(executor, getOnCompleteListener);
    String expected =
        Base64.getEncoder()
            .encodeToString(
                "{\"heartbeats\":[{\"date\":[\"2015-03-02\"],\"agent\":\"test-agent\"},{\"date\":[\"2015-03-03\"],\"agent\":\"test-agent-1\"}],\"version\":\"2\"}"
                    .getBytes());
    assertThat(getOnCompleteListener.await()).isEqualTo(expected);
  }
}
