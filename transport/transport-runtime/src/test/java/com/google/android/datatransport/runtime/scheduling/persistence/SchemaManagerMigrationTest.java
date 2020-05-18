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

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.DB_NAME;
import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.SCHEMA_VERSION;
import static com.google.common.truth.Truth.assertThat;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.util.PriorityMapping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class SchemaManagerMigrationTest {
    private final int highVersion;
    private final int lowVersion;

    private static Map<Integer, StateSimulations.StateSimulator> simulatorMap = new HashMap<>();
    static {
        simulatorMap.put(1, new StateSimulations.V1());
        simulatorMap.put(2, new StateSimulations.V2());
        simulatorMap.put(3, new StateSimulations.V3());
        simulatorMap.put(4, new StateSimulations.V4());
    }
    @ParameterizedRobolectricTestRunner.Parameters(name = "lowVersion = {0}, highVersion = {1}")
    public static Collection<Object[]> data() {
        Collection<Object[]> params = new ArrayList<>();
        for(int lowVersion  = 1 ; lowVersion < SCHEMA_VERSION; lowVersion ++) {
            for (int highVersion = lowVersion + 1; highVersion <= SCHEMA_VERSION; highVersion++) {
                params.add(new Object[]{lowVersion, highVersion});
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
        schemaManager.onUpgrade(schemaManager.getWritableDatabase(), lowVersion, highVersion);
        simulatorMap.get(highVersion).simulate(schemaManager);
    }

    @Test
    public void downgrade_migratesSuccessfully() {
        SchemaManager schemaManager =
                new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, highVersion);
        schemaManager.onDowngrade(schemaManager.getWritableDatabase(), highVersion, lowVersion);
        simulatorMap.get(lowVersion).simulate(schemaManager);
    }

    @Test
    public void downgrade_upgrade_migratesSuccessfully() {
        SchemaManager schemaManager =
                new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, highVersion);

        schemaManager.onDowngrade(schemaManager.getWritableDatabase(), highVersion, lowVersion);
        simulatorMap.get(lowVersion).simulate(schemaManager);

        schemaManager.onUpgrade(schemaManager.getWritableDatabase(), lowVersion, highVersion);
        simulatorMap.get(highVersion).simulate(schemaManager);
    }

    @Test
    public void upgrade_downgrade_upgrade_migratesSuccessfully() {
        SchemaManager schemaManager =
                new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, lowVersion);

        schemaManager.onUpgrade(schemaManager.getWritableDatabase(), lowVersion, highVersion);
        simulatorMap.get(highVersion).simulate(schemaManager);

        schemaManager.onDowngrade(schemaManager.getWritableDatabase(), highVersion, lowVersion);
        simulatorMap.get(lowVersion).simulate(schemaManager);

        schemaManager.onUpgrade(schemaManager.getWritableDatabase(), lowVersion, highVersion);
        simulatorMap.get(highVersion).simulate(schemaManager);
    }

    private PersistedEvent simulatedPersistOnV1Database(
            SchemaManager schemaManager, TransportContext transportContext, EventInternal eventInternal) {
        SQLiteDatabase db = schemaManager.getWritableDatabase();

        ContentValues record = new ContentValues();
        record.put("backend_name", transportContext.getBackendName());
        record.put("priority", PriorityMapping.toInt(transportContext.getPriority()));
        record.put("next_request_ms", 0);
        long contextId = db.insert("transport_contexts", null, record);

        ContentValues values = new ContentValues();
        values.put("context_id", contextId);
        values.put("transport_name", eventInternal.getTransportName());
        values.put("timestamp_ms", eventInternal.getEventMillis());
        values.put("uptime_ms", eventInternal.getUptimeMillis());
        values.put("payload", eventInternal.getPayload());
        values.put("code", eventInternal.getCode());
        values.put("num_attempts", 0);
        long newEventId = db.insert("events", null, values);

        for (Map.Entry<String, String> entry : eventInternal.getMetadata().entrySet()) {
            ContentValues metadata = new ContentValues();
            metadata.put("event_id", newEventId);
            metadata.put("name", entry.getKey());
            metadata.put("value", entry.getValue());
            db.insert("event_metadata", null, metadata);
        }

        return PersistedEvent.create(newEventId, transportContext, eventInternal);
    }
}
