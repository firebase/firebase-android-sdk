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

import static com.google.firebase.crashlytics.internal.common.Utils.awaitEvenIfOnMainThread;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class IdManager implements InstallIdProvider {

  public static final String DEFAULT_VERSION_NAME = "0.0";

  static final String PREFKEY_ADVERTISING_ID = "crashlytics.advertising.id";
  static final String PREFKEY_INSTALLATION_UUID = "crashlytics.installation.id";
  static final String PREFKEY_FIREBASE_IID = "firebase.installation.id";
  static final String PREFKEY_LEGACY_INSTALLATION_UUID = "crashlytics.installation.id";

  /** Regex for stripping all non-alphanumeric characters from ALL the identifier fields. */
  private static final Pattern ID_PATTERN = Pattern.compile("[^\\p{Alnum}]");

  private static final String SYNTHETIC_FID_PREFIX = "SYN_";

  private static final String FORWARD_SLASH_REGEX = Pattern.quote("/");

  private final InstallerPackageNameProvider installerPackageNameProvider;
  private final Context appContext;
  private final String appIdentifier;

  // The FirebaseInstallationsApi encapsulates a Firebase-wide install id
  private final FirebaseInstallationsApi firebaseInstallations;

  private final DataCollectionArbiter dataCollectionArbiter;

  // Stores a Crashlytics-specific install id, and Firebase installation id.
  private InstallIds installIds;

  /**
   * @param appContext Application {@link Context}
   * @param appIdentifier the value to return as the App identifier (usually a package name), must
   *     not be <code>null</code>
   * @throws IllegalArgumentException if {@link Context} is null, or <code>appPackageName</code> is
   *     null
   */
  public IdManager(
      Context appContext,
      String appIdentifier,
      FirebaseInstallationsApi firebaseInstallations,
      DataCollectionArbiter dataCollectionArbiter) {
    if (appContext == null) {
      throw new IllegalArgumentException("appContext must not be null");
    }
    if (appIdentifier == null) {
      throw new IllegalArgumentException("appIdentifier must not be null");
    }
    this.appContext = appContext;
    this.appIdentifier = appIdentifier;
    this.firebaseInstallations = firebaseInstallations;
    this.dataCollectionArbiter = dataCollectionArbiter;

    installerPackageNameProvider = new InstallerPackageNameProvider();
  }

  /** Apply consistent formatting and stripping of special characters. */
  @NonNull
  private static String formatId(@NonNull String id) {
    return ID_PATTERN.matcher(id).replaceAll("").toLowerCase(Locale.US);
  }

  /**
   * Return a UUID that is unique to this application installation, generating it if necessary.
   *
   * <p>The UUID is used to generate crash counts for a specific user, as well as for business
   * metrics. It can be reset by resetting the Firebase Instance Id (FID) or toggling Crashlytics
   * data collection.
   *
   * <p>The first time this method is called, the returned value is read from Shared Preferences and
   * then cached in memory for the duration of the app execution. If the FID has been reset after
   * this method is called, the App Install Identifier will be reset on the subsequent launch.
   */
  @Override
  @NonNull
  public synchronized InstallIds getInstallIds() {
    if (!shouldRefresh()) {
      return installIds;
    }

    Logger.getLogger().v("Determining Crashlytics installation ID...");
    final SharedPreferences prefs = CommonUtils.getSharedPrefs(appContext);
    final String cachedFid = prefs.getString(PREFKEY_FIREBASE_IID, null);
    Logger.getLogger().v("Cached Firebase Installation ID: " + cachedFid);

    // We only look at the FID if Crashlytics data collection is enabled, since querying it can
    // result in a network call that registers the FID with Firebase.
    if (dataCollectionArbiter.isAutomaticDataCollectionEnabled()) {
      // We don't need an auth token here, we just need to detect if the fid has changed.
      FirebaseInstallationId trueFid = fetchTrueFid(/* validate= */ false);
      Logger.getLogger().v("Fetched Firebase Installation ID: " + trueFid.getFid());

      if (trueFid.getFid() == null) {
        // This shouldn't happen often. We will assume the cached FID is valid, if it exists.
        // Otherwise, the safest thing to do is to create a synthetic ID instead
        trueFid =
            new FirebaseInstallationId(cachedFid == null ? createSyntheticFid() : cachedFid, null);
      }

      if (Objects.equals(trueFid.getFid(), cachedFid)) {
        // the current FID is the same as the cached FID, so we keep the cached Crashlytics ID
        installIds = InstallIds.create(readCachedCrashlyticsInstallId(prefs), trueFid);
      } else {
        // the current FID has changed, so we generate a new Crashlytics ID
        installIds =
            InstallIds.create(createAndCacheCrashlyticsInstallId(trueFid.getFid(), prefs), trueFid);
      }
    } else { // data collection is NOT enabled; we can't use the FID
      if (isSyntheticFid(cachedFid)) {
        // We already have a cached synthetic FID, so we don't need to change the Crashlytics ID
        installIds = InstallIds.createWithoutFid(readCachedCrashlyticsInstallId(prefs));
      } else {
        // we don't have a synthetic FID, so we need to replace the cached FID with a synthetic
        // one and create a new Crashlytics install id.
        installIds =
            InstallIds.createWithoutFid(
                createAndCacheCrashlyticsInstallId(createSyntheticFid(), prefs));
      }
    }
    Logger.getLogger().v("Install IDs: " + installIds);
    return installIds;
  }

  /**
   * Returns true if we have not cached an InstallIds, or should force refresh the fid.
   *
   * <p>We should force refresh the fid if data collection is enabled but we don't have a cached
   * fid. This can happen if data collection was disabled at crash time, but enabled at upload time.
   */
  private boolean shouldRefresh() {
    return installIds == null
        || (installIds.getFirebaseInstallationId() == null
            && dataCollectionArbiter.isAutomaticDataCollectionEnabled());
  }

  static String createSyntheticFid() {
    return SYNTHETIC_FID_PREFIX + UUID.randomUUID().toString();
  }

  static boolean isSyntheticFid(String fid) {
    return (fid != null && fid.startsWith(SYNTHETIC_FID_PREFIX));
  }

  private String readCachedCrashlyticsInstallId(SharedPreferences prefs) {
    return prefs.getString(PREFKEY_INSTALLATION_UUID, null);
  }

  /**
   * Makes a blocking call to query the Firebase installation id and Firebase authentication token.
   *
   * <p>If either call fails for any reason, logs a warning and sets a null value for that field.
   */
  @NonNull
  public FirebaseInstallationId fetchTrueFid(boolean validate) {
    String fid = null;
    String authToken = null;

    if (validate) {
      // Fetch the auth token first when requested, so the fid will be validated.
      try {
        authToken = awaitEvenIfOnMainThread(firebaseInstallations.getToken(false)).getToken();
      } catch (Exception ex) {
        Logger.getLogger().w("Error getting Firebase authentication token.", ex);
      }
    }
    try {
      fid = awaitEvenIfOnMainThread(firebaseInstallations.getId());
    } catch (Exception ex) {
      Logger.getLogger().w("Error getting Firebase installation id.", ex);
    }

    return new FirebaseInstallationId(fid, authToken);
  }

  @NonNull
  private synchronized String createAndCacheCrashlyticsInstallId(
      String fidToCache, SharedPreferences prefs) {
    final String iid = formatId(UUID.randomUUID().toString());
    Logger.getLogger()
        .v("Created new Crashlytics installation ID: " + iid + " for FID: " + fidToCache);
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
