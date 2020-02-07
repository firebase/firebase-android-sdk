// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.breadcrumbs;

import static org.mockito.Mockito.*;

import android.os.Bundle;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.breadcrumbs.AnalyticsConnectorBreadcrumbsReceiver.BreadcrumbHandler;
import org.mockito.ArgumentCaptor;

public class AnalyticsConnectorBreadcrumbsReceiverTest extends CrashlyticsTestCase {

  private BreadcrumbHandler mockBreadcrumbHandler;
  private AnalyticsConnector mockAnalyticsConnector;
  private AnalyticsConnectorHandle mockAnalyticsHandle;

  protected void setUp() throws Exception {
    super.setUp();

    mockBreadcrumbHandler = mock(BreadcrumbHandler.class);
    mockAnalyticsConnector = mock(AnalyticsConnector.class);
    mockAnalyticsHandle = mock(AnalyticsConnectorHandle.class);
  }

  public void testAnalyticsConnectorRegisters() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), any(AnalyticsConnectorListener.class)))
        .thenReturn(mockAnalyticsHandle);

    assertTrue("Receiver not properly registered", receiver.register());

    verify(mockAnalyticsConnector)
        .registerAnalyticsConnectorListener(
            AnalyticsConnectorBreadcrumbsReceiver.CRASH_ORIGIN, receiver);
  }

  public void testRegisterFailsWhenAnalyticsConnectorIsNull() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(null, mockBreadcrumbHandler);

    assertFalse("Receiver was unexpectedly registered", receiver.register());
  }

  public void testRegisterFailsWhenAnalyticsConnectorReturnsNull() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), any(AnalyticsConnectorListener.class)))
        .thenReturn(null);

    assertFalse("Receiver was unexpectedly registered", receiver.register());
  }

  public void testUnregister() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), any(AnalyticsConnectorListener.class)))
        .thenReturn(mockAnalyticsHandle);

    assertTrue(receiver.register());

    receiver.unregister();

    verify(mockAnalyticsHandle).unregister();
  }

  public void testUnregisterIsNoOpWhenAnalyticsConnectorRegisterFails() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), any(AnalyticsConnectorListener.class)))
        .thenReturn(null);

    assertFalse("Receiver was unexpectedly registered", receiver.register());

    receiver.unregister();
  }

  public void testUnregisterIsNoOpWhenNullAnalyticsConnector() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(null, mockBreadcrumbHandler);

    assertFalse("Receiver was unexpectedly registered", receiver.register());

    receiver.unregister();
  }

  public void testBreadcrumbHandlerReceivesCorrectBreadcrumbJson() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    ArgumentCaptor<AnalyticsConnectorListener> listenerCapture =
        ArgumentCaptor.forClass(AnalyticsConnector.AnalyticsConnectorListener.class);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), listenerCapture.capture()))
        .thenReturn(mockAnalyticsHandle);

    assertTrue(receiver.register());

    AnalyticsConnector.AnalyticsConnectorListener listener = listenerCapture.getValue();
    final Bundle extras = new Bundle();
    extras.putString("name", "_s");
    final Bundle params = new Bundle();
    params.putString("_sc", "MainActivity");
    extras.putBundle("params", params);
    listener.onMessageTriggered(1, extras);

    verify(mockBreadcrumbHandler)
        .dropBreadcrumb("$A$:{\"name\":\"_s\",\"parameters\":{\"_sc\":\"MainActivity\"}}");
  }

  public void testBreadcrumbHandlerWithEmptyParams() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    ArgumentCaptor<AnalyticsConnectorListener> listenerCapture =
        ArgumentCaptor.forClass(AnalyticsConnector.AnalyticsConnectorListener.class);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), listenerCapture.capture()))
        .thenReturn(mockAnalyticsHandle);

    assertTrue(receiver.register());

    AnalyticsConnector.AnalyticsConnectorListener listener = listenerCapture.getValue();
    final Bundle extras = new Bundle();
    extras.putString("name", "_s");
    extras.putBundle("params", new Bundle());
    listener.onMessageTriggered(1, extras);

    verify(mockBreadcrumbHandler).dropBreadcrumb("$A$:{\"name\":\"_s\",\"parameters\":{}}");
  }

  public void testBreadcrumbHandlerDoesNotPassEventMissingName() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    ArgumentCaptor<AnalyticsConnectorListener> listenerCapture =
        ArgumentCaptor.forClass(AnalyticsConnector.AnalyticsConnectorListener.class);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), listenerCapture.capture()))
        .thenReturn(mockAnalyticsHandle);

    assertTrue(receiver.register());

    AnalyticsConnector.AnalyticsConnectorListener listener = listenerCapture.getValue();
    final Bundle extras = new Bundle();
    final Bundle params = new Bundle();
    params.putString("_sc", "MainActivity");
    extras.putBundle("params", params);
    listener.onMessageTriggered(1, extras);

    verifyZeroInteractions(mockBreadcrumbHandler);
  }

  public void testBreadcrumbHandlerDoesNotPassEventNullParams() {
    AnalyticsConnectorBreadcrumbsReceiver receiver =
        new AnalyticsConnectorBreadcrumbsReceiver(mockAnalyticsConnector, mockBreadcrumbHandler);
    ArgumentCaptor<AnalyticsConnectorListener> listenerCapture =
        ArgumentCaptor.forClass(AnalyticsConnector.AnalyticsConnectorListener.class);
    when(mockAnalyticsConnector.registerAnalyticsConnectorListener(
            anyString(), listenerCapture.capture()))
        .thenReturn(mockAnalyticsHandle);

    assertTrue(receiver.register());

    AnalyticsConnector.AnalyticsConnectorListener listener = listenerCapture.getValue();
    listener.onMessageTriggered(1, null);

    verifyZeroInteractions(mockBreadcrumbHandler);
  }
}
