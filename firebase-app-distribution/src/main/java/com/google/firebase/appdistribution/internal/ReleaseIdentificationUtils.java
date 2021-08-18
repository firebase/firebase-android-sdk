// Copyright 2021 Google LLC
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

package com.google.firebase.appdistribution.internal;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ReleaseIdentificationUtils {
  private static final String TAG = "ReleaseIdentification";
  private static final int BYTES_IN_LONG = 8;

  @Nullable
  public static String extractInternalAppSharingArtifactId(@NonNull Context appContext) {
    try {
      PackageInfo packageInfo =
          appContext
              .getPackageManager()
              .getPackageInfo(appContext.getPackageName(), PackageManager.GET_META_DATA);
      if (packageInfo.applicationInfo.metaData == null) {
        return null;
      }
      return packageInfo.applicationInfo.metaData.getString("com.android.vending.internal.apk.id");
    } catch (PackageManager.NameNotFoundException e) {
      Log.w(TAG, "Could not extract internal app sharing artifact ID");
      return null;
    }
  }

  @Nullable
  public static String calculateApkInternalCodeHash(@NonNull File file) {
    Log.v(TAG, String.format("Calculating release id for %s", file.getPath()));
    Log.v(TAG, String.format("File size: %d", file.length()));

    long start = System.currentTimeMillis();
    long entries = 0;
    String zipFingerprint = null;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      ArrayList<Byte> checksums = new ArrayList<>();

      // Since calculating the codeHash returned from the release backend is computationally
      // expensive, using existing checksum data from the ZipFile we can quickly calculate
      // an intermediate hash that then gets mapped to the backend's returned release codehash
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
      Log.v(TAG, String.format("id calculation failed for %s", file.getPath()));
      return null;
    } finally {
      long elapsed = System.currentTimeMillis() - start;
      if (elapsed > 2 * 1000) {
        Log.v(
            TAG,
            String.format(
                "Long id calculation time %d ms and %d entries for %s",
                elapsed, entries, file.getPath()));
      }

      Log.v(TAG, String.format("Finished calculating %d entries in %d ms", entries, elapsed));
      Log.v(TAG, String.format("%s hashes to %s", file.getPath(), zipFingerprint));
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
