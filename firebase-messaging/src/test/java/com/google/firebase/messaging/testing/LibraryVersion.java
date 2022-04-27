// Copyright 2019 Google LLC
package com.google.firebase.messaging.testing;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.GmsLogger;
import com.google.android.gms.common.internal.Preconditions;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

// TODO Remove this version once the version in basement is available
/** Helper class to retrieve SDK versions. */
public class LibraryVersion {
  private static final String TAG = "LibraryVersion";
  private static final GmsLogger LOGGER = new GmsLogger(TAG, /* messagePrefix= */ "");
  private static final String UNKNOWN_VERSION = "UNKNOWN";

  private static LibraryVersion INSTANCE = new LibraryVersion();
  /** Returns the singleton instance of LibraryVersion. */
  public static LibraryVersion getInstance() {
    return INSTANCE;
  }

  @VisibleForTesting
  public static synchronized void setInstanceForTesting(LibraryVersion instance) {
    INSTANCE = instance;
  }

  private LibraryVersion() {}

  private final ConcurrentHashMap<String, String> libraryVersionMap = new ConcurrentHashMap<>();

  public String getVersion(@NonNull String libraryName) {
    Preconditions.checkNotEmpty(libraryName, "Please provide a valid libraryName");

    if (libraryVersionMap.containsKey(libraryName)) {
      return libraryVersionMap.get(libraryName);
    }

    String version = null;
    Properties props = new Properties();
    try {
      String propertiesFileName = String.format("/%s.properties", libraryName);
      InputStream fs = LibraryVersion.class.getResourceAsStream(propertiesFileName);
      if (fs != null) {
        props.load(fs);
        version = props.getProperty("version", /* defaultValue */ null);
        LOGGER.v(TAG, libraryName + " version is " + version);
      } else {
        LOGGER.e(TAG, "Failed to get app version for libraryName: " + libraryName);
      }
    } catch (IOException e) {
      LOGGER.e(TAG, "Failed to get app version for libraryName: " + libraryName, e);
    }

    if (version == null) {
      version = UNKNOWN_VERSION;
      LOGGER.d(
          TAG,
          ".properties file is dropped during release process. Failure to read app version is"
              + "expected druing Google internal testing where locally-built libraries are used");
    }

    // Even if library version is unknown, we still cache it (since retry won't help).
    libraryVersionMap.put(libraryName, version);
    return version;
  }
}
