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
import java.util.Map;

public class NativeSessionReport implements Report {

  private final File reportDirectory;

  public NativeSessionReport(File reportDirectory) {
    this.reportDirectory = reportDirectory;
  }

  @Override
  @SuppressWarnings("all") // result of .delete is ignored
  public void remove() {
    for (File file : getFiles()) {
      Logger.getLogger().d("Removing native report file at " + file.getPath());
      file.delete();
    }
    Logger.getLogger().d("Removing native report directory at " + reportDirectory);
    reportDirectory.delete();
  }

  @Override
  public String getFileName() {
    return null;
  }

  @Override
  public String getIdentifier() {
    return reportDirectory.getName();
  }

  @Override
  public File getFile() {
    return null;
  }

  @Override
  public File[] getFiles() {
    return reportDirectory.listFiles();
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return null;
  }

  @Override
  public Type getType() {
    return Type.NATIVE;
  }
}
