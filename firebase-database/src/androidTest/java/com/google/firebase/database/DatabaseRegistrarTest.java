package com.google.firebase.database;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;

public class DatabaseRegistrarTest {
  @Test
  public void databaseRegistrar_getComponents_publishesLibVersionComponent() {
    TestUserAgentDependentComponent userAgentDependant =
        FirebaseDatabase.getInstance().getApp().get(TestUserAgentDependentComponent.class);

    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String actualUserAgent = userAgentPublisher.getUserAgent();

    assertThat(actualUserAgent).contains("firebase-database");
  }
}
