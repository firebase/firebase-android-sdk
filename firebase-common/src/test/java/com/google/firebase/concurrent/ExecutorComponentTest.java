package com.google.firebase.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.FirebaseAppTestUtil.withApp;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.FirebaseOptions;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExecutorComponentTest {
  private static final FirebaseOptions OPTIONS =
      new FirebaseOptions.Builder()
          .setApiKey("myKey")
          .setApplicationId("123")
          .setProjectId("456")
          .build();

  @Test
  public void testThatAllExecutorsAreRegisteredByCommon() {
    withApp(
        "test",
        OPTIONS,
        app -> {
          ExecutorComponent executorComponent = app.get(ExecutorComponent.class);
          // If the component is not null, it means it was able to get all of its required
          // dependencies, otherwise get() would throw.
          assertThat(executorComponent).isNotNull();
        });
  }
}
