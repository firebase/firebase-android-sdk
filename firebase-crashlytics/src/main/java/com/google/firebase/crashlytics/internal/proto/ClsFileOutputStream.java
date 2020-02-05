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

package com.google.firebase.crashlytics.internal.proto;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 * FileOutputStream that writes to a temporary file until the stream is closed, then copies to the
 * file to the "completed" location and deletes the temporary file.
 *
 * <p>This class is useful to guard against the case where a file is left in an
 * incomplete/inconsistent state because an exception occurred while generating the data for the
 * FileOutputStream. If the ClsFileOutputStream's getCompleteFile method is non-null, it is
 * generally safe to assume the file stream was properly closed by the client.
 */
public class ClsFileOutputStream extends FileOutputStream {

  public static final String SESSION_FILE_EXTENSION = ".cls";
  public static final String IN_PROGRESS_SESSION_FILE_EXTENSION = ".cls_temp";

  private final String root;
  private File inProgress;
  private File complete;
  private boolean closed = false;

  public ClsFileOutputStream(String dir, String fileRoot) throws FileNotFoundException {
    this(new File(dir), fileRoot);
  }

  public ClsFileOutputStream(File dir, String fileRoot) throws FileNotFoundException {
    super(new File(dir, fileRoot + IN_PROGRESS_SESSION_FILE_EXTENSION));
    root = dir + File.separator + fileRoot;
    inProgress = new File(root + IN_PROGRESS_SESSION_FILE_EXTENSION);
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    super.flush();
    super.close();

    final File complete = new File(root + SESSION_FILE_EXTENSION);

    if (inProgress.renameTo(complete)) {
      inProgress = null;
      this.complete = complete;
    } else {
      String reason = "";
      if (complete.exists()) {
        reason = " (target already exists)";
      } else if (!inProgress.exists()) {
        reason = " (source does not exist)";
      }
      throw new IOException(
          "Could not rename temp file: " + inProgress + " -> " + complete + reason);
    }
  }

  /**
   * Closes the in-progress stream WITHOUT copying the final to its "completed" location. Does not
   * delete the in-progress file.
   *
   * <p>This method should be called if the client wants to save the progress in the file in case of
   * error without invoking close() and therefore giving the appearance of a valid file.
   */
  public void closeInProgressStream() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    super.flush();
    super.close();
  }

  /**
   * Returns the name of the completed file. Will return null if the file was not written
   * successfully.
   */
  public File getCompleteFile() {
    return complete;
  }

  /**
   * Returns the name of the file as it is being written. This file will be deleted when the stream
   * is closed, and this method will return null if the stream has been successfully closed.
   */
  public File getInProgressFile() {
    return inProgress;
  }

  public static final FilenameFilter TEMP_FILENAME_FILTER =
      new FilenameFilter() {
        @Override
        public boolean accept(File dir, String filename) {
          return filename.endsWith(IN_PROGRESS_SESSION_FILE_EXTENSION);
        }
      };
}
