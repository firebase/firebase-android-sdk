/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig.internal.rollouts;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsStateSubscriber;
import java.util.concurrent.Executor;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RolloutsStateSubscriptionsHandlerTest {
  @Mock ConfigCacheClient mockActivatedConfigsCache;
  @Mock RolloutsStateFactory mockRolloutsStateFactory;
  @Mock RolloutsStateSubscriber mockRolloutsStateSubscriber;

  private Executor executor;
  private RolloutsStateSubscriptionsHandler handler;

  private static ConfigContainer configWithoutRollouts;
  private static final RolloutsState rolloutsStateWithoutAssignments =
      RolloutsState.create(ImmutableSet.of());

  private static ConfigContainer configWithRollouts;
  private static final RolloutAssignment rolloutAssignment =
      RolloutAssignment.builder()
          .setRolloutId("rollout_1")
          .setVariantId("control")
          .setParameterKey("my_feature")
          .setParameterValue("true")
          .setTemplateVersion(1L)
          .build();
  private static final RolloutsState rolloutsState =
      RolloutsState.create(ImmutableSet.of(rolloutAssignment));

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    executor = MoreExecutors.directExecutor();

    configWithoutRollouts = ConfigContainer.newBuilder().build();

    configWithRollouts =
        ConfigContainer.newBuilder()
            .withRolloutMetadata(
                new JSONArray(
                    "["
                        + "{"
                        + "\"rollout_id\": \"rollout_1\","
                        + "\"variant_id\": \"control\","
                        + "\"affected_parameter_keys\": [\"my_feature\"]"
                        + "}]"))
            .build();

    handler =
        new RolloutsStateSubscriptionsHandler(
            mockActivatedConfigsCache, mockRolloutsStateFactory, executor);
  }

  @Test
  public void registerRolloutsStateSubscriber_noActiveRolloutsState_callsSubscriber()
      throws Exception {
    when(mockActivatedConfigsCache.get()).thenReturn(Tasks.forResult(configWithoutRollouts));
    when(mockRolloutsStateFactory.getActiveRolloutsState(any()))
        .thenReturn(rolloutsStateWithoutAssignments);

    handler.registerRolloutsStateSubscriber(mockRolloutsStateSubscriber);

    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsStateWithoutAssignments);
  }

  @Test
  public void registerRolloutsStateSubscriber_activeRollouts_callsSubscriber() throws Exception {
    when(mockActivatedConfigsCache.get()).thenReturn(Tasks.forResult(configWithRollouts));
    when(mockRolloutsStateFactory.getActiveRolloutsState(any())).thenReturn(rolloutsState);

    handler.registerRolloutsStateSubscriber(mockRolloutsStateSubscriber);

    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsState);
  }

  @Test
  public void registerRolloutsStateSubscriber_exceptionGettingState_doesNotThrow()
      throws Exception {
    when(mockActivatedConfigsCache.get()).thenReturn(Tasks.forResult(configWithRollouts));
    when(mockRolloutsStateFactory.getActiveRolloutsState(any()))
        .thenThrow(new FirebaseRemoteConfigClientException("Exception getting rollouts state."));

    handler.registerRolloutsStateSubscriber(mockRolloutsStateSubscriber);

    // Exception isn't thrown, and the subscriber won't be called.
    verifyNoInteractions(mockRolloutsStateSubscriber);
  }

  @Test
  public void publishActiveRolloutsState_assignmentsAdded_callsSubscribersWithAssignments()
      throws Exception {
    when(mockActivatedConfigsCache.get()).thenReturn(Tasks.forResult(configWithoutRollouts));
    when(mockRolloutsStateFactory.getActiveRolloutsState(any()))
        .thenReturn(rolloutsStateWithoutAssignments)
        .thenReturn(rolloutsState);

    // When the subscriber is added, it should be called without rollout assignments.
    handler.registerRolloutsStateSubscriber(mockRolloutsStateSubscriber);
    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsStateWithoutAssignments);

    // Then some rollouts are assigned.
    handler.publishActiveRolloutsState(configWithoutRollouts);
    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsState);
  }

  @Test
  public void publishActiveRolloutsState_assignmentsRemoved_callsSubscribersWithoutAssignments()
      throws Exception {
    when(mockActivatedConfigsCache.get()).thenReturn(Tasks.forResult(configWithoutRollouts));
    when(mockRolloutsStateFactory.getActiveRolloutsState(any()))
        .thenReturn(rolloutsState)
        .thenReturn(rolloutsStateWithoutAssignments);

    // When the subscriber is added, it should be called with the assigned rollouts.
    handler.registerRolloutsStateSubscriber(mockRolloutsStateSubscriber);
    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsState);

    // Then assignments are removed.
    handler.publishActiveRolloutsState(configWithoutRollouts);
    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsStateWithoutAssignments);
  }

  @Test
  public void publishActiveRolloutsState_exceptionGettingState_doesNotThrow() throws Exception {
    when(mockActivatedConfigsCache.get()).thenReturn(Tasks.forResult(configWithoutRollouts));
    when(mockRolloutsStateFactory.getActiveRolloutsState(any()))
        .thenReturn(rolloutsState)
        .thenThrow(new FirebaseRemoteConfigClientException("Exception getting rollouts state."));

    // When the subscriber is added, it should be called with the assigned rollouts.
    handler.registerRolloutsStateSubscriber(mockRolloutsStateSubscriber);
    verify(mockRolloutsStateSubscriber).onRolloutsStateChanged(rolloutsState);

    // Then assignments are removed.
    // Exception isn't thrown, and the subscriber won't be called.
    handler.publishActiveRolloutsState(configWithoutRollouts);
    verifyNoMoreInteractions(mockRolloutsStateSubscriber);
  }
}
