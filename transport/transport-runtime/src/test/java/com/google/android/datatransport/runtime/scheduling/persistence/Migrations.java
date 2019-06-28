package com.google.android.datatransport.runtime.scheduling.persistence;

import android.database.sqlite.SQLiteDatabase;

public class Migrations {
  Migration TO_V2 =
      new Migration() {
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
      };

  public interface Migration {
    void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion);
  }
}
