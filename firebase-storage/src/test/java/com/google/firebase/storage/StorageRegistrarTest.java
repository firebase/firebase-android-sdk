package com.google.firebase.storage;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static com.google.common.truth.Truth.assertThat;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
public class StorageRegistrarTest {
  @Test
  public void storageRegistrar_getComponents_publishesLibVersionComponent() {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            RuntimeEnvironment.application.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:196403931065:android:60949756fbe381ea")
                .build());
    TestUserAgentDependentComponent userAgentDependant =
        FirebaseStorage.getInstance(app).getApp().get(TestUserAgentDependentComponent.class);

    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String actualUserAgent = userAgentPublisher.getUserAgent();

    assertThat(actualUserAgent).contains("firebase-storage");
  }
}
