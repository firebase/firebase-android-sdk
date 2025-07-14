package com.google.firebase.appcheck.recaptchaenterprise;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link FirebaseAppCheckRecaptchaEnterpriseRegistrar}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseAppCheckRecaptchaEnterpriseRegistrarTest {
  @Test
  public void testGetComponents() {
    FirebaseAppCheckRecaptchaEnterpriseRegistrar registrar =
        new FirebaseAppCheckRecaptchaEnterpriseRegistrar();
    List<Component<?>> components = registrar.getComponents();
    assertThat(components).isNotEmpty();
    assertThat(components).hasSize(3);
    Component<?> applicationComponent = components.get(0);
    assertThat(applicationComponent.getDependencies())
        .containsExactly(Dependency.required(Context.class));
    assertThat(applicationComponent.isLazy()).isTrue();
  }
}
