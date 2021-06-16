package com.google.firebase.dynamiclinks.internal;

import androidx.annotation.Keep;
import com.google.android.gms.common.annotation.KeepForSdk;
import com.google.android.gms.common.internal.Hide;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import java.util.Arrays;
import java.util.List;

/**
 * {@link ComponentRegistrar} for FirebaseDynamicLinks.
 *
 * <p>see go/firebase-components-android-integration-guide for more details
 *
 * @hide
 */
@Hide
@KeepForSdk
@Keep
public final class FirebaseDynamicLinkRegistrar implements ComponentRegistrar {

  @Override
  @Keep
  public List<Component<?>> getComponents() {
    Component<FirebaseDynamicLinks> firebaseDynamicLinks =
        Component.builder(FirebaseDynamicLinks.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.optionalProvider(AnalyticsConnector.class))
            .factory(
                container ->
                    new FirebaseDynamicLinksImpl(
                        container.get(FirebaseApp.class),
                        container.getProvider(AnalyticsConnector.class)))
            .build(); // no need for eager init for the Internal component.

    return Arrays.asList(firebaseDynamicLinks);
  }
}
