package com.google.firebase.appcheck.recaptchaenterprise;

import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.recaptchaenterprise.internal.RecaptchaEnterpriseAppCheckProvider;

/**
 * Implementation of an {@link AppCheckProviderFactory} that builds <br>
 * {@link RecaptchaEnterpriseAppCheckProvider}s. This is the default implementation.
 */
public class RecaptchaEnterpriseAppCheckProviderFactory implements AppCheckProviderFactory {

  private static volatile RecaptchaEnterpriseAppCheckProviderFactory instance;
  private static String siteKey;

  /** Gets an instance of this class for installation into a {@link FirebaseAppCheck} instance. */
  @NonNull
  public static RecaptchaEnterpriseAppCheckProviderFactory getInstance(@NonNull String siteKey) {
    if (instance == null) {
      synchronized (RecaptchaEnterpriseAppCheckProviderFactory.class) {
        if (instance == null) {
          instance = new RecaptchaEnterpriseAppCheckProviderFactory();
          RecaptchaEnterpriseAppCheckProviderFactory.siteKey = siteKey;
        }
      }
    }
    return instance;
  }

  @NonNull
  public static String getSiteKey() {
    return siteKey;
  }

  @NonNull
  @Override
  @SuppressWarnings("FirebaseUseExplicitDependencies")
  public AppCheckProvider create(@NonNull FirebaseApp firebaseApp) {
    return firebaseApp.get(RecaptchaEnterpriseAppCheckProvider.class);
  }
}
