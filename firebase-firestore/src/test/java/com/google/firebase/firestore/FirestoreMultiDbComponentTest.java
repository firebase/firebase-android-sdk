// Copyright 2020 Google LLC
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

package com.google.firebase.firestore;

import static org.junit.Assert.assertNotSame;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.InteropAppCheckTokenProvider;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.firestore.remote.GrpcMetadataProvider;
import com.google.firebase.firestore.testutil.ImmediateDeferred;
import com.google.firebase.inject.Deferred;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class FirestoreMultiDbComponentTest {

  @Test
  public void onDeleted_shouldRemoveAllInstances() {
    FirebaseApp firebaseApp = createApp("onDeleted-shouldRemoveAllInstances");
    FirestoreMultiDbComponent component = createComponent(firebaseApp);
    FirebaseFirestore firebaseFirestore1 = component.get("db1");
    FirebaseFirestore firebaseFirestore2 = component.get("db2");

    component.onDeleted(firebaseApp.getName(), firebaseApp.getOptions());

    assertNotSame(firebaseFirestore1, component.get("db1"));
    assertNotSame(firebaseFirestore2, component.get("db2"));
  }

  private static FirebaseApp createApp(String appName) {
    FirebaseOptions firebaseOptions =
        new FirebaseOptions.Builder().setProjectId("project-id").setApplicationId("app-id").build();
    FirebaseApp app = mock(FirebaseApp.class);
    when(app.getOptions()).thenReturn(firebaseOptions);
    when(app.getName()).thenReturn(appName);
    return app;
  }

  private static FirestoreMultiDbComponent createComponent(FirebaseApp firebaseApp) {
    Context context = InstrumentationRegistry.getInstrumentation().getContext();
    InternalAuthProvider authProvider = mock(InternalAuthProvider.class);
    Deferred<InternalAuthProvider> deferredAuthProvider = new ImmediateDeferred<>(authProvider);
    InteropAppCheckTokenProvider mockInternalAppCheckProvider =
        mock(InteropAppCheckTokenProvider.class);
    AppCheckTokenResult mockAppCheckTokenResult = mock(AppCheckTokenResult.class);
    when(mockAppCheckTokenResult.getToken()).thenReturn("TestAppCheckToken");
    doReturn(Tasks.forResult(mockAppCheckTokenResult))
        .when(mockInternalAppCheckProvider)
        .getToken(anyBoolean());
    Deferred<InteropAppCheckTokenProvider> deferredAppCheckTokenProvider =
        new ImmediateDeferred<>(mockInternalAppCheckProvider);
    GrpcMetadataProvider metadataProvider = mock(GrpcMetadataProvider.class);
    return new FirestoreMultiDbComponent(
        context,
        firebaseApp,
        deferredAuthProvider,
        deferredAppCheckTokenProvider,
        metadataProvider);
  }
}
