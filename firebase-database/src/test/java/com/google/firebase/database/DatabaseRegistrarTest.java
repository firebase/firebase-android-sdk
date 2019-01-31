package com.google.firebase.database;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
public class DatabaseRegistrarTest {
  private static final String TEST_NAMESPACE = "http://tests.firebaselocal.com:9000";

  @Test
  public void databaseRegistrar_getComponents_publishesLibVersionComponent() {
    FirebaseApp app = appForDatabaseUrl(TEST_NAMESPACE, "test");
    TestUserAgentDependentComponent userAgentDependant =
        FirebaseDatabase.getInstance(app).getApp().get(TestUserAgentDependentComponent.class);

    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String actualUserAgent = userAgentPublisher.getUserAgent();

    assertThat(actualUserAgent).contains("firebase-database");
  }

  private static FirebaseApp appForDatabaseUrl(String url, String name) {
    return FirebaseApp.initializeApp(
        RuntimeEnvironment.application.getApplicationContext(),
        new FirebaseOptions.Builder().setApplicationId("appid").setDatabaseUrl(url).build(),
        name);
  }
}
