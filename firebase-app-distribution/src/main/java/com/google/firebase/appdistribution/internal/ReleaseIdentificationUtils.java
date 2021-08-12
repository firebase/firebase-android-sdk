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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ReleaseIdentificationUtils {
  private static final String TAG = "ReleaseIdentification";

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

  public static String calculateApkZipFingerprint(File file){
    Log.v(TAG, "Calculating release id for ${file.path}");
    long start = System.currentTimeMillis();
    long entries = 0;
    String zipFingerprint = null;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      ArrayList<Byte> checksums = new ArrayList<>();

      ZipFile zis = new ZipFile(file);

      try {
        while(zis.entries().hasMoreElements()) {
          ZipEntry zip = zis.entries().nextElement();
          entries += 1;
          checksums.add(Longs.toByteArray(zip.getCrc()));
        }
      } finally {
        zis.close();
      }
      byte[] checksumByteArray = convertToByteArray(checksums);

      zipFingerprint = digest.digest
          digest.digest(checksumByteArray).fold("", { str, it -> str + "%02x".format(it) })
    } catch (IOException | NoSuchAlgorithmException e) {
      Log.v(TAG, "id calculation failed for ${file.path}");
      return null;
    } finally {
      long elapsed = System.currentTimeMillis() - start;
      if (elapsed > 2 * 1000) {
        Log.v(TAG, "Long id calculation time $elapsed ms and $entries entries for ${file.path}")
      }

      Log.v(TAG,"Finished calculating entries in  ms");
      Log.v(TAG,"${file.path} hashes to $hashValue");
    }

    return zipFingerprint;

  }

  @NonNull
  private static byte[] convertToByteArray(@NonNull ArrayList list) throws IOException {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(list);

      return new bos.toByteArray();
  }
}
