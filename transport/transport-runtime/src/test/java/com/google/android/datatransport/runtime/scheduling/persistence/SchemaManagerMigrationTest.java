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
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RobolectricTestRunner;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class SchemaManagerMigrationTest {
    private final int toVersion;
    private final int fromVersion;

    @ParameterizedRobolectricTestRunner.Parameters(name = "lowVersion = {0}, highVersion = {1}")
    public static Collection<Object[]> data() {
        Collection<Object[]> params = new ArrayList<>();
        for(int fromVersion  = 1 ; fromVersion < SCHEMA_VERSION; fromVersion ++) {
            for (int toVersion = fromVersion + 1; toVersion <= SCHEMA_VERSION; toVersion++) {
                params.add(new Object[]{fromVersion, toVersion});
            }
        }
        return params;
    }

    public SchemaManagerMigrationTest(int fromVersion, int toVersion) {
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }

    @Test
    public void upgrade_migratesSuccessfully() {
        SchemaManager schemaManager =
                new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, fromVersion);
        schemaManager.onUpgrade(schemaManager.getWritableDatabase(), fromVersion, toVersion);
    }

    @Test
    public void downgrade_migratesSuccessfully() {
        SchemaManager schemaManager =
                new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, toVersion);
        schemaManager.onDowngrade(schemaManager.getWritableDatabase(), toVersion, fromVersion);
    }

    @Test
    public void downgrade_upgrade_migratesSuccessfully() {
        SchemaManager schemaManager =
                new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, toVersion);

        schemaManager.onDowngrade(schemaManager.getWritableDatabase(), toVersion, fromVersion);
        schemaManager.onUpgrade(schemaManager.getWritableDatabase(), fromVersion, toVersion);
    }
}
