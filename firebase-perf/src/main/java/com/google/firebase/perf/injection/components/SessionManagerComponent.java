package com.google.firebase.perf.injection.components;

import androidx.annotation.NonNull;

import com.google.firebase.perf.injection.modules.SessionManagerModule;
import com.google.firebase.perf.session.SessionManager;

import javax.inject.Singleton;

import dagger.Component;

@Component(modules = {SessionManagerModule.class})
@Singleton
public interface SessionManagerComponent {
  @NonNull
  SessionManager getSessionManager();
}
