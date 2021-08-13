package com.google.firebase.appdistribution;

import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;

class ReleaseUtils {
  static AppDistributionRelease convertToAppDistributionRelease(
      AppDistributionReleaseInternal internalRelease) {
    if (internalRelease == null) {
      return null;
    }
    long versionCode;
    try {
      versionCode = Long.parseLong(internalRelease.getBuildVersion());
    } catch (NumberFormatException e) {
      versionCode = 0;
    }
    return AppDistributionRelease.builder()
        .setVersionCode(versionCode)
        .setDisplayVersion(internalRelease.getDisplayVersion())
        .setReleaseNotes(internalRelease.getReleaseNotes())
        .setBinaryType(internalRelease.getBinaryType())
        .build();
  }
}
