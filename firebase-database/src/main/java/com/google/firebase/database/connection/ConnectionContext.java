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

package com.google.firebase.database.connection;

import com.google.firebase.database.logging.Logger;
import java.util.concurrent.ScheduledExecutorService;

public class ConnectionContext {
  private final ScheduledExecutorService executorService;
  private final ConnectionAuthTokenProvider authTokenProvider;
  private final Logger logger;
  private final boolean persistenceEnabled;
  private final String clientSdkVersion;
  private final String userAgent;
  private final String applicationId;
  private final String sslCacheDirectory;

  public ConnectionContext(
      Logger logger,
      ConnectionAuthTokenProvider authTokenProvider,
      ScheduledExecutorService executorService,
      boolean persistenceEnabled,
      String clientSdkVersion,
      String userAgent,
      String applicationId,
      String sslCacheDirectory) {
    this.logger = logger;
    this.authTokenProvider = authTokenProvider;
    this.executorService = executorService;
    this.persistenceEnabled = persistenceEnabled;
    this.clientSdkVersion = clientSdkVersion;
    this.userAgent = userAgent;
    this.applicationId = applicationId;
    this.sslCacheDirectory = sslCacheDirectory;
  }

  public Logger getLogger() {
    return this.logger;
  }

  public ConnectionAuthTokenProvider getAuthTokenProvider() {
    return this.authTokenProvider;
  }

  public ScheduledExecutorService getExecutorService() {
    return this.executorService;
  }

  public boolean isPersistenceEnabled() {
    return this.persistenceEnabled;
  }

  public String getClientSdkVersion() {
    return this.clientSdkVersion;
  }

  public String getUserAgent() {
    return this.userAgent;
  }

  public String getSslCacheDirectory() {
    return sslCacheDirectory;
  }

  public String getApplicationId() {
    return applicationId;
  }
}
