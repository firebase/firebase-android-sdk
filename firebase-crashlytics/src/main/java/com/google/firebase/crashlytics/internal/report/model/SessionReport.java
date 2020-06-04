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

package com.google.firebase.crashlytics.internal.report.model;

import com.google.firebase.crashlytics.internal.Logger;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable {@link Report} object */
public class SessionReport implements Report {
  private final File file;
  private final File[] files;
  private final Map<String, String> customHeaders;

  public SessionReport(File file) {
    this(file, Collections.<String, String>emptyMap());
  }

  /** Creates a session report to be sent with the given custom HTTP headers. */
  public SessionReport(File file, Map<String, String> customHeaders) {
    this.file = file;
    this.files = new File[] {file};
    this.customHeaders = new HashMap<>(customHeaders);
  }

  public File getFile() {
    return file;
  }

  public File[] getFiles() {
    return files;
  }

  @Override
  public String getFileName() {
    return getFile().getName();
  }

  @Override
  public String getIdentifier() {
    final String fileName = getFileName();
    return fileName.substring(0, fileName.lastIndexOf('.'));
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return Collections.unmodifiableMap(customHeaders);
  }

  @Override
  public void remove() {
    Logger.getLogger().d("Removing report at " + file.getPath());
    file.delete();
  }

  @Override
  public Type getType() {
    return Type.JAVA;
  }
}
