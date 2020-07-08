// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.crashlytics.internal.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Task;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class IdManager implements InstallIdProvider {

  public static final String DEFAULT_VERSION_NAME = "0.0";

  static final String PREFKEY_ADVERTISING_ID = "crashlytics.advertising.id";
  static final String PREFKEY_INSTALLATION_UUID = "crashlytics.installation.id";
  static final String PREFKEY_FIREBASE_IID = "firebase.installation.id";
  static final String PREFKEY_LEGACY_INSTALLATION_UUID = "crashlytics.installation.id";

  /** Regex for stripping all non-alphnumeric characters from ALL the identifier fields. */
  private static final Pattern ID_PATTERN = Pattern.compile("[^\\p{Alnum}]");

  private static final String FORWARD_SLASH_REGEX = Pattern.quote("/");

  private final InstallerPackageNameProvider installerPackageNameProvider;
  private final Context appContext;
  private final String appIdentifier;

  // The FirebaseInstallationsApi encapsulates a Firebase-wide install id
  private final FirebaseInstallationsApi firebaseInstallationsApi;
  // Crashlytics maintains a Crashlytics-specific install id, used in the crash processing backend
  private String crashlyticsInstallId;

  /**
   * @param appContext Application {@link Context}
   * @param appIdentifier the value to return as the App identifier (usually a package name), must
   *     not be <code>null</code>
   * @throws IllegalArgumentException if {@link Context} is null, or <code>appPackageName</code> is
   *     null
   */
  public IdManager(
      Context appContext, String appIdentifier, FirebaseInstallationsApi firebaseInstallationsApi) {
    if (appContext == null) {
      throw new IllegalArgumentException("appContext must not be null");
    }
    if (appIdentifier == null) {
      throw new IllegalArgumentException("appIdentifier must not be null");
    }
    this.appContext = appContext;
    this.appIdentifier = appIdentifier;
    this.firebaseInstallationsApi = firebaseInstallationsApi;

    installerPackageNameProvider = new InstallerPackageNameProvider();
  }

  /**
   * Apply consistent formatting and stripping of special characters. Null input is allowed, will
   * return null.
   */
  private static String formatId(String id) {
    return (id == null) ? null : ID_PATTERN.matcher(id).replaceAll("").toLowerCase(Locale.US);
  }

  /**
   * Return a UUID that is unique to this application installation, generating it if necessary.
   *
   * <p>The UUID is used to generate crash counts for a specific user, as well as for business
   * metrics. It can be reset by resetting the Firebase Instance Id (FID).
   *
   * <p>The first time this method is called, the returned value is read from Shared Preferences and
   * then cached in memory for the duration of the app execution. If the FID has been reset after
   * this method is called, the App Install Identifier will be reset on the subsequent launch.
   */
  @SuppressWarnings("JavadocReference")
  @Override
  @NonNull
  public synchronized String getCrashlyticsInstallId() {
    if (crashlyticsInstallId != null) {
      // Once found, the id is cached locally for the duration of execution; see javadoc comment for
      // this method.
      return crashlyticsInstallId;
    }
    final SharedPreferences prefs = CommonUtils.getSharedPrefs(appContext);

    // Crashlytics rotates the Crashlytics-specific IID if the Firebase IID ("FID") is reset.
    // This way, Crashlytics privacy controls are consistent with the rest of Firebase.
    Task<String> currentFidTask = firebaseInstallationsApi.getId();
    String currentFid = null;
    final String cachedFid = prefs.getString(PREFKEY_FIREBASE_IID, null);

    try {
      currentFid = Utils.awaitEvenIfOnMainThread(currentFidTask);
    } catch (Exception e) {
      Logger.getLogger().d("Failed to retrieve installation id", e);

      // this avoids rotating the identifier in the case that there was an exception which is likely
      // to succeed in a future invocation
      if (cachedFid != null) {
        currentFid = cachedFid;
      }
    }

    if (cachedFid == null) {
      // This must be either 1) a new installation or 2) an upgrade from the legacy
      // Crashlytics SDK.
      // If it is a legacy upgrade, we'll migrate the legacy ID to the new pref store.
      final SharedPreferences legacyPrefs = CommonUtils.getLegacySharedPrefs(appContext);
      final String legacyId = legacyPrefs.getString(PREFKEY_LEGACY_INSTALLATION_UUID, null);
      Logger.getLogger().d("No cached FID; legacy id is " + legacyId);

      if (legacyId == null) {
        // if there's no legacy ID, this must be a new Crashlytics install.
        crashlyticsInstallId = createAndStoreIid(currentFid, prefs);
      } else { // must be a legacy upgrade
        crashlyticsInstallId = legacyId;
        migrateLegacyId(legacyId, currentFid, prefs, legacyPrefs);
      }
      return crashlyticsInstallId;
    } else {
      // we have a cached FID, so check if it has been changed since previous launch
      if (cachedFid.equals(currentFid)) {
        crashlyticsInstallId = prefs.getString(PREFKEY_INSTALLATION_UUID, null);
        Logger.getLogger().d("Found matching FID, using Crashlytics IID: " + crashlyticsInstallId);
        // Since we've cached an FID previously, the IID should always exist at this point.
        // But check just in case:
        if (crashlyticsInstallId == null) {
          crashlyticsInstallId = createAndStoreIid(currentFid, prefs);
        }
      } else {
        // the FID has changed, so we need to update our IID and cache the new FID value.
        crashlyticsInstallId = createAndStoreIid(currentFid, prefs);
      }
      return crashlyticsInstallId;
    }
  }

  private synchronized void migrateLegacyId(
      String legacyId, String fidToCache, SharedPreferences prefs, SharedPreferences legacyPrefs) {
    Logger.getLogger().d("Migrating legacy Crashlytics IID: " + legacyId);
    prefs
        .edit()
        .putString(PREFKEY_INSTALLATION_UUID, legacyId)
        .putString(PREFKEY_FIREBASE_IID, fidToCache)
        .apply();
    legacyPrefs
        .edit()
        .remove(PREFKEY_LEGACY_INSTALLATION_UUID)
        .remove(PREFKEY_ADVERTISING_ID) // cleanup any previously-cached AdId from old SDK versions.
        .apply();
  }

  private synchronized String createAndStoreIid(String fidToCache, SharedPreferences prefs) {
    final String iid = formatId(UUID.randomUUID().toString());
    Logger.getLogger().d("Created new Crashlytics IID: " + iid);
    prefs
        .edit()
        .putString(PREFKEY_INSTALLATION_UUID, iid)
        .putString(PREFKEY_FIREBASE_IID, fidToCache)
        .apply();
    return iid;
  }

  /** @return the package name that identifies this App. */
  public String getAppIdentifier() {
    return appIdentifier;
  }

  /**
   * @return {@link String} identifying the display version of the Android OS that the device is
   *     running, e.g. "4.2.2". Any forward slashes in the system returned value will be removed.
   */
  public String getOsDisplayVersionString() {
    return removeForwardSlashesIn(Build.VERSION.RELEASE);
  }

  /**
   * @return {@link String} identifying the build version of the Android OS that the device is
   *     running, e.g. "573038". Any forward slashes in the system returned value will be removed.
   */
  public String getOsBuildVersionString() {
    return removeForwardSlashesIn(Build.VERSION.INCREMENTAL);
  }

  /**
   * @return {@link String} identifying the model of this device. Includes the manufacturer and
   *     model names.
   */
  public String getModelName() {
    return String.format(
        Locale.US,
        "%s/%s",
        removeForwardSlashesIn(Build.MANUFACTURER),
        removeForwardSlashesIn(Build.MODEL));
  }

  private String removeForwardSlashesIn(String s) {
    return s.replaceAll(FORWARD_SLASH_REGEX, "");
  }

  public String getInstallerPackageName() {
    return installerPackageNameProvider.getInstallerPackageName(appContext);
  }
}
