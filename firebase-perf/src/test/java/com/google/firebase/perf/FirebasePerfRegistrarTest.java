// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.Dependency;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.firebase.time.Instant;
import com.google.firebase.time.StartupTime;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class FirebasePerfRegistrarTest {

  @Mock private ComponentContainer container;

  @Before
  public void setUp() {
    initMocks(this);
  }

  @After
  public void tearDownFirebaseApp() {
    FirebaseApp.clearInstancesForTest();
    SessionManager.getInstance()
        .setPerfSession(com.google.firebase.perf.session.PerfSession.create());
  }

  @Test
  public void testGetComponents() {
    FirebasePerfRegistrar firebasePerfRegistrar = new FirebasePerfRegistrar();
    List<Component<?>> components = firebasePerfRegistrar.getComponents();

    // Note: Although we have 3 deps but looks like size doesn't count deps towards interface like
    // FirebaseInstallationsApi
    assertThat(components).hasSize(3);

    Component<?> firebasePerfComponent = components.get(0);

    assertThat(firebasePerfComponent.getDependencies())
        .containsExactly(
            Dependency.required(FirebaseApp.class),
            Dependency.requiredProvider(RemoteConfigComponent.class),
            Dependency.required(FirebaseInstallationsApi.class),
            Dependency.requiredProvider(TransportFactory.class));

    assertThat(firebasePerfComponent.isLazy()).isTrue();
  }

  @Test
  public void tracerComponentFactory_initializesGaugeCollection() {
    com.google.firebase.perf.session.PerfSession mockPerfSession =
        mock(com.google.firebase.perf.session.PerfSession.class);
    when(mockPerfSession.sessionId()).thenReturn("sessionId");
    when(mockPerfSession.isGaugeAndEventCollectionEnabled()).thenReturn(true);
    when(container.get(ArgumentMatchers.eq(Context.class)))
        .thenReturn(ApplicationProvider.getApplicationContext());
    when(container.get(ArgumentMatchers.eq(StartupTime.class)))
        .thenReturn(new StartupTime(Instant.NEVER));

    SessionManager.getInstance().setPerfSession(mockPerfSession);
    String oldSessionId = SessionManager.getInstance().perfSession().sessionId();
    Assert.assertEquals(oldSessionId, SessionManager.getInstance().perfSession().sessionId());

    FirebasePerfRegistrar.providesFirebasePerfInternalTracer(container);

    Assert.assertEquals(oldSessionId, SessionManager.getInstance().perfSession().sessionId());
    verify(mockPerfSession, times(2)).isGaugeAndEventCollectionEnabled();
  }
}
