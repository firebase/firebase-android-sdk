// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.local;

import com.google.firebase.firestore.auth.User;
import com.google.protobuf.ByteString;

public class SQLiteGlobalsCache implements GlobalsCache{

    private static final String DB_TOKEN = "dbToken";
    private final SQLitePersistence db;

    public SQLiteGlobalsCache(SQLitePersistence persistence) {
        this.db = persistence;
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
        return db.query("SELECT value FROM globals WHERE global = ?")
                .binding(global)
                .firstValue(row -> row.getBlob(0));
    }

    private void set(String global, byte[] value) {
        db.execute(
                "INSERT OR REPLACE INTO globals "
                        + "(global, value) "
                        + "VALUES (?, ?)",
                global,
                value);
    }
}
