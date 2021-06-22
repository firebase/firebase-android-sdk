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


package com.google.firebase.appdistribution;

import com.google.android.gms.tasks.Task;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;



public abstract class FirebaseAppDistributionApi  {

    public static FirebaseAppDistributionApi getInstance() {
        return null;
    };

    /**
     * Updates the app to the latest release, if one is available. Returns the release
     * information or an empty Optional if no update is found. Performs the following actions:
     * 1. If tester is not signed in, presents the tester with a Google sign in UI
     * 2. Checks if a newer release is available. If so, presents the tester with a
     * confirmation dialog to begin the download.
     * 3. For APKs, downloads the binary and starts an installation intent.
     * 4. For AABs, directs the tester to the Play app to complete the download and
     * installation.
     */
    public abstract Task<Optional<AppDistributionRelease>> updateToLatestRelease();

    /**
     * Returns an AppDistributionRelease if one is available for the current signed in
     * tester. If no update is found, returns an empty Optional. If tester is not signed in, presents the
     * tester with a Google sign in UI
     */
    public abstract Task<Optional<AppDistributionRelease>> checkForUpdate();

    /**
     * Updates app to the latest release.
     * If the latest release is an APK, downloads the binary and starts an installation
     * If the latest release is an AAB, directs the tester to the Play app to complete the
     * download and installation.
     * @throws an UPDATE_NOT_AVAIALBLE exception if
     * @param updateProgressListener a callback function invoked as the update progresses
     */
    public abstract Task<Void> updateApp(@Nullable UpdateProgressListener updateProgressListener);

    /**
     * Interface to subscribe to status updates on the release update process.
     * Called by updateApp.
     */
    public interface UpdateProgressListener {
        public void onProgressUpdate(UpdateProgress updateProgress);
    }

    public interface UpdateProgress {
        // The number of bytes downloaded so far for the APK.
        //  Returns -1 if called on an AAB.
        public long getApkBytesDownloaded();

        // The total number of bytes to download for the APK.
        // Returns -1 if called on an AAB.
        public long getApkTotalBytesToDownload();

        public UpdateStatus getUpdateStatus();
    }

    public enum UpdateStatus {
        // Update queued but not started
        PENDING,

        // Download in progress
        DOWNLOADING,

        // Download completed
        DOWNLOADED,

        // Download failed
        DOWNLOAD_FAILED,

        // Update installed
        INSTALLED,

        // Installation cancelled
        INSTALL_CANCELED,

        // Installation failed
        INSTALL_FAILED,

        // AAB flow (directed to Play)
        REDIRECTED_TO_PLAY,
    }

    /**
     * Signs in the App Distribution tester. Presents the tester with a Google sign in UI
     */
    public abstract Task<Void> signInTester();

    /**
     * Returns true if the App Distribution tester is signed in
     */
    public abstract boolean isTesterSignedIn();

    /**
     * Signs out the App Distribution tester
     */
    public abstract void signOutTester();

    /**
     * The release information returned by the update check when a new version is available.
     */
    public interface AppDistributionRelease {
        // The short bundle version of this build (example 1.0.0)
        public String getDisplayVersion();

        // The bundle version of this build (example: 123)
        public String getBuildVersion();

        // The release notes for this build
        public String getReleaseNotes();

        // The binary type for this build
        public BinaryType getBinaryType();
    }

    public enum BinaryType {
        AAB, APK
    }
}


