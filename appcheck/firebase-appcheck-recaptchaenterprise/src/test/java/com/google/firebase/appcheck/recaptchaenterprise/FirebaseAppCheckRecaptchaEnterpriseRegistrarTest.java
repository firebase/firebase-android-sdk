package com.google.firebase.appcheck.recaptchaenterprise;

import static com.google.common.truth.Truth.assertThat;

import android.app.Application;
import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.recaptchaenterprise.internal.SiteKey;
import com.google.firebase.components.Component;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import java.util.concurrent.Executor;

/** Tests for {@link FirebaseAppCheckRecaptchaEnterpriseRegistrar}. */
@RunWith(RobolectricTestRunner.class)
public class FirebaseAppCheckRecaptchaEnterpriseRegistrarTest {
  @Test
  public void testGetComponents() {
    FirebaseAppCheckRecaptchaEnterpriseRegistrar registrar =
        new FirebaseAppCheckRecaptchaEnterpriseRegistrar();
    List<Component<?>> components = registrar.getComponents();
    assertThat(components).isNotEmpty();
    assertThat(components).hasSize(4);
    Component<?> applicationComponent = components.get(0);
    assertThat(applicationComponent.getDependencies())
        .containsExactly(Dependency.required(Context.class));
    assertThat(applicationComponent.isLazy()).isTrue();
    Component<?> siteKeyComponent = components.get(1);
    assertThat(siteKeyComponent.isLazy()).isTrue();
    Component<?> appCheckRecaptchaEnterpriseComponent = components.get(2);
    assertThat(appCheckRecaptchaEnterpriseComponent.getDependencies())
        .containsExactly(
            Dependency.required(FirebaseApp.class),
            Dependency.required(Application.class),
            Dependency.required(SiteKey.class),
            Dependency.required(Qualified.qualified(Lightweight.class, Executor.class)),
            Dependency.required(Qualified.qualified(Blocking.class, Executor.class)));
    assertThat(appCheckRecaptchaEnterpriseComponent.isLazy()).isTrue();
  }
}
