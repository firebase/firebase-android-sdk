// Copyright 2018 Google LLC
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

package com.google.firebase.database.core;

import com.google.firebase.database.connection.ConnectionContext;
import com.google.firebase.database.connection.HostInfo;
import com.google.firebase.database.connection.PersistentConnection;
import com.google.firebase.database.core.persistence.PersistenceManager;
import com.google.firebase.database.logging.Logger;
import java.io.File;
import java.util.List;

public interface Platform {
  public Logger newLogger(Context ctx, Logger.Level level, List<String> components);

  public EventTarget newEventTarget(Context ctx);

  public RunLoop newRunLoop(Context ctx);

  public PersistentConnection newPersistentConnection(
      Context context,
      ConnectionContext connectionContext,
      HostInfo info,
      PersistentConnection.Delegate delegate);

  public String getUserAgent(Context ctx);

  public String getPlatformVersion();

  public PersistenceManager createPersistenceManager(Context ctx, String firebaseId);

  public File getSSLCacheDirectory();
}
