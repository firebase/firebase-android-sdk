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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.messaging.TopicOperation.OPERATION_PAIR_DIVIDER;
import static com.google.firebase.messaging.TopicOperation.TopicOperations.OPERATION_SUBSCRIBE;
import static com.google.firebase.messaging.TopicOperation.TopicOperations.OPERATION_UNSUBSCRIBE;

import androidx.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TopicOperationRoboTest {
  private static final String TEST_TOPIC = "Test_Topic";

  @Test
  public void testsubscribe() {
    TopicOperation subscribeOperation = TopicOperation.subscribe(TEST_TOPIC);
    assertThat(subscribeOperation.getOperation()).isEqualTo(OPERATION_SUBSCRIBE);
    assertThat(subscribeOperation.getTopic()).isEqualTo(TEST_TOPIC);
  }

  @Test
  public void testUnsubscribe() {
    TopicOperation subscribeOperation = TopicOperation.unsubscribe(TEST_TOPIC);
    assertThat(subscribeOperation.getOperation()).isEqualTo(OPERATION_UNSUBSCRIBE);
    assertThat(subscribeOperation.getTopic()).isEqualTo(TEST_TOPIC);
  }

  @Test
  public void testSerializeSubscribe() {
    TopicOperation topicOperation = TopicOperation.subscribe(TEST_TOPIC);
    assertThat(topicOperation.serialize())
        .isEqualTo(OPERATION_SUBSCRIBE + OPERATION_PAIR_DIVIDER + TEST_TOPIC);
  }

  @Test
  public void testSerializeUnsubscribe() {
    TopicOperation topicOperation = TopicOperation.unsubscribe(TEST_TOPIC);
    assertThat(topicOperation.serialize())
        .isEqualTo(OPERATION_UNSUBSCRIBE + OPERATION_PAIR_DIVIDER + TEST_TOPIC);
  }

  @Test
  public void testFromSubscribe() {
    TopicOperation topicOperation = TopicOperation.subscribe(TEST_TOPIC);
    assertThat(TopicOperation.from(topicOperation.serialize())).isEqualTo(topicOperation);
  }

  @Test
  public void testFromUnsubscribe() {
    TopicOperation topicOperation = TopicOperation.unsubscribe(TEST_TOPIC);
    assertThat(TopicOperation.from(topicOperation.serialize())).isEqualTo(topicOperation);
  }
}
