package com.google.firebase.firestore.local;

import com.google.firebase.firestore.auth.User;
import com.google.protobuf.ByteString;

public class SQLiteGlobalsCache implements GlobalsCache{

    private static final String DB_TOKEN = "dbToken";
    private final SQLitePersistence db;

    /** The normalized uid (for example, null => "") used in the uid column. */
    private final String uid;

    public SQLiteGlobalsCache(SQLitePersistence persistence, User user) {
        this.db = persistence;
        this.uid = user.isAuthenticated() ? user.getUid() : "";
    }

    @Override
    public ByteString getDbToken() {
        byte[] bytes = get(DB_TOKEN);
        return bytes == null ? null : ByteString.copyFrom(bytes);
    }

    @Override
    public void setDbToken(ByteString value) {
        set(DB_TOKEN, value.toByteArray());
    }

    private byte[] get(String global) {
        return db.query("SELECT value FROM globals WHERE uid = ? AND global = ?")
                .binding(uid, global)
                .firstValue(row -> row.getBlob(0));
    }

    private void set(String global, byte[] value) {
        db.execute(
                "INSERT OR REPLACE INTO globals "
                        + "(uid, global, value) "
                        + "VALUES (?, ?, ?)",
                uid,
                global,
                value);
    }
}
