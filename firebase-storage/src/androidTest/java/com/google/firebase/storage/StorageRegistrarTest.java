package com.google.firebase.storage;

import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
@RunWith(AndroidJUnit4.class)
public class StorageRegistrarTest {
  @Test
  public void databaseRegistrar_getComponents_publishesLibVersionComponent() {
    FirebaseApp app =
        FirebaseApp.initializeApp(
            InstrumentationRegistry.getContext(),
            new FirebaseOptions.Builder()
                .setApplicationId("1:196403931065:android:60949756fbe381ea")
                .setApiKey("AIzaSyDMAScliyLx7F0NPDEJi1QmyCgHIAODrlU")
                .setStorageBucket("project-5516366556574091405.appspot.com")
                .build());
    TestUserAgentDependentComponent userAgentDependant =
        FirebaseStorage.getInstance(app).getApp().get(TestUserAgentDependentComponent.class);

    UserAgentPublisher userAgentPublisher = userAgentDependant.getUserAgentPublisher();
    String[] actualUserAgent = userAgentPublisher.getUserAgent().split(" ");

    assertThat(arrayElementContains(actualUserAgent, "firebase-storage")).isTrue();
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
