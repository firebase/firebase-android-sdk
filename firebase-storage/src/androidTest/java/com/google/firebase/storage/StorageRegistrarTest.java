package com.google.firebase.storage;

import static com.google.common.truth.Truth.assertThat;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StorageRegistrarTest {
  @Test
  public void databaseRegistrar_getComponents_publishesLibVersionComponent() {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            InstrumentationRegistry.getContext(),
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
