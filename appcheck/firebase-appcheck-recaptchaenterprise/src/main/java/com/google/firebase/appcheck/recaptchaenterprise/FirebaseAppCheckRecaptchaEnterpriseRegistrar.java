package com.google.firebase.appcheck.recaptchaenterprise;

import android.app.Application;
import android.content.Context;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.recaptchaenterprise.internal.FirebaseExecutors;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * {@link ComponentRegistrar} for setting up FirebaseAppCheck reCAPTCHA Enterprise's dependency
 * injections in Firebase Android Components.
 *
 * @hide
 */
@KeepForSdk
public class FirebaseAppCheckRecaptchaEnterpriseRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-app-check-recaptcha-enterprise";

  @Override
  public List<Component<?>> getComponents() {
    Qualified<Executor> liteExecutor = Qualified.qualified(Lightweight.class, Executor.class);
    Qualified<Executor> blockingExecutor = Qualified.qualified(Blocking.class, Executor.class);

    return Arrays.asList(
        Component.builder(Application.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(Context.class))
            .factory(
                container -> {
                  Context context = container.get(Context.class);
                  return (Application) context.getApplicationContext();
                })
            .build(),
        Component.builder(FirebaseExecutors.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(liteExecutor))
            .add(Dependency.required(blockingExecutor))
            .factory(
                container ->
                    new FirebaseExecutors(
                        container.get(liteExecutor), container.get(blockingExecutor)))
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }
}
