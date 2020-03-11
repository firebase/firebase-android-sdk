// Copyright 2020 Google LLC
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

import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import java.io.File;
import java.io.IOException;

/**
 * Helper class which handles writing, checking for, and deleting a marker file. The location of the
 * file is lazy-loaded from the passed-in <code>FileStore</code>.
 */
class CrashlyticsFileMarker {

  private final String markerName;
  private final FileStore fileStore;

  /**
   * Creates a new CrashlyticsMarker instance which will manage a file with the given name in the
   * directory provided by the given FileStore.
   *
   * @param markerName the name of the marker file.
   * @param fileStore the FileStore which will provide the directory for the marker file.
   */
  public CrashlyticsFileMarker(String markerName, FileStore fileStore) {
    this.markerName = markerName;
    this.fileStore = fileStore;
  }

  /**
   * Create the marker file.
   *
   * @return <code>true</code> if the marker file was properly set, or <code>false</code> if not.
   */
  public boolean create() {
    boolean wasCreated = false;
    try {
      wasCreated = getMarkerFile().createNewFile();
    } catch (IOException e) {
      Logger.getLogger().e("Error creating marker: " + markerName, e);
    }
    return wasCreated;
  }

  /**
   * Check if the marker file is set.
   *
   * @return whether the marker file is set.
   */
  public boolean isPresent() {
    return getMarkerFile().exists();
  }

  /**
   * Delete the marker file.
   *
   * @return <code>true</code> if the marker file was properly removed, or <code>false</code> if
   *     not.
   */
  public boolean remove() {
    return getMarkerFile().delete();
  }

  private File getMarkerFile() {
    return new File(fileStore.getFilesDir(), markerName);
  }
}
