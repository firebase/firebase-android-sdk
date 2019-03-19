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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.IntegrationTestHelpers;
import com.google.firebase.database.core.view.View;
import com.google.firebase.database.core.view.ViewAccess;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

/** Verifies that the zombie state matches the current registration list in a Repo */
public class ZombieVerifier {

  public static void verifyRepoZombies(List<DatabaseReference> references)
      throws InterruptedException {
    for (DatabaseReference ref : references) {
      verifyRepoZombies(ref);
    }
  }

  public static void verifyRepoZombies(DatabaseReference ref) throws InterruptedException {
    verifyRepoZombies(ref.getRepo());
  }

  public static void verifyRepoZombies(List<DatabaseReference> references, Semaphore semaphore)
      throws InterruptedException {
    for (DatabaseReference ref : references) {
      verifyRepoZombies(ref, semaphore);
    }
  }

  public static void verifyRepoZombies(DatabaseReference ref, Semaphore semaphore)
      throws InterruptedException {
    verifyRepoZombies(ref.getRepo(), semaphore);
  }

  static void verifyRepoZombies(final Repo repo) throws InterruptedException {
    final Semaphore wait = new Semaphore(0);

    verifyRepoZombies(repo, wait);
    IntegrationTestHelpers.waitFor(wait);
  }

  // To verify our state, we:
  // 1. verify each eventregistration instance that exists in the zombiemanager also exist in some
  //    view
  // 2. there are no zombied registrations inside zombiemanager
  static void verifyRepoZombies(final Repo repo, final Semaphore semaphore)
      throws InterruptedException {
    repo.scheduleNow(
        new Runnable() {
          @Override
          public void run() {
            ZombieEventManager manager = ZombieEventManager.getInstance();

            HashMap<InstanceHashKey, Long> zombieCount = new HashMap<InstanceHashKey, Long>();

            for (List<EventRegistration> list : manager.globalEventRegistrations.values()) {
              for (EventRegistration eventRegistration : list) {
                if (eventRegistration.getRepo() != repo) {
                  continue;
                }
                assertFalse(eventRegistration.isZombied());
                InstanceHashKey key = new InstanceHashKey(eventRegistration);
                Long current = zombieCount.get(key);
                zombieCount.put(key, current == null ? 1 : current + 1);
              }
            }

            HashMap<InstanceHashKey, Long> viewCount = new HashMap<InstanceHashKey, Long>();
            // now enumerate views in the repo.
            for (SyncPoint point : repo.getServerSyncTree().getSyncPointTree().values()) {
              scanRegistrations(repo, point, viewCount);
            }
            for (SyncPoint point : repo.getInfoSyncTree().getSyncPointTree().values()) {
              scanRegistrations(repo, point, viewCount);
            }

            for (InstanceHashKey key : zombieCount.keySet()) {
              Long zombies = zombieCount.get(key);
              if (!key.getInner().getQuerySpec().isDefault()) {
                zombies = zombies / 2;
              }
              if (!zombies.equals(viewCount.get(key))) {
                fail("an imbalance in the force has been detected!");
              }
            }

            semaphore.release();
          }
        });
  }

  private static void scanRegistrations(
      Repo repo, SyncPoint point, HashMap<InstanceHashKey, Long> viewCount) {
    for (View view : point.getViews().values()) {
      for (EventRegistration eventRegistration : ViewAccess.getEventRegistrations(view)) {
        if (eventRegistration.getRepo() != repo) {
          continue;
        }
        InstanceHashKey key = new InstanceHashKey(eventRegistration);
        Long current = viewCount.get(key);
        viewCount.put(key, current == null ? 1 : current + 1);
      }
    }
  }

  // Special class that we use to test based on instance equality.
  static class InstanceHashKey {
    private EventRegistration inner;

    public InstanceHashKey(EventRegistration inner) {
      this.inner = inner;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof InstanceHashKey)) {
        return false;
      }
      return inner == ((InstanceHashKey) obj).inner;
    }

    @Override
    public int hashCode() {
      return inner.hashCode();
    }

    public EventRegistration getInner() {
      return inner;
    }
  }
}
