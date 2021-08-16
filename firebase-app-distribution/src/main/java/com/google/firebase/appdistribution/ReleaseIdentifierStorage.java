package com.google.firebase.appdistribution;

import android.content.Context;
import android.content.SharedPreferences;

public class ReleaseIdentifierStorage {

  private static final String RELEASE_IDENTIFIER_PREFERENCES_NAME =
      "FirebaseAppDistributionReleaseIdentifierStorage";

  private final SharedPreferences releaseIdentifierSharedPreferences;

  ReleaseIdentifierStorage(Context applicationContext) {
    this.releaseIdentifierSharedPreferences =
        applicationContext.getSharedPreferences(
            RELEASE_IDENTIFIER_PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  void setCodeHashMap(String internalCodeHash, String externalCodeHash) {
    this.releaseIdentifierSharedPreferences
        .edit()
        .putString(internalCodeHash, externalCodeHash)
        .apply();
  }

  String getExternalCodeHash(String internalCodeHash) {
    return releaseIdentifierSharedPreferences.getString(internalCodeHash, null);
  }
}
