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

import static com.google.firebase.appdistribution.impl.ReleaseIdentificationUtils.getPackageInfoWithMetadata;

import android.content.Context;
import android.content.pm.PackageInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import java.io.File;
import java.io.IOException;
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

/** Extracts a hash of the installed APK. */
class ApkHashExtractor {

  private static final String TAG = "ApkHashExtractor:";
  private static final int BYTES_IN_LONG = 8;

  private final ConcurrentMap<String, String> cachedApkHashes = new ConcurrentHashMap<>();
  private Context applicationContext;

  ApkHashExtractor(Context applicationContext) {
    this.applicationContext = applicationContext;
  }

  /**
   * Extract the SHA-256 hash of the installed APK.
   *
   * <p>The result is stored in an in-memory cache to avoid computing it repeatedly.
   */
  String extractApkHash() throws FirebaseAppDistributionException {
    PackageInfo metadataPackageInfo = getPackageInfoWithMetadata(applicationContext);
    String installedReleaseApkHash = extractApkHash(metadataPackageInfo);
    if (installedReleaseApkHash == null || installedReleaseApkHash.isEmpty()) {
      throw new FirebaseAppDistributionException(
          "Could not calculate hash of installed APK", Status.UNKNOWN);
    }
    return installedReleaseApkHash;
  }

  @VisibleForTesting
  String extractApkHash(PackageInfo packageInfo) {
    File sourceFile = new File(packageInfo.applicationInfo.sourceDir);

    String key =
        String.format(
            Locale.ENGLISH, "%s.%d", sourceFile.getAbsolutePath(), sourceFile.lastModified());
    if (!cachedApkHashes.containsKey(key)) {
      cachedApkHashes.put(key, calculateApkHash(sourceFile));
    }
    return cachedApkHashes.get(key);
  }

  @Nullable
  String calculateApkHash(@NonNull File file) {
    LogWrapper.getInstance().v(TAG + "Calculating release id for " + file.getPath());
    LogWrapper.getInstance().v(TAG + "File size: " + file.length());

    long start = System.currentTimeMillis();
    long entries = 0;
    String zipFingerprint = null;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      ArrayList<Byte> checksums = new ArrayList<>();

      // Since calculating the codeHash returned from the release backend is computationally
      // expensive, we has the existing checksum data from the ZipFile and compare it to
      // (1) the apk hash returned by the backend, or (2) look up a mapping from the apk zip hash to
      // the
      // full codehash, and compare that to the codehash to the backend
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
      LogWrapper.getInstance().v(TAG + "id calculation failed for " + file.getPath());
      return null;
    } finally {
      long elapsed = System.currentTimeMillis() - start;
      if (elapsed > 2 * 1000) {
        LogWrapper.getInstance()
            .v(
                TAG
                    + String.format(
                        "Long id calculation time %d ms and %d entries for %s",
                        elapsed, entries, file.getPath()));
      }

      LogWrapper.getInstance()
          .v(TAG + String.format("Finished calculating %d entries in %d ms", entries, elapsed));
      LogWrapper.getInstance()
          .v(TAG + String.format("%s hashes to %s", file.getPath(), zipFingerprint));
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
}
