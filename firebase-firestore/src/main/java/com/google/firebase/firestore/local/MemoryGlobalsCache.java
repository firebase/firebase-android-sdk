package com.google.firebase.firestore.local;

import com.google.protobuf.ByteString;

/** In-memory cache of global values */
final class MemoryGlobalsCache implements GlobalsCache {

    private ByteString dbToken;

    @Override
    public ByteString getDbToken() {
        return dbToken;
    }

    @Override
    public void setDbToken(ByteString value) {
        dbToken = value;
    }
}
