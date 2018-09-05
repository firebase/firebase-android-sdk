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

package com.google.firebase.firestore.core;

/**
 * Generates monotonically increasing integer IDs. There are separate generators for different
 * scopes. While these generators will operate independently of each other, they are scoped, such
 * that no two generators will ever produce the same ID. This is useful, because sometimes the
 * backend may group IDs from separate parts of the client into the same ID space.
 */
public class TargetIdGenerator {

  /**
   * Creates and returns the TargetIdGenerator for the local store.
   *
   * @param after An ID to start at. Every call to nextID will return an id > after.
   * @return A shared instance of TargetIdGenerator.
   */
  public static TargetIdGenerator getLocalStoreIdGenerator(int after) {
    return new TargetIdGenerator(LOCAL_STATE_ID, after);
  }

  /**
   * Creates and returns the TargetIdGenerator for the sync engine.
   *
   * @param after An ID to start at. Every call to nextID will return an id > after.
   * @return A shared instance of TargetIdGenerator.
   */
  public static TargetIdGenerator getSyncEngineGenerator(int after) {
    return new TargetIdGenerator(SYNC_ENGINE_ID, after);
  }

  private static final int LOCAL_STATE_ID = 0;
  private static final int SYNC_ENGINE_ID = 1;

  private static final int RESERVED_BITS = 1;

  private int previousId;

  TargetIdGenerator(int generatorId, int after) {
    int afterWithoutGenerator = (after >>> RESERVED_BITS) << RESERVED_BITS;
    int afterGenerator = after - afterWithoutGenerator;
    if (afterGenerator >= generatorId) {
      // For example, if:
      //   self.generatorID = 0b0000
      //   after = 0b1011
      //   afterGenerator = 0b0001
      // Then:
      //   previous = 0b1010
      //   next = 0b1100
      previousId = afterWithoutGenerator | generatorId;
    } else {
      // For example, if:
      //   self.generatorID = 0b0001
      //   after = 0b1010
      //   afterGenerator = 0b0000
      // Then:
      //   previous = 0b1001
      //   next = 0b1011
      previousId = (afterWithoutGenerator | generatorId) - (1 << RESERVED_BITS);
    }
  }

  /** @return the next id in the sequence */
  public int nextId() {
    previousId += 1 << RESERVED_BITS;
    return previousId;
  }
}
