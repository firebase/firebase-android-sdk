package com.google.firebase.firestore.local;

import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MemoryGlobalsCacheTest extends GlobalsCacheTest {

    @Override
    Persistence getPersistence() {
        return PersistenceTestHelpers.createEagerGCMemoryPersistence();
    }
}
