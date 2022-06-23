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

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Debug;
import android.os.StatFs;
import android.provider.Settings.Secure;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class CommonUtils {

  private static final String SHA1_INSTANCE = "SHA-1";
  private static final String GOLDFISH = "goldfish";
  private static final String RANCHU = "ranchu";
  private static final String SDK = "sdk";

  public static final String SHARED_PREFS_NAME = "com.google.firebase.crashlytics";
  public static final String LEGACY_SHARED_PREFS_NAME = "com.crashlytics.prefs";

  private static final char[] HEX_VALUES = {
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
  };

  static final String MAPPING_FILE_ID_RESOURCE_NAME =
      "com.google.firebase.crashlytics.mapping_file_id";
  static final String LEGACY_MAPPING_FILE_ID_RESOURCE_NAME = "com.crashlytics.android.build_id";

  private static final long UNCALCULATED_TOTAL_RAM = -1;
  static final int BYTES_IN_A_GIGABYTE = 1073741824;
  static final int BYTES_IN_A_MEGABYTE = 1048576;
  static final int BYTES_IN_A_KILOBYTE = 1024;

  // Caches the result of the total ram calculation, which is expensive, so we only want to
  // perform it once. The value won't change over time, so it's safe to cache.
  private static long totalRamInBytes = UNCALCULATED_TOTAL_RAM;

  // TODO: Maybe move this method into a more appropriate class.
  public static SharedPreferences getSharedPrefs(Context context) {
    return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
  }

  /** Return the pre-Firebase shared prefs for Crashlytics. */
  public static SharedPreferences getLegacySharedPrefs(Context context) {
    return context.getSharedPreferences(LEGACY_SHARED_PREFS_NAME, Context.MODE_PRIVATE);
  }

  /**
   * Utility method to open the given file and extract the field matching fieldname. Assumes each
   * line of the file is formatted "fieldID:value" with an arbitrary amount of whitespace on both
   * sides of the colon. Will return null if the file can't be opened or the field was not found.
   */
  public static String extractFieldFromSystemFile(File file, String fieldname) {
    String toReturn = null;
    if (file.exists()) {

      BufferedReader br = null;
      try {
        br = new BufferedReader(new FileReader(file), 1024);
        String line;
        while ((line = br.readLine()) != null) {
          final Pattern pattern = Pattern.compile("\\s*:\\s*");
          final String[] pieces = pattern.split(line, 2);
          if (pieces.length > 1 && pieces[0].equals(fieldname)) {
            toReturn = pieces[1];

            break;
          }
        }
      } catch (Exception e) {
        Logger.getLogger().e("Error parsing " + file, e);
      } finally {
        CommonUtils.closeOrLog(br, "Failed to close system file reader.");
      }
    }
    return toReturn;
  }

  /**
   * Get Architecture based on Integer order in Protobuf enum
   *
   * @return
   */
  public static int getCpuArchitectureInt() {
    return Architecture.getValue().ordinal();
  }

  /**
   * CPU Architecture enum matching PBbuf in
   * https://github.com/crashlytics/protobuf/blob/master/crashlytics.proto
   */
  enum Architecture {
    X86_32,
    X86_64,
    ARM_UNKNOWN,
    PPC,
    PPC64,
    ARMV6,
    ARMV7,
    UNKNOWN,
    ARMV7S,
    ARM64;

    private static final Map<String, Architecture> matcher = new HashMap<String, Architecture>(4);

    static {
      matcher.put("armeabi-v7a", ARMV7);
      matcher.put("armeabi", ARMV6);
      matcher.put("arm64-v8a", ARM64);
      matcher.put("x86", X86_32);
    }

    /** @Return {@link CommonUtils.Architecture} enum based on @param String */
    static Architecture getValue() {
      String arch = Build.CPU_ABI;

      if (TextUtils.isEmpty(arch)) {
        Logger.getLogger().v("Architecture#getValue()::Build.CPU_ABI returned null or empty");
        return UNKNOWN;
      }

      arch = arch.toLowerCase(Locale.US);
      Architecture value = matcher.get(arch);
      if (value == null) {
        value = UNKNOWN;
      }
      return value;
    }
  }

  /**
   * Returns the total ram of the device, in bytes, as read from the /proc/meminfo file. No API call
   * exists to get this value in API level 7.
   */
  public static synchronized long getTotalRamInBytes() {
    if (totalRamInBytes == UNCALCULATED_TOTAL_RAM) {
      long bytes = 0;
      String result = extractFieldFromSystemFile(new File("/proc/meminfo"), "MemTotal");

      if (!TextUtils.isEmpty(result)) {
        result = result.toUpperCase(Locale.US);

        try {
          if (result.endsWith("KB")) {
            bytes = convertMemInfoToBytes(result, "KB", BYTES_IN_A_KILOBYTE);
          } else if (result.endsWith("MB")) {
            // It is uncertain that this notation would ever be returned, but we'll
            // leave in this handling since we don't know for certain it isn't.
            bytes = convertMemInfoToBytes(result, "MB", BYTES_IN_A_MEGABYTE);
          } else if (result.endsWith("GB")) {
            // It is uncertain that this notation would ever be returned, but we'll
            // leave in this handling since we don't know for certain it isn't.
            bytes = convertMemInfoToBytes(result, "GB", BYTES_IN_A_GIGABYTE);
          } else {
            Logger.getLogger().w("Unexpected meminfo format while computing RAM: " + result);
          }
        } catch (NumberFormatException e) {
          Logger.getLogger().e("Unexpected meminfo format while computing RAM: " + result, e);
        }
      }
      totalRamInBytes = bytes;
    }
    return totalRamInBytes;
  }

  /**
   * Converts a meminfo value String with associated size notation into bytes by stripping the
   * provided unit notation and applying the provided multiplier.
   */
  static long convertMemInfoToBytes(String memInfo, String notation, int notationMultiplier) {
    return Long.parseLong(memInfo.split(notation)[0].trim()) * notationMultiplier;
  }

  /**
   * Returns the RunningAppProcessInfo object for the given package, or null if it cannot be found.
   */
  public static ActivityManager.RunningAppProcessInfo getAppProcessInfo(
      String packageName, Context context) {
    final ActivityManager actman =
        (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    final List<ActivityManager.RunningAppProcessInfo> processes = actman.getRunningAppProcesses();
    ActivityManager.RunningAppProcessInfo procInfo = null;
    // According to docs, the result of getRunningAppProcesses can be null instead of empty.
    // Yay.
    if (processes != null) {
      for (ActivityManager.RunningAppProcessInfo info : processes) {
        if (info.processName.equals(packageName)) {
          procInfo = info;
          break;
        }
      }
    }
    return procInfo;
  }

  public static String streamToString(InputStream is) {
    // Previous code was running into this: http://code.google.com/p/android/issues/detail?id=14562
    // on Android 2.3.3. The below code below does not exhibit that problem.
    final java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

  public static String sha1(String source) {
    return hash(source, SHA1_INSTANCE);
  }

  private static String hash(String s, String algorithm) {
    return hash(s.getBytes(), algorithm);
  }

  private static String hash(byte[] bytes, String algorithm) {
    MessageDigest digest = null;

    try {
      digest = java.security.MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException e) {
      Logger.getLogger()
          .e("Could not create hashing algorithm: " + algorithm + ", returning empty string.", e);
      return "";
    }
    // :TODO: Need to specify character encoding??
    // may not matter, since it will always give consistent output for
    // the given input on a given device, which is good enough for our ID purposes.

    digest.update(bytes);

    return hexify(digest.digest());
  }

  /**
   * Returns an ID suitable for identifying a code release, constructed from the "slice" IDs passed
   * in. Returns <code>null</code> if the input is <code>null</code> or an empty array.
   */
  public static String createInstanceIdFrom(String... sliceIds) {
    // We'll return null if we don't have any IDs to work with
    if (sliceIds == null || sliceIds.length == 0) {
      return null;
    }

    // We need to remove null values from the array, so create new list to hold the filtered IDs
    final List<String> sliceIdList = new ArrayList<String>();

    for (String id : sliceIds) {
      // Skip null values in the array
      if (id != null) {
        // Strip dashes and lowercase to normalize
        sliceIdList.add(id.replace("-", "").toLowerCase(Locale.US));
      }
    }

    // Sort into canonical ordering
    Collections.sort(sliceIdList);

    // Concatenate them all together to get a single value to hash
    final StringBuilder sb = new StringBuilder();
    for (String id : sliceIdList) {
      sb.append(id);
    }

    final String concatValue = sb.toString();

    // SHA1 the sorted, concatenated String of slice IDs to get the instance ID, or if we have
    // no appended value, return null.
    return (concatValue.length() > 0) ? sha1(concatValue) : null;
  }

  /**
   * Calculates and returns the amount of free RAM in bytes.
   *
   * @param context used to acquire the necessary resources for calculating free RAM
   * @return Amount of free RAM in bytes
   */
  public static long calculateFreeRamInBytes(Context context) {
    final MemoryInfo mi = new MemoryInfo();
    ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(mi);
    return mi.availMem;
  }

  /**
   * Calculates and returns the amount of used disk space in bytes at the specified path.
   *
   * @param path Filesystem path at which to make the space calculations
   * @return Amount of disk space used, in bytes
   */
  public static long calculateUsedDiskSpaceInBytes(String path) {
    final StatFs statFs = new StatFs(path);
    final long blockSizeBytes = statFs.getBlockSize();
    final long totalSpaceBytes = blockSizeBytes * statFs.getBlockCount();
    final long availableSpaceBytes = blockSizeBytes * statFs.getAvailableBlocks();
    return totalSpaceBytes - availableSpaceBytes;
  }

  public static boolean getProximitySensorEnabled(Context context) {
    if (isEmulator()) {
      // For whatever reason, accessing the sensor manager locks up the emulator. Just return
      // false since it's doesn't really have one anyway.
      return false;
    } else {
      final SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
      final Sensor prox = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
      return prox != null;
    }
  }

  /** @deprecated This method will now always return false. It should not be used. */
  @Deprecated
  public static boolean isLoggingEnabled(Context context) {
    return false;
  }

  /**
   * Gets a value for a boolean resource by its name. If a key is not present, the provided default
   * value will be returned.
   *
   * <p>Tries to look up a boolean value two ways:
   *
   * <ol>
   *   <li>As a <code>bool</code> resource. A discovered value is returned as-is.
   *   <li>As a <code>string</code> resource. A discovered value is turned into a boolean with
   *       {@link Boolean#parseBoolean(String)} before being returned.
   * </ol>
   *
   * @param context {@link Context} to use when accessing resources
   * @param key {@link String} name of the boolean value to look up
   * @param defaultValue value to be returned if the specified resource could be not be found.
   * @return {@link String} value of the specified property, or an empty string if it could not be
   *     found.
   */
  public static boolean getBooleanResourceValue(Context context, String key, boolean defaultValue) {
    if (context != null) {
      final Resources resources = context.getResources();

      if (resources != null) {
        int id = getResourcesIdentifier(context, key, "bool");

        if (id > 0) {
          return resources.getBoolean(id);
        }

        id = getResourcesIdentifier(context, key, "string");

        if (id > 0) {
          return Boolean.parseBoolean(context.getString(id));
        }
      }
    }

    return defaultValue;
  }

  public static int getResourcesIdentifier(Context context, String key, String resourceType) {
    final Resources resources = context.getResources();
    return resources.getIdentifier(key, resourceType, getResourcePackageName(context));
  }

  /**
   * Checks via some common methods if we are running on an Android emulator.
   *
   * @return boolean value indicating that we are or are not running in an emulator.
   */
  public static boolean isEmulator() {
    // TODO(mrober): Consider other heuristics to decide if we are running on an Android emulator.
    return Build.PRODUCT.contains(SDK)
        || Build.HARDWARE.contains(GOLDFISH)
        || Build.HARDWARE.contains(RANCHU);
  }

  public static boolean isRooted() {
    // No reliable way to determine if an android phone is rooted, since a rooted phone could
    // always disguise itself as non-rooted. Some common approaches can be found on SO:
    //   http://stackoverflow.com/questions/1101380/determine-if-running-on-a-rooted-device
    //
    // http://stackoverflow.com/questions/3576989/how-can-you-detect-if-the-device-is-rooted-in-the-app
    //
    // http://stackoverflow.com/questions/7727021/how-can-androids-copy-protection-check-if-the-device-is-rooted
    final boolean isEmulator = isEmulator();
    final String buildTags = Build.TAGS;
    if (!isEmulator && buildTags != null && buildTags.contains("test-keys")) {
      return true;
    }

    // Superuser.apk would only exist on a rooted device:
    File file = new File("/system/app/Superuser.apk");
    if (file.exists()) {
      return true;
    }

    // su is only available on a rooted device (or the emulator)
    // The user could rename or move to a non-standard location, but in that case they
    // probably don't want us to know they're root and they can pretty much subvert
    // any check anyway.
    file = new File("/system/xbin/su");
    if (!isEmulator && file.exists()) {
      return true;
    }
    return false;
  }

  public static boolean isDebuggerAttached() {
    return Debug.isDebuggerConnected() || Debug.waitingForDebugger();
  }

  public static final int DEVICE_STATE_ISSIMULATOR = 1 << 0;
  public static final int DEVICE_STATE_JAILBROKEN = 1 << 1;
  public static final int DEVICE_STATE_DEBUGGERATTACHED = 1 << 2;
  // Beta, Vendor Internal and Compromised Libraries are currently not implemented.
  public static final int DEVICE_STATE_BETAOS = 1 << 3;
  public static final int DEVICE_STATE_VENDORINTERNAL = 1 << 4;
  public static final int DEVICE_STATE_COMPROMISEDLIBRARIES = 1 << 5;

  public static int getDeviceState() {
    int deviceState = 0;
    if (CommonUtils.isEmulator()) {
      deviceState |= DEVICE_STATE_ISSIMULATOR;
    }

    if (CommonUtils.isRooted()) {
      deviceState |= DEVICE_STATE_JAILBROKEN;
    }

    if (CommonUtils.isDebuggerAttached()) {
      deviceState |= DEVICE_STATE_DEBUGGERATTACHED;
    }

    return deviceState;
  }

  /** Returns a hex string for the given byte array. */
  public static String hexify(byte[] bytes) {
    final char[] hexChars = new char[bytes.length * 2];
    int v;
    for (int i = 0; i < bytes.length; i++) {
      v = bytes[i] & 0xFF;
      hexChars[i * 2] = HEX_VALUES[v >>> 4];
      hexChars[i * 2 + 1] = HEX_VALUES[v & 0x0F];
    }
    return new String(hexChars);
  }

  /**
   * Returns true if the app's manifest includes android:debuggable=true. Eclipse and ant inject
   * this property into the manifest automatically for debug builds.
   */
  public static boolean isAppDebuggable(Context context) {
    return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
  }

  /**
   * Gets values for string properties in the strings.xml file by its name. If a key is not present,
   * an empty String is returned.
   *
   * @param context {@link Context} to use when accessing resources
   * @param key {@link String} name of the string value to look up
   * @return {@link String} value of the specified property, or an empty string if it could not be
   *     found.
   */
  public static String getStringsFileValue(Context context, String key) {
    final int id = getResourcesIdentifier(context, key, "string");

    if (id > 0) {
      return context.getString(id);
    }

    return "";
  }

  /**
   * Closes a {@link Closeable}, ignoring any {@link IOException}s raised in the process. Does
   * nothing if the {@link Closeable} is <code>null</code>.
   *
   * @param c {@link Closeable} to close
   */
  public static void closeOrLog(Closeable c, String message) {
    if (c != null) {
      try {
        c.close();
      } catch (IOException e) {
        Logger.getLogger().e(message, e);
      }
    }
  }

  /**
   * Returns a String that is exactly 10 characters long, where the leftmost digits have been padded
   * with zeros. It is 10 characters to match the {@link String} length of the maximum int value.
   * Negative values are not handled, and will throw an exception.
   *
   * @throws IllegalArgumentException if value is negative
   */
  public static String padWithZerosToMaxIntWidth(int value) {
    if (value < 0) {
      throw new IllegalArgumentException("value must be zero or greater");
    }
    // This formatter pads the int value with spaces on the left until it reaches 10 characters
    // in width, then the spaces are replaced with zeros.
    return String.format(Locale.US, "%1$10s", value).replace(' ', '0');
  }

  /**
   * Get the package name associated with the resources for this context. This is typically the same
   * as context.getPackageName(). It can differ in unusual build scenarios, such as when using
   * aapt's --rename-manifest-package parameter.
   *
   * @param context Context to get resource package name from
   * @return String representing the package name of the resources for the given context
   */
  public static String getResourcePackageName(Context context) {
    // The return value should be cached as it cannot change during app execution,
    // but it would require a major refactor to be testable. We should consider it for the
    // upcoming SDK rewrite.
    String resourcePackageName;

    // Pick a resource that is likely to exist: the app's icon.
    final int iconId = context.getApplicationContext().getApplicationInfo().icon;
    if (iconId > 0) {
      try {
        resourcePackageName = context.getResources().getResourcePackageName(iconId);
        // Apps using built-in icons will end up with the "android" resource package,
        // which is definitely not what we want. Default to context.getPackageName().
        if ("android".equals(resourcePackageName)) {
          resourcePackageName = context.getPackageName();
        }
      } catch (Resources.NotFoundException e) {
        // If we get here, the app has a valid icon, but it isn't in the resources for this
        // context. This can happen if the app uses a default system icon, for example.
        // In this case, we'll hope that context.getPackageName() is accurate.
        // (See b/122825635 for a note about whether or not this check makes sense for
        // future versions of the SDK, or if we should just use context.getPackageName()
        // in all cases.) Logging info or a warning here would be nice, but this method is
        // invoked many times throughout application startup.
        resourcePackageName = context.getPackageName();
      }
    } else {
      // If there's no icon, or something went wrong with the one we found, we'll hope that
      // context.getPackageName() is sufficient for this app.
      resourcePackageName = context.getPackageName();
    }
    return resourcePackageName;
  }

  public static String getMappingFileId(Context context) {
    // TODO: This functionality should be refactored into an InstanceIdProvider class or similar.
    String mappingFileId = null;
    int id = CommonUtils.getResourcesIdentifier(context, MAPPING_FILE_ID_RESOURCE_NAME, "string");

    if (id == 0) {
      // If we didn't find it, check for the resource injected by the legacy build tools.
      id =
          CommonUtils.getResourcesIdentifier(
              context, LEGACY_MAPPING_FILE_ID_RESOURCE_NAME, "string");
    }

    if (id != 0) {
      mappingFileId = context.getResources().getString(id);
    }
    return mappingFileId;
  }

  public static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (RuntimeException rethrown) {
        throw rethrown;
      } catch (Exception ignored) {
      }
    }
  }

  /** @return if the given permission is granted */
  public static boolean checkPermission(Context context, String permission) {
    final int res = context.checkCallingOrSelfPermission(permission);
    return (res == PackageManager.PERMISSION_GRANTED);
  }

  /**
   * Returns current connection state if android.permission.ACCESS_NETWORK_STATE is granted Defaults
   * to true if permission is not granted
   */
  @SuppressLint("MissingPermission")
  public static boolean canTryConnection(Context context) {
    if (checkPermission(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
      final ConnectivityManager cm =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

      final NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
      final boolean isConnected = activeNetwork != null && activeNetwork.isConnectedOrConnecting();
      return isConnected;
    } else {
      return true;
    }
  }

  /** @return true if s1.equals(s2), or if both are null. */
  public static boolean nullSafeEquals(@Nullable String s1, @Nullable String s2) {
    // :TODO: replace calls to this method with Objects.equals(...) when minSdkVersion is 19+
    if (s1 == null) {
      return s2 == null;
    }
    return s1.equals(s2);
  }
}
