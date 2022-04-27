// Copyright 2018 Google LLC
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

package com.google.firebase.database.core;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.core.view.QuerySpec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * {@link ZombieEventManager} records event registrations made from Query so that when they are
 * unregistered, we immediately "zombie" them so that all further events are suppressed. This stops
 * events that would normally be fired if events were already queued in the {@link Repo} or even in
 * the Android message queue.
 */
public class ZombieEventManager implements EventRegistrationZombieListener {
  // This hashmap stores the original eventregistrations sent to the repo.
  // These are the registration instances that will get called with update events.
  // Since EventRegistration overrides equals and hashcode, we create temporary instances
  // to use as lookup keys.
  // Package private for testing purposes only
  final HashMap<EventRegistration, List<EventRegistration>> globalEventRegistrations =
      new HashMap<EventRegistration, List<EventRegistration>>();

  private static ZombieEventManager defaultInstance = new ZombieEventManager();

  private ZombieEventManager() {}

  @NotNull
  public static ZombieEventManager getInstance() {
    return defaultInstance;
  }

  public void recordEventRegistration(EventRegistration registration) {
    synchronized (globalEventRegistrations) {
      List<EventRegistration> registrationList = globalEventRegistrations.get(registration);
      if (registrationList == null) {
        registrationList = new ArrayList<EventRegistration>();
        globalEventRegistrations.put(registration, registrationList);
      }
      registrationList.add(registration);
      // We record non default listeners twice, because when the default listener is zombied
      // (removed) the repo will remove that listener on all specific queries as well.
      // We need to match that behavior here and zombie all the relevant registrations.
      if (!registration.getQuerySpec().isDefault()) {
        EventRegistration defaultRegistration =
            registration.clone(QuerySpec.defaultQueryAtPath(registration.getQuerySpec().getPath()));
        registrationList = globalEventRegistrations.get(defaultRegistration);
        if (registrationList == null) {
          registrationList = new ArrayList<EventRegistration>();
          globalEventRegistrations.put(defaultRegistration, registrationList);
        }
        registrationList.add(registration);
      }

      registration.setIsUserInitiated(true);
      registration.setOnZombied(this);
    }
  }

  private void unRecordEventRegistration(EventRegistration zombiedRegistration) {
    synchronized (globalEventRegistrations) {
      boolean found = false;

      List<EventRegistration> registrationList = globalEventRegistrations.get(zombiedRegistration);
      if (registrationList != null) {
        for (int i = 0; i < registrationList.size(); i++) {
          if (registrationList.get(i) == zombiedRegistration) {
            found = true;
            registrationList.remove(i);
            break;
          }
        }
        if (registrationList.isEmpty()) {
          globalEventRegistrations.remove(zombiedRegistration);
        }
      }
      hardAssert(found || !zombiedRegistration.isUserInitiated());

      // If the registration was recorded twice, we need to remove its second
      // record.
      if (!zombiedRegistration.getQuerySpec().isDefault()) {
        EventRegistration defaultRegistration =
            zombiedRegistration.clone(
                QuerySpec.defaultQueryAtPath(zombiedRegistration.getQuerySpec().getPath()));

        registrationList = globalEventRegistrations.get(defaultRegistration);
        if (registrationList != null) {
          for (int i = 0; i < registrationList.size(); i++) {
            if (registrationList.get(i) == zombiedRegistration) {
              registrationList.remove(i);
              break;
            }
          }
          if (registrationList.isEmpty()) {
            globalEventRegistrations.remove(defaultRegistration);
          }
        }
      }
    }
  }

  public void zombifyForRemove(EventRegistration registration) {
    synchronized (globalEventRegistrations) {
      List<EventRegistration> registrationList = globalEventRegistrations.get(registration);
      if (registrationList != null && !registrationList.isEmpty()) {
        if (registration.getQuerySpec().isDefault()) {
          // The behavior here has to match the behavior of SyncPoint.removeEventRegistration.
          // If the query is default, it remove a single instance of the registration
          // from each unique query.  So for example, if you had 3 copies registered under default,
          // you would end up with 2 still registered.
          // If you had 1 registration in default and 2 in query a', you'd end up with just
          // a single registration in a'.
          // To implement this, we just store in a hashset queries that we remove so we can still
          // keep a fairly simple structure.
          // Note that we *could* use the same logic for non-default as the list there only has a
          // a single query, but its somewhat wasteful to enumerate the list when we know we will
          // only grab 1.
          HashSet<QuerySpec> zombiedQueries = new HashSet<QuerySpec>();
          // Walk down the list so that removes do not mess up the enumeration.
          for (int i = registrationList.size() - 1; i >= 0; i--) {
            EventRegistration currentRegistration = registrationList.get(i);
            if (!zombiedQueries.contains(currentRegistration.getQuerySpec())) {
              zombiedQueries.add(currentRegistration.getQuerySpec());
              currentRegistration.zombify();
            }
          }
        } else {
          // Note that this entry cannot already be zombied because we are inside synchronization
          // and any previous calls to zombify would have removed the entry.
          registrationList.get(0).zombify();
        }
      }
    }
  }

  @Override
  public void onZombied(EventRegistration zombiedInstance) {
    unRecordEventRegistration(zombiedInstance);
  }
}
