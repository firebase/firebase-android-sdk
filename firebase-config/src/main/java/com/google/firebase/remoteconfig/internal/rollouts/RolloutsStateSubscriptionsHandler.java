package com.google.firebase.remoteconfig.internal.rollouts;

import androidx.annotation.NonNull;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsStateSubscriber;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

public class RolloutsStateSubscriptionsHandler {
  private ScheduledExecutorService executorService;

  public RolloutsStateSubscriptionsHandler(@NonNull ScheduledExecutorService executorService) {
    this.executorService = executorService;
  }

  // Thread-safe implementation for subscribers, using a ConcurrentHashMap as the underlying
  // implementation with set-like accessors.
  private Set<RolloutsStateSubscriber> subscribers =
      Collections.newSetFromMap(new ConcurrentHashMap<>());

  public void registerRolloutsStateSubscriber(@NonNull RolloutsStateSubscriber subscriber) {
    subscribers.add(subscriber);

    executorService.execute(() -> subscriber.onRolloutsStateChanged(getMockRolloutsState()));
  }

  public void publishActiveRolloutsState() {
    RolloutsState activeRolloutsState = getMockRolloutsState();

    for (RolloutsStateSubscriber subscriber : subscribers) {
      executorService.execute(() -> subscriber.onRolloutsStateChanged(activeRolloutsState));
    }
  }

  // TODO(Dana): Get the real rollouts state.
  private RolloutsState getMockRolloutsState() {
    RolloutAssignment assignment =
        RolloutAssignment.builder()
            .setRolloutId("rollout_1")
            .setVariantId("control")
            .setParameterKey("amazing_rollout")
            .setParameterValue("false")
            .build();

    Set<RolloutAssignment> assignments = new HashSet<>();
    assignments.add(assignment);

    return RolloutsState.create(assignments);
  }
}
