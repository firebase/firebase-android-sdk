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

package com.google.firebase.inappmessaging.internal;

import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.firebase.inappmessaging.FirebaseInAppMessagingClickListener;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayErrorListener;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingImpressionListener;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DeveloperListenerManagerTest {
  @Mock FirebaseInAppMessagingClickListener clickListener;
  @Mock FirebaseInAppMessagingImpressionListener inAppMessagingImpressionListener;
  @Mock FirebaseInAppMessagingDisplayErrorListener errorListener;
  @Mock Executor devExecutor;
  DeveloperListenerManager developerListenerManager;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    developerListenerManager = new DeveloperListenerManager();
  }

  @Test
  public void notifies_ImpressionListenersOnImpression() {
    developerListenerManager.addImpressionListener(inAppMessagingImpressionListener);
    developerListenerManager.impressionDetected(BANNER_MESSAGE_MODEL);

    verify(inAppMessagingImpressionListener, timeout(1000).times(1))
        .impressionDetected(BANNER_MESSAGE_MODEL);
  }

  @Test
  public void notifies_ClickListenersOnClick() {
    developerListenerManager.addClickListener(clickListener);
    developerListenerManager.messageClicked(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());

    verify(clickListener, timeout(1000).times(1))
        .messageClicked(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());
  }

  @Test
  public void notifies_ErrorListenerOnError() {
    developerListenerManager.addDisplayErrorListener(errorListener);
    developerListenerManager.displayErrorEncountered(
        BANNER_MESSAGE_MODEL,
        FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);

    verify(errorListener, timeout(1000).times(1))
        .displayErrorEncountered(
            BANNER_MESSAGE_MODEL,
            FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason
                .UNSPECIFIED_RENDER_ERROR);
  }

  @Test
  public void notifies_ImpressionListenersOnImpression_onOwnExecutor() {

    developerListenerManager.addImpressionListener(inAppMessagingImpressionListener, devExecutor);
    developerListenerManager.impressionDetected(BANNER_MESSAGE_MODEL);

    verify(devExecutor, times(1)).execute(any());
  }

  @Test
  public void notifies_ClickListenersOnClick_onOwnExecutor() {
    developerListenerManager.addClickListener(clickListener, devExecutor);
    developerListenerManager.messageClicked(BANNER_MESSAGE_MODEL, BANNER_MESSAGE_MODEL.getAction());

    verify(devExecutor, times(1)).execute(any());
  }

  @Test
  public void notifies_ErrorListenerOnError_onOwnExecutor() {
    developerListenerManager.addDisplayErrorListener(errorListener, devExecutor);
    developerListenerManager.displayErrorEncountered(
        BANNER_MESSAGE_MODEL,
        FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason.UNSPECIFIED_RENDER_ERROR);

    verify(devExecutor, times(1)).execute(any());
  }

  @Test
  public void notifies_multipleImpressionListenersOnImpression() {
    developerListenerManager.addImpressionListener(inAppMessagingImpressionListener);
    developerListenerManager.impressionDetected(BANNER_MESSAGE_MODEL);

    verify(inAppMessagingImpressionListener, timeout(1000).times(1))
        .impressionDetected(BANNER_MESSAGE_MODEL);
  }
}
