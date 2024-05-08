package com.google.firebase.firestore.local;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteGlobalCacheTest extends GlobalsCacheTest {

    @Override
    Persistence getPersistence() {
        return PersistenceTestHelpers.createSQLitePersistence();
    }
}
