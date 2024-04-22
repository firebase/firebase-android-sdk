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
package com.google.android.datatransport.runtime.scheduling.persistence;

import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.DB_NAME;
import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.SCHEMA_VERSION;

import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class SchemaManagerMigrationTest {
  private final int highVersion;
  private final int lowVersion;

  private static final Map<Integer, StateSimulations.StateSimulator> simulatorMap = new HashMap<>();

  static {
    simulatorMap.put(1, new StateSimulations.V1());
    simulatorMap.put(2, new StateSimulations.V2());
    simulatorMap.put(3, new StateSimulations.V3());
    simulatorMap.put(4, new StateSimulations.V4());
    simulatorMap.put(5, new StateSimulations.V5());
    simulatorMap.put(6, new StateSimulations.V6());
    simulatorMap.put(7, new StateSimulations.V7());
  }

  @ParameterizedRobolectricTestRunner.Parameters(name = "lowVersion = {0}, highVersion = {1}")
  public static Collection<Object[]> data() {
    Collection<Object[]> params = new ArrayList<>();
    for (int fromVersion = 1; fromVersion < SCHEMA_VERSION; fromVersion++) {
      for (int toVersion = fromVersion + 1; toVersion <= SCHEMA_VERSION; toVersion++) {
        params.add(new Object[] {fromVersion, toVersion});
      }
    }
    return params;
  }

  public SchemaManagerMigrationTest(int lowVersion, int highVersion) {
    this.lowVersion = lowVersion;
    this.highVersion = highVersion;
  }

  @Test
  public void upgrade_migratesSuccessfully() {
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, lowVersion);
    try (SQLiteDatabase db = schemaManager.getWritableDatabase()) {
      schemaManager.onUpgrade(db, lowVersion, highVersion);

      simulatorMap.get(highVersion).simulate(schemaManager);
    }
  }

  @Test
  public void downgrade_migratesSuccessfully() {
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, highVersion);
    try (SQLiteDatabase db = schemaManager.getWritableDatabase()) {
      schemaManager.onDowngrade(db, highVersion, lowVersion);
      simulatorMap.get(lowVersion).simulate(schemaManager);
    }
  }

  @Test
  public void downgrade_upgrade_migratesSuccessfully() {
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, highVersion);
    try (SQLiteDatabase db = schemaManager.getWritableDatabase()) {
      schemaManager.onDowngrade(db, highVersion, lowVersion);
      simulatorMap.get(lowVersion).simulate(schemaManager);

      schemaManager.onUpgrade(db, lowVersion, highVersion);
      simulatorMap.get(highVersion).simulate(schemaManager);
    }
  }

  @Test
  public void upgrade_downgrade_upgrade_migratesSuccessfully() {
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, lowVersion);
    try (SQLiteDatabase db = schemaManager.getWritableDatabase()) {
      schemaManager.onUpgrade(db, lowVersion, highVersion);
      simulatorMap.get(highVersion).simulate(schemaManager);

      schemaManager.onDowngrade(db, highVersion, lowVersion);
      simulatorMap.get(lowVersion).simulate(schemaManager);

      schemaManager.onUpgrade(db, lowVersion, highVersion);
      simulatorMap.get(highVersion).simulate(schemaManager);
    }
  }
}
