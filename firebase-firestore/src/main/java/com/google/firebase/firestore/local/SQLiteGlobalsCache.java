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

import androidx.annotation.NonNull;
import com.google.protobuf.ByteString;

public class SQLiteGlobalsCache implements GlobalsCache {

  private static final String SESSION_TOKEN = "sessionToken";
  private final SQLitePersistence db;

  public SQLiteGlobalsCache(SQLitePersistence persistence) {
    this.db = persistence;
  }

  @NonNull
  @Override
  public ByteString getSessionsToken() {
    byte[] bytes = get(SESSION_TOKEN);
    return bytes == null ? ByteString.EMPTY : ByteString.copyFrom(bytes);
  }

  @Override
  public void setSessionToken(@NonNull ByteString value) {
    set(SESSION_TOKEN, value.toByteArray());
  }

  private byte[] get(@NonNull String name) {
    return db.query("SELECT value FROM globals WHERE name = ?")
        .binding(name)
        .firstValue(row -> row.getBlob(0));
  }

  private void set(@NonNull String name, @NonNull byte[] value) {
    db.execute("INSERT OR REPLACE INTO globals " + "(name, value) " + "VALUES (?, ?)", name, value);
  }
}
