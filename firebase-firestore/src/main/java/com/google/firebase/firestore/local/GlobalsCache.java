package com.google.firebase.firestore.local;

import com.google.protobuf.ByteString;

/**
 * General purpose cache for global values for user.
 *
 * <p>Global state that cuts across components should be saved here. Following are contained herein:
 *
 * <p>`db_token` tracks server interaction across Listen and Write streams. This facilitates cache
 * synchronization and invalidation.
 */
interface GlobalsCache {

    ByteString getDbToken();

    void setDbToken(ByteString value);

}
