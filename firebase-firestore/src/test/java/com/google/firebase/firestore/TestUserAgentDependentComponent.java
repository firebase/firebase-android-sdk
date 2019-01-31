package com.google.firebase.firestore;

import com.google.firebase.platforminfo.UserAgentPublisher;

public class TestUserAgentDependentComponent {
  private final UserAgentPublisher userAgentPublisher;

  public TestUserAgentDependentComponent(UserAgentPublisher userAgentPublisher) {
    this.userAgentPublisher = userAgentPublisher;
  }

  public UserAgentPublisher getUserAgentPublisher() {
    return userAgentPublisher;
  }
}
