package com.google.firebase.database;

import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class DatabaseRegistrarTest {
  @Test
  public void databaseRegistrar_getComponents_publishesLibVersionComponent() {
    TestUserAgentDependentComponent userAgentDependant =
        FirebaseDatabase.getInstance().getApp().get(TestUserAgentDependentComponent.class);

    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");

    assertThat(arrayElementContains(actualUserAgent, "firebase-database")).isTrue();
  }

  private boolean arrayElementContains(String[] array, String str) {
    for (String s : array) {
      if (s.contains(str)) {
        return true;
      }
    }
    return false;
  }
}
