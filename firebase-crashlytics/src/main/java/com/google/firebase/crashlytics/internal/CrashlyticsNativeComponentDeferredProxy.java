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

package com.google.firebase.crashlytics.internal;

import androidx.annotation.NonNull;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport;
import com.google.firebase.crashlytics.internal.model.StaticSessionData;
import com.google.firebase.inject.Deferred;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

public final class CrashlyticsNativeComponentDeferredProxy implements CrashlyticsNativeComponent {

  private static final NativeSessionFileProvider MISSING_NATIVE_SESSION_FILE_PROVIDER =
      new MissingNativeSessionFileProvider();

  private final Deferred<CrashlyticsNativeComponent> deferredNativeComponent;
  private final AtomicReference<CrashlyticsNativeComponent> availableNativeComponent =
      new AtomicReference<>(null);

  public CrashlyticsNativeComponentDeferredProxy(
      Deferred<CrashlyticsNativeComponent> deferredNativeComponent) {
    this.deferredNativeComponent = deferredNativeComponent;

    this.deferredNativeComponent.whenAvailable(
        nativeComponent -> {
          Logger.getLogger().d("Crashlytics native component now available.");
          availableNativeComponent.set(nativeComponent.get());
        });
  }

  @Override
  public boolean hasCrashDataForCurrentSession() {
    CrashlyticsNativeComponent component = availableNativeComponent.get();
    return component != null && component.hasCrashDataForCurrentSession();
  }

  @Override
  public boolean hasCrashDataForSession(@NonNull String sessionId) {
    CrashlyticsNativeComponent component = availableNativeComponent.get();
    return component != null && component.hasCrashDataForSession(sessionId);
  }

  @Override
  public void prepareNativeSession(
      @NonNull String sessionId,
      @NonNull String generator,
      long startedAtSeconds,
      @NonNull StaticSessionData sessionData) {

    Logger.getLogger().v("Deferring native open session: " + sessionId);

    this.deferredNativeComponent.whenAvailable(
        nativeComponent -> {
          nativeComponent
              .get()
              .prepareNativeSession(sessionId, generator, startedAtSeconds, sessionData);
        });
  }

  @NonNull
  @Override
  public NativeSessionFileProvider getSessionFileProvider(@NonNull String sessionId) {
    CrashlyticsNativeComponent component = availableNativeComponent.get();
    return (component == null)
        ? MISSING_NATIVE_SESSION_FILE_PROVIDER
        : component.getSessionFileProvider(sessionId);
  }

  private static final class MissingNativeSessionFileProvider implements NativeSessionFileProvider {

    @Override
    public File getMinidumpFile() {
      return null;
    }

    @Override
    public CrashlyticsReport.ApplicationExitInfo getApplicationExitInto() {
      return null;
    }

    @Override
    public File getBinaryImagesFile() {
      return null;
    }

    @Override
    public File getMetadataFile() {
      return null;
    }

    @Override
    public File getSessionFile() {
      return null;
    }

    @Override
    public File getAppFile() {
      return null;
    }

    @Override
    public File getDeviceFile() {
      return null;
    }

    @Override
    public File getOsFile() {
      return null;
    }
  }
}
