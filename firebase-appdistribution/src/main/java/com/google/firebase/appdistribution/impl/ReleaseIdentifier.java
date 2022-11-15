// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import static com.google.firebase.appdistribution.impl.PackageInfoUtils.getPackageInfoWithMetadata;

import android.content.pm.PackageInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Identifies the installed release using binary identifiers. */
class ReleaseIdentifier {

  private static final String TAG = "ApkHashExtractor";
  private static final int BYTES_IN_LONG = 8;
  static final String IAS_ARTIFACT_ID_METADATA_KEY = "com.android.vending.internal.apk.id";

  private final ConcurrentMap<String, String> cachedApkHashes = new ConcurrentHashMap<>();
  private final FirebaseApp firebaseApp;
  private final FirebaseAppDistributionTesterApiClient testerApiClient;

  ReleaseIdentifier(
      FirebaseApp firebaseApp, FirebaseAppDistributionTesterApiClient testerApiClient) {
    this.firebaseApp = firebaseApp;
    this.testerApiClient = testerApiClient;
  }

  /** Identify the currently installed release, returning the release name. */
  Task<String> identifyRelease() {
    if (developmentModeEnabled()) {
      return Tasks.forResult(null);
    }

    // Attempt to find release using IAS artifact ID, which identifies app bundle releases
    String iasArtifactId = null;
    try {
      iasArtifactId = extractInternalAppSharingArtifactId();
    } catch (FirebaseAppDistributionException e) {
      LogWrapper.getInstance()
          .w(
              "Error extracting IAS artifact ID to identify app bundle. Assuming release is an APK.");
    }
    if (iasArtifactId != null) {
      return testerApiClient.findReleaseUsingIasArtifactId(iasArtifactId);
    }

    // If we can't find an IAS artifact ID, we assume the installed release is an APK
    String apkHash;
    try {
      apkHash = extractApkHash();
    } catch (FirebaseAppDistributionException e) {
      return Tasks.forException(e);
    }
    return testerApiClient.findReleaseUsingApkHash(apkHash);
  }

  /**
   * Extract the IAS artifact ID of the installed app.
   *
   * @return null if the IAS artifact ID was not present in the app metadata, which will happen if
   *     the app was installed via APK
   */
  @Nullable
  String extractInternalAppSharingArtifactId() throws FirebaseAppDistributionException {
    PackageInfo packageInfo = getPackageInfoWithMetadata(firebaseApp.getApplicationContext());
    if (packageInfo.applicationInfo.metaData == null) {
      throw new FirebaseAppDistributionException("Missing package info metadata", Status.UNKNOWN);
    }
    return packageInfo.applicationInfo.metaData.getString(IAS_ARTIFACT_ID_METADATA_KEY);
  }

  /**
   * Extract the SHA-256 hash of the installed APK.
   *
   * <p>The result is stored in an in-memory cache to avoid computing it repeatedly.
   */
  String extractApkHash() throws FirebaseAppDistributionException {
    PackageInfo metadataPackageInfo =
        getPackageInfoWithMetadata(firebaseApp.getApplicationContext());
    String installedReleaseApkHash = extractApkHash(metadataPackageInfo);
    if (installedReleaseApkHash == null || installedReleaseApkHash.isEmpty()) {
      throw new FirebaseAppDistributionException(
          "Could not calculate hash of installed APK", Status.UNKNOWN);
    }
    return installedReleaseApkHash;
  }

  private String extractApkHash(PackageInfo packageInfo) {
    File sourceFile = new File(packageInfo.applicationInfo.sourceDir);

    String key =
        String.format(
            Locale.ENGLISH, "%s.%d", sourceFile.getAbsolutePath(), sourceFile.lastModified());
    if (!cachedApkHashes.containsKey(key)) {
      cachedApkHashes.put(key, calculateApkHash(sourceFile));
    }
    return cachedApkHashes.get(key);
  }

  @VisibleForTesting
  @Nullable
  String calculateApkHash(@NonNull File file) {
    LogWrapper.getInstance()
        .v(
            TAG,
            String.format(
                "Calculating release id for %s (%d bytes)", file.getPath(), file.length()));

    long start = System.currentTimeMillis();
    long entries = 0;
    String zipFingerprint = null;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      ArrayList<Byte> checksums = new ArrayList<>();

      // Since calculating the codeHash returned from the release backend is computationally
      // expensive, we has the existing checksum data from the ZipFile and compare it to
      // (1) the apk hash returned by the backend, or (2) look up a mapping from the apk zip hash to
      // the full codehash, and compare that to the codehash to the backend
      ZipFile zis = new ZipFile(file);
      try {
        Enumeration<? extends ZipEntry> zipEntries = zis.entries();
        while (zipEntries.hasMoreElements()) {
          ZipEntry zip = zipEntries.nextElement();
          entries += 1;
          byte[] crcBytes = longToByteArray(zip.getCrc());
          for (byte b : crcBytes) {
            checksums.add(b);
          }
        }
      } finally {
        zis.close();
      }
      byte[] checksumByteArray = digest.digest(arrayListToByteArray(checksums));
      StringBuilder sb = new StringBuilder();
      for (byte b : checksumByteArray) {
        sb.append(String.format("%02x", b));
      }
      zipFingerprint = sb.toString();

    } catch (IOException | NoSuchAlgorithmException e) {
      LogWrapper.getInstance().v(TAG, "id calculation failed for " + file.getPath());
      return null;
    } finally {
      long elapsed = System.currentTimeMillis() - start;
      LogWrapper.getInstance()
          .v(
              TAG,
              String.format(
                  "Computed hash of %s (%d entries, %d ms elapsed): %s",
                  file.getPath(), entries, elapsed, zipFingerprint));
    }

    return zipFingerprint;
  }

  private static byte[] longToByteArray(long x) {
    ByteBuffer buffer = ByteBuffer.allocate(BYTES_IN_LONG);
    buffer.putLong(x);
    return buffer.array();
  }

  private static byte[] arrayListToByteArray(ArrayList<Byte> list) {
    byte[] result = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) {
      result[i] = list.get(i);
    }
    return result;
  }

  private static boolean developmentModeEnabled() {
    return Boolean.valueOf(getSystemProperty("debug.firebase.appdistro.devmode"));
  }

  @Nullable
  @SuppressWarnings({"unchecked", "PrivateApi"})
  private static String getSystemProperty(String propertyName) {
    String className = "android.os.SystemProperties";
    try {
      Class<?> sysProps = Class.forName(className);
      Method method = sysProps.getDeclaredMethod("get", String.class);
      Object result = method.invoke(null, propertyName);
      if (result != null && String.class.isAssignableFrom(result.getClass())) {
        return (String) result;
      }
    } catch (Exception e) {
      // do nothing
    }
    return null;
  }
}
