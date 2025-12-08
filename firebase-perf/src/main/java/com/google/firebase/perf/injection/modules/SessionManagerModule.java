package com.google.firebase.perf.injection.modules;

import androidx.annotation.NonNull;

import com.google.firebase.perf.session.SessionManager;

import dagger.Module;
import dagger.Provides;

@Module
public class SessionManagerModule {
  private final SessionManager sessionManager;

  public SessionManagerModule(@NonNull SessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  @Provides
  SessionManager providesSessionManager() {
    return sessionManager;
  }
}
