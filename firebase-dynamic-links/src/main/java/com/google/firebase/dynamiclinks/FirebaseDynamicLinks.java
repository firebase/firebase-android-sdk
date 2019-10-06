// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.dynamiclinks;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;

/**
 * Provides access to dynamic links that are received by an app at launch.
 *
 * <p>When a dynamic link is clicked, the app is launched, or if the app is not yet installed, the
 * user is directed to the Play Store to install and launch the app. In both cases the dynamic link
 * made available to the app using {@link #getDynamicLink(Intent)}. An {@link
 * android.content.IntentFilter} for the deeplink can also be used to launch the app directly into a
 * targeted {@link android.app.Activity} or otherwise will start in the main launch Activity.
 *
 * <p>Dynamic link data returned from {@link #getDynamicLink(Intent)} can be accessed using {@link
 * PendingDynamicLinkData} class.
 *
 * <p><a href="https://developer.android.com/training/app-links/index.html">Android App Links</a>
 * can also be used to launch the app with dynamic links by registering to handle your Dynamic Links
 * in your app. The guide for setting up your app to receive Firebase Dynamic Links as an App Link
 * can be found on the Android <a
 * href="https://firebase.google.com/docs/dynamic-links/android/receive#app_links">Firebase Dynamic
 * Links</a> site.
 *
 * <p>Dynamic link data is available from the app launch intent. This data may include data for
 * dynamic link extensions such as app invites.
 */
public abstract class FirebaseDynamicLinks {

  /**
   * Returns an instance of {@link FirebaseDynamicLinks}.
   *
   * <p>The default {@link FirebaseApp} instance must have been initialized before this function is
   * called. See <a
   * href="https://firebase.google.com/docs/reference/android/com/google/firebase/FirebaseApp">
   * FirebaseApp</a>.
   */
  @NonNull
  public static synchronized FirebaseDynamicLinks getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  @NonNull
  public static synchronized FirebaseDynamicLinks getInstance(@NonNull FirebaseApp firebaseApp) {
    return firebaseApp.get(FirebaseDynamicLinks.class);
  }

  /**
   * Determine if the app has a pending dynamic link and provide access to the dynamic link
   * parameters. A pending dynamic link may have been previously captured when a user clicked on a
   * dynamic link, or may be present in the intent.
   *
   * <p>When a dynamic link is clicked by the user, in most cases it is captured when clicked and
   * stored until accessed by {@link #getDynamicLink(Intent)} and returned as the {@link
   * PendingDynamicLinkData} of the {@link Task}. If the dynamic link was not captured, as is the
   * case when App Links launches launches the app, then the dynamic link is provided in the {@link
   * Intent#getData()}. The intent data is then processed to retrieve the dynamic link data. If the
   * dynamic links is both captured and is present in the intent, then the captured data will take
   * precedence. The captured data will be removed after first access.
   *
   * <p>The intent parameter should be the intent that launched the application, or can be null if
   * the intent does not include the dynamic link. A non-null intent is necessary only when the app
   * is launched directly using the dynamic link, such as when using <a
   * href="https://developer.android.com/training/app-links/index.html">App Links</a>. The app must
   * configure an {@link android.content.IntentFilter} to override the default capture processing
   * when the link is clicked.
   *
   * <p>In the callback the {@link PendingDynamicLinkData} is returned in {@link
   * Task#addOnSuccessListener(OnSuccessListener)} or {@link Task#addOnCompleteListener(Activity,
   * OnCompleteListener)} which returns the most recently clicked dynamic link, or null if a dynamic
   * link was not pending as captured data or in the intent.
   *
   * <p>If processing could not be completed due to an error, then {@link OnFailureListener} will be
   * called. Notice that in the case a pending dynamic link is not present, then {@link
   * Task#isSuccessful()} will be true and the returned {@link PendingDynamicLinkData} will be null
   * as this is normal processing and not an error condition.
   *
   * <p>If a dynamic link, the call will also send FirebaseAnalytics dynamic link event.
   */
  @NonNull
  public abstract Task<PendingDynamicLinkData> getDynamicLink(@NonNull Intent intent);

  /**
   * Determine if the app has a pending dynamic link and provide access to the dynamic link
   * parameters. A pending dynamic link may have been previously captured when a user clicked on a
   * dynamic link, or may be present in the dynamicLinkUri parameter. If both are present, the
   * previously captured dynamic link will take precedence. The captured data will be removed after
   * first access.
   *
   * <p>This method provides the same functionality as {@link #getDynamicLink(Intent)} except the
   * Uri is provided in place of the {@link Intent}.
   *
   * @param dynamicLinkUri - A uri that may be a dynamic link.
   * @return Task where {@link Task#isSuccessful()} is true when processing is completed
   *     successfully and either a dynamic link is returned, or null if a dynamic link is not
   *     previously captured or is in the Uri.
   *     <p>{@link Task#isSuccessful()} will only be false when a processing error occurs.
   */
  @NonNull
  public abstract Task<PendingDynamicLinkData> getDynamicLink(@NonNull Uri dynamicLinkUri);

  /**
   * Create a long or short Dynamic Link.
   *
   * @return Builder to create the Dynamic Link.
   */
  @NonNull
  public abstract DynamicLink.Builder createDynamicLink();
}
