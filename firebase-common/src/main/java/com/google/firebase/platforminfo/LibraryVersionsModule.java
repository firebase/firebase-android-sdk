package com.google.firebase.platforminfo;

import com.google.firebase.components.ComponentRuntime;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.Set;

@Module
public interface LibraryVersionsModule {
  @Provides
  static Set<LibraryVersion> providesLibraryVersions(ComponentRuntime runtime) {
    return runtime.setOf(LibraryVersion.class);
  }

  @Binds
  UserAgentPublisher bindsPublisher(DefaultUserAgentPublisher publisher);
}
