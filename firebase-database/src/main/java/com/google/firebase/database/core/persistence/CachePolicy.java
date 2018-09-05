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

package com.google.firebase.database.core.persistence;

public interface CachePolicy {
  public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries);

  public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck);

  public float getPercentOfQueriesToPruneAtOnce();

  public long getMaxNumberOfQueriesToKeep();

  public static CachePolicy NONE =
      new CachePolicy() {
        @Override
        public boolean shouldPrune(long currentSizeBytes, long countOfPrunableQueries) {
          return false;
        }

        @Override
        public boolean shouldCheckCacheSize(long serverUpdatesSinceLastCheck) {
          return false;
        }

        @Override
        public float getPercentOfQueriesToPruneAtOnce() {
          return 0;
        }

        @Override
        public long getMaxNumberOfQueriesToKeep() {
          return Long.MAX_VALUE;
        }
      };
}
