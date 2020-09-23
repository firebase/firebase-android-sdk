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

package com.google.firebase.inappmessaging;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.inappmessaging.CommonTypesProto.Trigger.ON_FOREGROUND;
import static com.google.firebase.inappmessaging.internal.injection.modules.ProtoStorageClientModule.CAMPAIGN_CACHE_FILE;
import static com.google.firebase.inappmessaging.internal.injection.modules.ProtoStorageClientModule.IMPRESSIONS_STORE_FILE;
import static com.google.firebase.inappmessaging.internal.injection.modules.ProtoStorageClientModule.RATE_LIMIT_STORE_FILE;
import static com.google.firebase.inappmessaging.testutil.TestData.ANALYTICS_EVENT_NAME;
import static com.google.firebase.inappmessaging.testutil.TestData.BANNER_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_ID_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.CAMPAIGN_NAME_STRING;
import static com.google.firebase.inappmessaging.testutil.TestData.IS_NOT_TEST_MESSAGE;
import static com.google.firebase.inappmessaging.testutil.TestData.MODAL_MESSAGE_MODEL;
import static com.google.firebase.inappmessaging.testutil.TestData.createBannerMessageCustomMetadata;
import static com.google.firebase.inappmessaging.testutil.TestProtos.BANNER_MESSAGE_PROTO;
import static com.google.firebase.inappmessaging.testutil.TestProtos.MODAL_MESSAGE_PROTO;
import static io.reactivex.BackpressureStrategy.BUFFER;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inappmessaging.CommonTypesProto.Event;
import com.google.firebase.inappmessaging.CommonTypesProto.Priority;
import com.google.firebase.inappmessaging.CommonTypesProto.TriggeringCondition;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingDismissType;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks.InAppMessagingErrorReason;
import com.google.firebase.inappmessaging.internal.AbtIntegrationHelper;
import com.google.firebase.inappmessaging.internal.DisplayCallbacksFactory;
import com.google.firebase.inappmessaging.internal.MetricsLoggerClient;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.TestDeviceHelper;
import com.google.firebase.inappmessaging.internal.injection.modules.AppMeasurementModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ApplicationModule;
import com.google.firebase.inappmessaging.internal.injection.modules.GrpcClientModule;
import com.google.firebase.inappmessaging.internal.injection.modules.ProgrammaticContextualTriggerFlowableModule;
import com.google.firebase.inappmessaging.model.BannerMessage;
import com.google.firebase.inappmessaging.model.CampaignMetadata;
import com.google.firebase.inappmessaging.model.InAppMessage;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.ThickContent;
import com.google.internal.firebase.inappmessaging.v1.CampaignProto.VanillaCampaignPayload;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.CampaignImpression;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsRequest;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc.InAppMessagingSdkServingImplBase;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.subscribers.TestSubscriber;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

// NOTE: Classes cannot have the same name as those in the
// integration-tests directory since this is an unconventional package structure.
@RunWith(AndroidJUnit4.class)
public class FirebaseInAppMessagingFlowableTest {

  public static final String PROJECT_NUMBER = "gcm-sender-id";
  public static final String APP_ID = "app-id";
  private static final String INSTALLATION_ID = "instance_id";
  private static final String INSTALLATION_TOKEN = "instance_token";
  private static final long PAST = 1000000;
  private static final long NOW = PAST + 100000;
  private static final long FUTURE = NOW + 1000000;
  private static final FirebaseApp app;
  private static final FirebaseOptions options =
      new FirebaseOptions.Builder()
          .setGcmSenderId(PROJECT_NUMBER)
          .setApplicationId(APP_ID)
          .setApiKey("apiKey")
          .setProjectId("fiam-integration-test")
          .build();
  private static final String ANALYTICS_EVENT = "event1";
  private static final TriggeringCondition.Builder ON_FOREGROUND_TRIGGER =
      TriggeringCondition.newBuilder().setFiamTrigger(ON_FOREGROUND);
  private static final TriggeringCondition.Builder ON_ANALYTICS_EVENT =
      TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(ANALYTICS_EVENT));
  private static final Priority priorityTwo = Priority.newBuilder().setValue(2).build();
  private static final VanillaCampaignPayload.Builder vanillaCampaign =
      VanillaCampaignPayload.newBuilder()
          .setCampaignId(CAMPAIGN_ID_STRING)
          .setCampaignName(CAMPAIGN_NAME_STRING)
          .setCampaignStartTimeMillis(PAST)
          .setCampaignEndTimeMillis(FUTURE);
  private static final ThickContent thickContent =
      ThickContent.newBuilder()
          .setContent(MODAL_MESSAGE_PROTO)
          .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
          .setPriority(priorityTwo)
          .addTriggeringConditions(ON_FOREGROUND_TRIGGER)
          .addTriggeringConditions(ON_ANALYTICS_EVENT)
          .setVanillaPayload(vanillaCampaign)
          .build();
  private static final FetchEligibleCampaignsResponse.Builder eligibleCampaignsBuilder =
      FetchEligibleCampaignsResponse.newBuilder()
          .setExpirationEpochTimestampMillis(FUTURE)
          .addMessages(thickContent);
  private static final FetchEligibleCampaignsResponse eligibleCampaigns =
      eligibleCampaignsBuilder.build();
  private static final RuntimeException t = new RuntimeException("boom!");
  private static final TestAnalyticsConnector analyticsConnector = new TestAnalyticsConnector();
  private static final InstallationTokenResult INSTALLATION_TOKEN_RESULT =
      new InstallationTokenResult() {
        @NonNull
        @Override
        public String getToken() {
          return INSTALLATION_TOKEN;
        }

        @Override
        public long getTokenExpirationTimestamp() {
          return 0;
        }

        @Override
        public long getTokenCreationTimestamp() {
          return 0;
        }

        @Override
        public Builder toBuilder() {
          return null;
        }
      };

  static {
    FirebaseApp.initializeApp(InstrumentationRegistry.getContext(), options);
    app = FirebaseApp.getInstance();
  }

  @Rule public final GrpcServerRule grpcServerRule = new GrpcServerRule();
  @Captor ArgumentCaptor<byte[]> byteArrayCaptor;

  @Mock
  private MetricsLoggerClient.EngagementMetricsLoggerInterface engagementMetricsLoggerInterface;

  @Mock private FirebaseInstallationsApi firebaseInstallations;
  @Mock private TestDeviceHelper testDeviceHelper;
  @Mock private Subscriber firebaseEventSubscriber;
  @Mock private AbtIntegrationHelper abtIntegrationHelper;

  private TestSubscriber<InAppMessage> subscriber;
  private FirebaseInAppMessaging instance;
  private TestForegroundNotifier foregroundNotifier;
  private Application application;
  private DisplayCallbacksFactory displayCallbacksFactory;
  private ProgramaticContextualTriggers programaticContextualTriggers;
  // CampaignId to display callbacks
  private static HashMap<String, FirebaseInAppMessagingDisplayCallbacks> callbacksHashMap;
  private static DaggerTestUniversalComponent.Builder universalComponentBuilder;
  private static DaggerTestAppComponent.Builder appComponentBuilder;

  private static void clearProtoDiskCache(Context context) {
    context.deleteFile(CAMPAIGN_CACHE_FILE);
    context.deleteFile(IMPRESSIONS_STORE_FILE);
    context.deleteFile(RATE_LIMIT_STORE_FILE);
  }

  private static List<Object> getPlainValues(TestSubscriber<InAppMessage> subscriber) {
    return subscriber.getEvents().get(0);
  }

  private static TestSubscriber<InAppMessage> listenerToFlowable(FirebaseInAppMessaging instance) {
    Flowable<InAppMessage> listenerFlowable =
        Flowable.create(
            new FlowableOnSubscribe<InAppMessage>() {
              @Override
              public void subscribe(FlowableEmitter<InAppMessage> emitter) throws Exception {
                instance.setMessageDisplayComponent(
                    new FirebaseInAppMessagingDisplay() {
                      @Override
                      public void displayMessage(
                          InAppMessage inAppMessage,
                          FirebaseInAppMessagingDisplayCallbacks callbacks) {
                        emitter.onNext(inAppMessage);
                        Log.i("FIAM", "Putting callback for IAM " + inAppMessage.getCampaignName());
                        callbacksHashMap.put(inAppMessage.getCampaignId(), callbacks);
                      }
                    });
              }
            },
            BUFFER);
    return listenerFlowable.test();
  }

  @Before
  public void setUp() {
    initMocks(this);
    callbacksHashMap = new HashMap<>();
    clearProtoDiskCache(InstrumentationRegistry.getTargetContext());
    application =
        spy((Application) InstrumentationRegistry.getTargetContext().getApplicationContext());
    when(firebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(firebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
    when(testDeviceHelper.isAppInstallFresh()).thenReturn(false);
    when(testDeviceHelper.isDeviceInTestMode()).thenReturn(false);

    if (Looper.myLooper() == null) {
      Looper.prepare();
    }
    foregroundNotifier = new TestForegroundNotifier();

    universalComponentBuilder =
        DaggerTestUniversalComponent.builder()
            .testGrpcModule(new TestGrpcModule(grpcServerRule.getChannel()))
            .testForegroundFlowableModule(new TestForegroundFlowableModule(foregroundNotifier))
            .applicationModule(new ApplicationModule(application))
            .appMeasurementModule(
                new AppMeasurementModule(analyticsConnector, firebaseEventSubscriber))
            .testSystemClockModule(new TestSystemClockModule(NOW))
            .programmaticContextualTriggerFlowableModule(
                new ProgrammaticContextualTriggerFlowableModule(
                    new ProgramaticContextualTriggers()));

    TestUniversalComponent universalComponent = universalComponentBuilder.build();

    appComponentBuilder =
        DaggerTestAppComponent.builder()
            .universalComponent(universalComponent)
            .testAbTestingModule(new TestAbTestingModule(abtIntegrationHelper))
            .testEngagementMetricsLoggerClientModule(
                new TestEngagementMetricsLoggerClientModule(app, engagementMetricsLoggerInterface))
            .grpcClientModule(new GrpcClientModule(app))
            .testApiClientModule(
                new TestApiClientModule(
                    app, firebaseInstallations, testDeviceHelper, universalComponent.clock()));
    TestAppComponent appComponent = appComponentBuilder.build();

    instance = appComponent.providesFirebaseInAppMessaging();
    displayCallbacksFactory = appComponent.displayCallbacksFactory();
    programaticContextualTriggers = universalComponent.programmaticContextualTriggers();
    grpcServerRule.getServiceRegistry().addService(new GoodFiamService(eligibleCampaigns));

    subscriber = listenerToFlowable(instance);
  }

  @Test
  public void onAppOpen_notifiesSubscriber() {
    simulateAppResume();
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onAnalyticsNotification_notifiesSubscriber() {
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onAppOpen_whenAnalyticsAbsent_notifiesSubscriber() {
    TestUniversalComponent analyticsLessUniversalComponent =
        universalComponentBuilder
            .appMeasurementModule(new AppMeasurementModule(null, firebaseEventSubscriber))
            .build();
    TestAppComponent appComponent =
        appComponentBuilder.universalComponent(analyticsLessUniversalComponent).build();
    FirebaseInAppMessaging instance = appComponent.providesFirebaseInAppMessaging();
    TestSubscriber<InAppMessage> subscriber = listenerToFlowable(instance);

    simulateAppResume();
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onProgrammaticTrigger_notifiesSubscriber() {
    programaticContextualTriggers.triggerEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);
    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void cachesResponseOnDisk() {
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertThat(fileExists(CAMPAIGN_CACHE_FILE)).isTrue();
  }

  @Test
  public void onCacheHit_notifiesCachedValue() {
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber); // this value will be cached
    grpcServerRule.getServiceRegistry().addService(new FaultyService());

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 1);

    List<InAppMessage> expected = new ArrayList<>();
    expected.add(MODAL_MESSAGE_MODEL);
    expected.add(MODAL_MESSAGE_MODEL);
    assertSubscriberListIs(expected, subscriber);
  }

  @Test
  public void onCorruptProtoCache_fetchesCampaignsFromService() throws IOException {
    changeFileContents(CAMPAIGN_CACHE_FILE);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void afterCacheLoad_returnsValueFromMemory() throws IOException {
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber); // this value will be cached
    clearProtoDiskCache(InstrumentationRegistry.getContext());

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 1);

    List<Object> triggeredMessages = getPlainValues(subscriber);
    assertThat(triggeredMessages.size()).isEqualTo(2);
    for (Object o : triggeredMessages) {
      assertThat(o).isEqualTo(MODAL_MESSAGE_MODEL);
    }
  }

  @Test
  public void whenTTLNotSet_honorsFileLastUpdated() throws IOException {
    FetchEligibleCampaignsResponse noExpiry =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .clearExpirationEpochTimestampMillis()
            .build();
    grpcServerRule.getServiceRegistry().addService(new GoodFiamService(noExpiry));
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber); // this value will be cached
    grpcServerRule.getServiceRegistry().addService(new FaultyService());

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 1);

    List<Object> triggeredMessages = getPlainValues(subscriber);
    assertThat(triggeredMessages.size()).isEqualTo(2);
    for (Object o : triggeredMessages) {
      assertThat(o).isEqualTo(MODAL_MESSAGE_MODEL);
    }
  }

  @Test
  public void onCacheDiskReadFailure_notifiesValueFromService()
      throws InterruptedException, FileNotFoundException {
    CountDownLatch readExceptionLatch = new CountDownLatch(1);
    doAnswer(
            i -> {
              readExceptionLatch.countDown();
              throw new NullPointerException();
            })
        .when(application)
        .openFileInput(CAMPAIGN_CACHE_FILE);
    subscriber = listenerToFlowable(instance);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    readExceptionLatch.await();
    waitUntilNotified(subscriber); // this value will be cached

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onCacheAndApiFailure_absorbsErrors()
      throws InterruptedException, FileNotFoundException {
    FaultyService faultyService = new FaultyService();
    grpcServerRule.getServiceRegistry().addService(faultyService);
    CountDownLatch readExceptionLatch = new CountDownLatch(1);
    doAnswer(
            i -> {
              readExceptionLatch.countDown();
              throw new NullPointerException();
            })
        .when(application)
        .openFileInput(CAMPAIGN_CACHE_FILE);
    subscriber = listenerToFlowable(instance);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    readExceptionLatch.await();
    faultyService.waitUntilFailureExercised();

    assertNoNotification(subscriber);
  }

  @Test
  public void onCacheWriteFailure_notifiesValueFetchedFromService()
      throws FileNotFoundException, InterruptedException {
    CountDownLatch writeExceptionLatch = new CountDownLatch(1);
    doAnswer(
            i -> {
              writeExceptionLatch.countDown();
              throw new NullPointerException();
            })
        .when(application)
        .openFileInput(CAMPAIGN_CACHE_FILE);
    subscriber = listenerToFlowable(instance);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    writeExceptionLatch.await();
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onTransientServiceFailure_continuesNotifying() throws InterruptedException {
    FaultyService faultyService = new FaultyService();
    grpcServerRule.getServiceRegistry().addService(faultyService);

    // Since the analytics events are received on its own thread (managed by it), and the request is
    // dispatched on io threads (managed by us), we need to ensure that the faulty service is
    // exercised by our client before "fixing" it. Without the latch, the service might be fixed
    // even before the first request is fired to it
    TestSubscriber<InAppMessage> subscriber = listenerToFlowable(instance);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    faultyService.waitUntilFailureExercised();
    grpcServerRule
        .getServiceRegistry()
        .addService(new GoodFiamService(eligibleCampaigns)); // service recovers
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onUnrelatedEvents_doesNotNotify() {
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    analyticsConnector.invokeListenerOnEvent("some_other_event");
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);

    List<Object> triggeredMessages = getPlainValues(subscriber);
    assertThat(triggeredMessages.size()).isEqualTo(2);
    for (Object o : triggeredMessages) {
      assertThat(o).isEqualTo(MODAL_MESSAGE_MODEL);
    }
  }

  @Test
  public void onExpiredCampaign_doesNotNotify() {
    VanillaCampaignPayload.Builder expiredCampaign =
        VanillaCampaignPayload.newBuilder()
            .setCampaignName(CAMPAIGN_NAME_STRING)
            .setCampaignStartTimeMillis(PAST)
            .setCampaignEndTimeMillis(PAST);
    TriggeringCondition.Builder analyticsEvent =
        TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName("ignored"));
    ThickContent t =
        ThickContent.newBuilder(thickContent)
            .clearContent()
            .clearTriggeringConditions()
            .addTriggeringConditions(analyticsEvent)
            .setVanillaPayload(expiredCampaign)
            .build();
    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns).addMessages(t).build();
    GoodFiamService impl = new GoodFiamService(r);
    grpcServerRule.getServiceRegistry().addService(impl);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    analyticsConnector.invokeListenerOnEvent("ignored");
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() == 2);

    List<InAppMessage> expected = new ArrayList<>();
    expected.add(MODAL_MESSAGE_MODEL);
    expected.add(MODAL_MESSAGE_MODEL);
    assertSubscriberListIs(expected, subscriber);
  }

  @Test
  public void onMultipleMatchingCampaigns_notifiesHighestPriorityCampaign() {
    ThickContent highPriorityContent =
        ThickContent.newBuilder(thickContent)
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .setContent(BANNER_MESSAGE_PROTO)
            .setPriority(Priority.newBuilder().setValue(1))
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(highPriorityContent)
            .build();
    GoodFiamService impl = new GoodFiamService(response);
    grpcServerRule.getServiceRegistry().addService(impl);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() == 1);
    assertSubsriberExactly(BANNER_MESSAGE_MODEL, subscriber);
  }

  @Test
  public void onEarlyUnSubscribe_absorbsError() throws InterruptedException {
    // We assert that unsubscribing early does not result in UndeliveredException
    FaultyService slowFaultyService = new SlowFaultyService();
    grpcServerRule.getServiceRegistry().addService(slowFaultyService);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    slowFaultyService.waitUntilFailureExercised();

    instance.clearDisplayListener();
    grpcServerRule
        .getServiceRegistry()
        .addService(new GoodFiamService(eligibleCampaigns)); // service recovers
    subscriber = listenerToFlowable(instance);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void onUnsupportedCampaign_doesNotNotify() {
    VanillaCampaignPayload.Builder campaign =
        VanillaCampaignPayload.newBuilder()
            .setCampaignName(CAMPAIGN_NAME_STRING)
            .setCampaignStartTimeMillis(NOW)
            .setCampaignEndTimeMillis(FUTURE);
    MessagesProto.Content unsupportedContent =
        MessagesProto.Content.newBuilder().clearMessageDetails().build();
    String eventName = "unsupported_campaign_event";
    TriggeringCondition.Builder analyticsEvent =
        TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName(eventName));
    ThickContent t =
        ThickContent.newBuilder(thickContent)
            .clearContent()
            .clearTriggeringConditions()
            .addTriggeringConditions(analyticsEvent)
            .setVanillaPayload(campaign)
            .setContent(unsupportedContent)
            .build();
    FetchEligibleCampaignsResponse r =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns).addMessages(t).build();
    GoodFiamService impl = new GoodFiamService(r);
    grpcServerRule.getServiceRegistry().addService(impl);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    analyticsConnector.invokeListenerOnEvent(eventName);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() == 2);

    // assert that the unsupported campaign is not in the list, but we get 2x MODAL

  }

  @Test
  public void whenImpressedButReceivedFromBackend_doesNotFilterCampaign()
      throws ExecutionException, InterruptedException, TimeoutException {
    Task<Void> logImpressionTask =
        displayCallbacksFactory
            .generateDisplayCallback(MODAL_MESSAGE_MODEL, ANALYTICS_EVENT_NAME)
            .impressionDetected();
    Tasks.await(logImpressionTask, 1000, TimeUnit.MILLISECONDS);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSubsriberExactly(MODAL_MESSAGE_MODEL, subscriber);
  }

  // There is not a purely functional way to determine if our clients inject the impressed
  // campaigns upstream since we filter impressions from the response on the client as well.
  // We work around this by failing hard on the fake service if we do not find impressions
  @Test
  public void whenImpressed_filtersCampaignToRequestUpstream()
      throws ExecutionException, InterruptedException, TimeoutException {
    VanillaCampaignPayload otherCampaign =
        VanillaCampaignPayload.newBuilder(vanillaCampaign.build())
            .setCampaignId("otherCampaignId")
            .setCampaignName(CAMPAIGN_NAME_STRING)
            .build();
    ThickContent otherContent =
        ThickContent.newBuilder(thickContent)
            .setContent(BANNER_MESSAGE_PROTO)
            .clearVanillaPayload()
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .clearTriggeringConditions()
            .addTriggeringConditions(
                TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName("event2")))
            .setVanillaPayload(otherCampaign)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(otherContent)
            .build();

    InAppMessagingSdkServingImplBase impl =
        new InAppMessagingSdkServingImplBase() {
          @Override
          public void fetchEligibleCampaigns(
              FetchEligibleCampaignsRequest request,
              StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {
            // Fail hard if impression not present
            CampaignImpression firstImpression = request.getAlreadySeenCampaignsList().get(0);
            assertThat(firstImpression).isNotNull();
            assertThat(firstImpression.getCampaignId())
                .isEqualTo(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId());

            responseObserver.onNext(response);
            responseObserver.onCompleted();
          }
        };
    grpcServerRule.getServiceRegistry().addService(impl);

    Task<Void> logImpressionTask =
        displayCallbacksFactory
            .generateDisplayCallback(MODAL_MESSAGE_MODEL, ANALYTICS_EVENT_NAME)
            .impressionDetected();
    Tasks.await(logImpressionTask, 1000, TimeUnit.MILLISECONDS);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    analyticsConnector.invokeListenerOnEvent("event2");

    waitUntilNotified(subscriber);
  }

  @Test
  public void whenImpressed_writesLimitsToDisk() {
    Task<Void> logImpressionTask =
        displayCallbacksFactory
            .generateDisplayCallback(MODAL_MESSAGE_MODEL, ANALYTICS_EVENT_NAME)
            .impressionDetected();
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);

    assertThat(fileExists(IMPRESSIONS_STORE_FILE)).isTrue();
  }

  @Test
  public void logImpression_writesExpectedLogToEngagementMetrics()
      throws InvalidProtocolBufferException {
    CampaignAnalytics expectedCampaignAnalytics =
        CampaignAnalytics.newBuilder()
            .setCampaignId(CAMPAIGN_ID_STRING)
            .setFiamSdkVersion(BuildConfig.VERSION_NAME)
            .setProjectNumber(PROJECT_NUMBER)
            .setClientTimestampMillis(NOW)
            .setClientApp(
                ClientAppInfo.newBuilder()
                    .setFirebaseInstanceId(INSTALLATION_ID)
                    .setGoogleAppId(APP_ID))
            .setEventType(EventType.IMPRESSION_EVENT_TYPE)
            .build();
    simulateAppResume();
    waitUntilNotified(subscriber);
    assertSingleSuccessNotification(subscriber);

    Task<Void> logAction =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .impressionDetected();
    await().timeout(2, SECONDS).until(logAction::isComplete);

    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());
    CampaignAnalytics campaignAnalytics = CampaignAnalytics.parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics).isEqualTo(expectedCampaignAnalytics);
  }

  @Test
  public void logAction_writesExpectedLogToEngagementMetrics()
      throws InvalidProtocolBufferException {
    CampaignAnalytics expectedCampaignAnalytics =
        CampaignAnalytics.newBuilder()
            .setCampaignId(CAMPAIGN_ID_STRING)
            .setFiamSdkVersion(BuildConfig.VERSION_NAME)
            .setProjectNumber(PROJECT_NUMBER)
            .setClientTimestampMillis(NOW)
            .setClientApp(
                ClientAppInfo.newBuilder()
                    .setFirebaseInstanceId(INSTALLATION_ID)
                    .setGoogleAppId(APP_ID))
            .setEventType(EventType.IMPRESSION_EVENT_TYPE)
            .build();
    simulateAppResume();
    waitUntilNotified(subscriber);
    assertSingleSuccessNotification(subscriber);

    Task<Void> logAction =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .impressionDetected();
    await().timeout(2, SECONDS).until(logAction::isComplete);

    // log impression will log 1 event
    verify(engagementMetricsLoggerInterface).logEvent(byteArrayCaptor.capture());
    CampaignAnalytics campaignAnalytics = CampaignAnalytics.parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics).isEqualTo(expectedCampaignAnalytics);
  }

  @Test
  public void logRenderError_writesExpectedLogToEngagementMetrics()
      throws InvalidProtocolBufferException {
    CampaignAnalytics expectedCampaignAnalytics =
        CampaignAnalytics.newBuilder()
            .setCampaignId(CAMPAIGN_ID_STRING)
            .setFiamSdkVersion(BuildConfig.VERSION_NAME)
            .setProjectNumber(PROJECT_NUMBER)
            .setClientTimestampMillis(NOW)
            .setClientApp(
                ClientAppInfo.newBuilder()
                    .setFirebaseInstanceId(INSTALLATION_ID)
                    .setGoogleAppId(APP_ID))
            .setRenderErrorReason(RenderErrorReason.IMAGE_DISPLAY_ERROR)
            .build();
    simulateAppResume();
    waitUntilNotified(subscriber);
    assertSingleSuccessNotification(subscriber);

    Task<Void> logAction =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .displayErrorEncountered(InAppMessagingErrorReason.IMAGE_DISPLAY_ERROR);
    await().timeout(2, SECONDS).until(logAction::isComplete);

    // this should only log the render error - not an impression
    verify(engagementMetricsLoggerInterface, times(1)).logEvent(byteArrayCaptor.capture());
    CampaignAnalytics campaignAnalytics = CampaignAnalytics.parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics).isEqualTo(expectedCampaignAnalytics);
  }

  @Test
  public void logDismiss_writesExpectedLogToEngagementMetrics()
      throws InvalidProtocolBufferException {
    CampaignAnalytics expectedCampaignAnalytics =
        CampaignAnalytics.newBuilder()
            .setCampaignId(CAMPAIGN_ID_STRING)
            .setFiamSdkVersion(BuildConfig.VERSION_NAME)
            .setProjectNumber(PROJECT_NUMBER)
            .setClientTimestampMillis(NOW)
            .setClientApp(
                ClientAppInfo.newBuilder()
                    .setFirebaseInstanceId(INSTALLATION_ID)
                    .setGoogleAppId(APP_ID))
            .setDismissType(DismissType.AUTO)
            .build();
    simulateAppResume();
    waitUntilNotified(subscriber);
    assertSingleSuccessNotification(subscriber);

    Task<Void> logImpression =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .impressionDetected();
    await().timeout(2, SECONDS).until(logImpression::isComplete);

    Task<Void> logAction =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .messageDismissed(InAppMessagingDismissType.AUTO);
    await().timeout(2, SECONDS).until(logAction::isComplete);

    // We verify this was called
    verify(engagementMetricsLoggerInterface, times(2)).logEvent(byteArrayCaptor.capture());
    CampaignAnalytics campaignAnalytics = CampaignAnalytics.parseFrom(byteArrayCaptor.getValue());
    assertThat(campaignAnalytics).isEqualTo(expectedCampaignAnalytics);
  }

  @Test
  public void logImpression_logsToEngagementMetrics() {
    Task<Void> logImpressionTask =
        displayCallbacksFactory
            .generateDisplayCallback(MODAL_MESSAGE_MODEL, ANALYTICS_EVENT_NAME)
            .impressionDetected();
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);

    assertThat(fileExists(IMPRESSIONS_STORE_FILE)).isTrue();
  }

  @Test
  @Ignore("Broken due to Impression Store changes. Needs fixing.")
  public void whenlogImpressionFails_doesNotFilterCampaign()
      throws ExecutionException, InterruptedException, TimeoutException, FileNotFoundException {
    doThrow(new NullPointerException("e1")).when(application).openFileInput(IMPRESSIONS_STORE_FILE);

    Task<Void> logImpressionTask =
        displayCallbacksFactory
            .generateDisplayCallback(MODAL_MESSAGE_MODEL, ANALYTICS_EVENT_NAME)
            .impressionDetected();
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);
    assertThat(logImpressionTask.getException()).hasMessageThat().contains("e1");
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  public void logImpression_whenlogEventLimitIncrementFails_doesNotRateLimit()
      throws ExecutionException, InterruptedException, TimeoutException, FileNotFoundException {
    CampaignMetadata otherMetadata =
        new CampaignMetadata("otherCampaignId", "otherCampaignName", IS_NOT_TEST_MESSAGE);
    VanillaCampaignPayload.Builder campaign =
        VanillaCampaignPayload.newBuilder()
            .setCampaignId(otherMetadata.getCampaignId())
            .setCampaignName(otherMetadata.getCampaignName())
            .setCampaignStartTimeMillis(PAST)
            .setCampaignEndTimeMillis(FUTURE);
    BannerMessage message = createBannerMessageCustomMetadata(otherMetadata);
    ThickContent highPriorityAppOpenEvent =
        ThickContent.newBuilder()
            .setContent(BANNER_MESSAGE_PROTO)
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .addTriggeringConditions(ON_FOREGROUND_TRIGGER)
            .setPriority(Priority.newBuilder().setValue(1))
            .setVanillaPayload(campaign)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(highPriorityAppOpenEvent)
            .build();
    GoodFiamService impl = new GoodFiamService(response);
    grpcServerRule.getServiceRegistry().addService(impl);
    doThrow(new NullPointerException("e1")).when(application).openFileInput(RATE_LIMIT_STORE_FILE);

    // We have 2 campaigns configured for app foreground.
    // We log impression for one of them during which limiter fails
    // We simulate foreground to determine that the other gets impressed.
    simulateAppResume();
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);
    assertSubsriberExactly(message, subscriber);

    Task<Void> logImpressionTask =
        callbacksHashMap
            .get(message.getCampaignMetadata().getCampaignId())
            .impressionDetected(); // limiter fails
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);
    simulateAppResume();
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 1);

    // App open events are ignored and analytics events are honored
    List<InAppMessage> expected = new ArrayList<>();
    expected.add(message);
    expected.add(MODAL_MESSAGE_MODEL);
    assertSubscriberListIs(expected, subscriber);
  }

  @Test
  public void logImpression_whenlogEventLimitIncrementSuccess_cachesLimitsInMemory()
      throws ExecutionException, InterruptedException, TimeoutException, FileNotFoundException {
    CampaignMetadata otherMetadata =
        new CampaignMetadata("otherCampaignId", "otherCampaignName", IS_NOT_TEST_MESSAGE);
    VanillaCampaignPayload.Builder campaign =
        VanillaCampaignPayload.newBuilder()
            .setCampaignId(otherMetadata.getCampaignId())
            .setCampaignName(otherMetadata.getCampaignName())
            .setCampaignStartTimeMillis(PAST)
            .setCampaignEndTimeMillis(FUTURE);
    BannerMessage message = createBannerMessageCustomMetadata(otherMetadata);
    ThickContent highPriorityAppOpenEvent =
        ThickContent.newBuilder()
            .setContent(BANNER_MESSAGE_PROTO)
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .addTriggeringConditions(ON_FOREGROUND_TRIGGER)
            .setPriority(Priority.newBuilder().setValue(1))
            .setVanillaPayload(campaign)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(highPriorityAppOpenEvent)
            .build();
    GoodFiamService impl = new GoodFiamService(response);
    grpcServerRule.getServiceRegistry().addService(impl);

    simulateAppResume();
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);
    assertSubsriberExactly(message, subscriber);
    Task<Void> logImpressionTask =
        callbacksHashMap.get(message.getCampaignMetadata().getCampaignId()).impressionDetected();
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);
    doThrow(new NullPointerException("e1")).when(application).openFileInput(RATE_LIMIT_STORE_FILE);
    simulateAppResume();
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);

    // App open events are ignored and analytics events are honored
    List<InAppMessage> expected = new ArrayList<>();
    expected.add(message);
    expected.add(MODAL_MESSAGE_MODEL);
    assertSubscriberListIs(expected, subscriber);
  }

  @Test
  public void whenlogEventLimitIncrementSuccess_writesLimitsToDisk() {
    simulateAppResume();
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);

    Task<Void> logImpressionTask =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .impressionDetected();
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);

    assertThat(fileExists(RATE_LIMIT_STORE_FILE)).isTrue();
  }

  @Test
  @Ignore("Broken due to Impression Store changes. Needs fixing.")
  public void onImpressionLog_cachesImpressionsInMemory()
      throws ExecutionException, InterruptedException, TimeoutException, FileNotFoundException {
    CampaignMetadata otherMetadata =
        new CampaignMetadata("otherCampaignId", "other_name", IS_NOT_TEST_MESSAGE);
    BannerMessage otherMessage = createBannerMessageCustomMetadata(otherMetadata);
    VanillaCampaignPayload otherCampaign =
        VanillaCampaignPayload.newBuilder(vanillaCampaign.build())
            .setCampaignId(otherMetadata.getCampaignId())
            .setCampaignName(otherMetadata.getCampaignName())
            .build();
    ThickContent otherThickContent =
        ThickContent.newBuilder(thickContent)
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .clearTriggeringConditions()
            .addTriggeringConditions(
                TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName("event2")))
            .clearVanillaPayload()
            .setVanillaPayload(otherCampaign)
            .setContent(BANNER_MESSAGE_PROTO)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(otherThickContent)
            .build();
    GoodFiamService impl = new GoodFiamService(response);
    grpcServerRule.getServiceRegistry().addService(impl);

    Task<Void> logImpressionTask =
        displayCallbacksFactory
            .generateDisplayCallback(MODAL_MESSAGE_MODEL, ANALYTICS_EVENT_NAME)
            .impressionDetected();
    Tasks.await(logImpressionTask, 1000, TimeUnit.MILLISECONDS);
    doThrow(new NullPointerException("e1")).when(application).openFileInput(IMPRESSIONS_STORE_FILE);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    analyticsConnector.invokeListenerOnEvent("event2");
    waitUntilNotified(subscriber);

    List<Object> triggeredMessages = getPlainValues(subscriber);
    assertThat(triggeredMessages.size()).isEqualTo(1);
    assertThat(triggeredMessages.get(0)).isEqualTo(otherMessage);
  }

  @Test
  public void onCorruptImpressionStore_doesNotFilter()
      throws ExecutionException, InterruptedException, TimeoutException, IOException {
    changeFileContents(IMPRESSIONS_STORE_FILE);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  @Test
  @Ignore("Broken due to Impression Store changes. Needs fixing.")
  public void onImpressionStoreReadFailure_doesNotFilter()
      throws ExecutionException, InterruptedException, TimeoutException, IOException {
    doThrow(new NullPointerException("e1")).when(application).openFileInput(IMPRESSIONS_STORE_FILE);

    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    waitUntilNotified(subscriber);

    assertSingleSuccessNotification(subscriber);
  }

  // There is not a purely functional way to determine if our clients inject the impressed
  // campaigns upstream since we filter impressions from the response on the client as well.
  // We work around this by failing hard on the fake service if we do not find an empty impression
  // list
  @Test
  @Ignore("Broken due to Impression Store changes. Needs fixing.")
  public void whenImpressionStorageClientFails_injectsEmptyImpressionListUpstream()
      throws ExecutionException, InterruptedException, TimeoutException, FileNotFoundException {
    VanillaCampaignPayload otherCampaign =
        VanillaCampaignPayload.newBuilder(vanillaCampaign.build())
            .setCampaignId("otherCampaignId")
            .setCampaignName("other_name")
            .build();
    ThickContent otherContent =
        ThickContent.newBuilder(thickContent)
            .setContent(BANNER_MESSAGE_PROTO)
            .clearVanillaPayload()
            .clearTriggeringConditions()
            .addTriggeringConditions(
                TriggeringCondition.newBuilder().setEvent(Event.newBuilder().setName("event2")))
            .setVanillaPayload(otherCampaign)
            .build();
    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(otherContent)
            .build();

    InAppMessagingSdkServingImplBase fakeFilteringService =
        new InAppMessagingSdkServingImplBase() {
          @Override
          public void fetchEligibleCampaigns(
              FetchEligibleCampaignsRequest request,
              StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {

            // Fail if impressions list is not empty
            assertThat(request.getAlreadySeenCampaignsList()).isEmpty();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
          }
        };
    grpcServerRule.getServiceRegistry().addService(fakeFilteringService);
    doThrow(new NullPointerException("e1")).when(application).openFileInput(IMPRESSIONS_STORE_FILE);
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    analyticsConnector.invokeListenerOnEvent("event2");

    waitUntilNotified(subscriber);
  }

  @Test
  public void whenAppForegroundIsRateLimited_doesNotNotify() {
    CampaignMetadata analyticsCampaignMetadata =
        new CampaignMetadata("analytics", "analyticsName", IS_NOT_TEST_MESSAGE);
    VanillaCampaignPayload.Builder analyticsCampaign =
        VanillaCampaignPayload.newBuilder()
            .setCampaignId(analyticsCampaignMetadata.getCampaignId())
            .setCampaignName(analyticsCampaignMetadata.getCampaignName())
            .setCampaignStartTimeMillis(PAST)
            .setCampaignEndTimeMillis(FUTURE);
    BannerMessage analyticsMessage = createBannerMessageCustomMetadata(analyticsCampaignMetadata);
    ThickContent analyticsThickContent =
        ThickContent.newBuilder()
            .setContent(BANNER_MESSAGE_PROTO)
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .addTriggeringConditions(ON_ANALYTICS_EVENT)
            .setVanillaPayload(analyticsCampaign)
            .build();

    CampaignMetadata foregroundCampaignMetadata =
        new CampaignMetadata("foreground", "foregroundName", IS_NOT_TEST_MESSAGE);
    VanillaCampaignPayload.Builder otherForegroundCampaign =
        VanillaCampaignPayload.newBuilder()
            .setCampaignId(foregroundCampaignMetadata.getCampaignId())
            .setCampaignName(foregroundCampaignMetadata.getCampaignName())
            .setCampaignStartTimeMillis(PAST)
            .setCampaignEndTimeMillis(FUTURE);
    ThickContent lowPriorityForegroundEvent =
        ThickContent.newBuilder()
            .setIsTestCampaign(IS_NOT_TEST_MESSAGE)
            .setPriority(Priority.newBuilder().setValue(4))
            .setContent(BANNER_MESSAGE_PROTO)
            .addTriggeringConditions(ON_ANALYTICS_EVENT)
            .addTriggeringConditions(ON_FOREGROUND_TRIGGER)
            .setVanillaPayload(otherForegroundCampaign)
            .build();

    FetchEligibleCampaignsResponse response =
        FetchEligibleCampaignsResponse.newBuilder(eligibleCampaigns)
            .addMessages(analyticsThickContent)
            .addMessages(lowPriorityForegroundEvent)
            .build();
    GoodFiamService impl = new GoodFiamService(response);
    grpcServerRule.getServiceRegistry().addService(impl);

    // Log impressions for an app open campaign
    // Trigger app open events (other app foreground campaign still eligible)
    // Trigger analytics events
    // Assert that only analytics event triggers were activated
    simulateAppResume();
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);
    assertSubsriberExactly(MODAL_MESSAGE_MODEL, subscriber);
    Task<Void> logImpressionTask =
        callbacksHashMap
            .get(MODAL_MESSAGE_MODEL.getCampaignMetadata().getCampaignId())
            .impressionDetected();
    await().timeout(2, SECONDS).until(logImpressionTask::isComplete);
    simulateAppResume();
    analyticsConnector.invokeListenerOnEvent(ANALYTICS_EVENT_NAME);
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 1);

    List<InAppMessage> expected = new ArrayList<>();
    expected.add(MODAL_MESSAGE_MODEL);
    expected.add(analyticsMessage);
    assertSubscriberListIs(expected, subscriber);
  }

  @Test
  public void onCorruptLimitStore_doesNotRateLimit()
      throws ExecutionException, InterruptedException, TimeoutException, IOException {
    changeFileContents(RATE_LIMIT_STORE_FILE);
    simulateAppResume();
    waitUntilNotified(subscriber);
    assertSubsriberExactly(MODAL_MESSAGE_MODEL, subscriber);
  }

  /* It is currently non trivial to write a non-flaky *integration* test to determine if
   * _nothing_ happens when the network fails since the expected outcome is that clients remain
   * unaffacted
   */
  @Test
  @Ignore("not ready yet")
  public void testNetworkFailure() {}

  @Test
  public void onCancellation_dropsNotification() {
    simulateAppResume();
    subscriber.dispose();
    await().timeout(2, SECONDS).until(() -> subscriber.isCancelled());

    assertNoNotification(subscriber);
  }

  private void assertSingleSuccessNotification(TestSubscriber<InAppMessage> subscriber) {
    subscriber.assertNotComplete();
    subscriber.assertNoErrors();
    assertThat(getPlainValues(subscriber).get(0)).isEqualTo(MODAL_MESSAGE_MODEL);
    assertThat(subscriber.lastThread().getName()).isEqualTo("main");
  }

  private void assertNoNotification(TestSubscriber<InAppMessage> subscriber) {
    subscriber.assertNotComplete();
    subscriber.assertNoErrors();
    subscriber.assertNoValues();
  }

  private void simulateAppResume() {
    foregroundNotifier.notifyForeground();
  }

  private void waitUntilNotified(TestSubscriber<InAppMessage> subscriber) {
    await().timeout(2, SECONDS).until(() -> subscriber.valueCount() > 0);
  }

  private void changeFileContents(String fileName) throws IOException {
    File file = new File(InstrumentationRegistry.getContext().getFilesDir(), fileName);
    FileOutputStream stream = new FileOutputStream(file);
    try {
      stream.write("corrupt-non-proto-contents".getBytes());
    } finally {
      stream.close();
    }
  }

  private boolean fileExists(String fileName) {
    File file = new File(application.getApplicationContext().getFilesDir(), fileName);
    return file.exists();
  }

  public static class GoodFiamService extends InAppMessagingSdkServingImplBase {

    private final FetchEligibleCampaignsResponse response;

    GoodFiamService(FetchEligibleCampaignsResponse response) {
      this.response = response;
    }

    @Override
    public void fetchEligibleCampaigns(
        FetchEligibleCampaignsRequest request,
        StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {
      responseObserver.onNext(response);
      responseObserver.onCompleted();
    }
  }

  public static class FilteringFiamService extends InAppMessagingSdkServingImplBase {
    private final ThickContent t1;
    private final ThickContent t2;

    FilteringFiamService(ThickContent t1, ThickContent t2) {
      this.t1 = t1;
      this.t2 = t2;
    }

    @Override
    public void fetchEligibleCampaigns(
        FetchEligibleCampaignsRequest request,
        StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {

      FetchEligibleCampaignsResponse.Builder eligibleCampaignsBuilder =
          FetchEligibleCampaignsResponse.newBuilder().setExpirationEpochTimestampMillis(FUTURE);
      HashSet<String> alreadySeen = new HashSet<>();
      for (CampaignImpression c : request.getAlreadySeenCampaignsList()) {
        alreadySeen.add(c.getCampaignId());
      }

      if (!alreadySeen.contains(t1.getVanillaPayload().getCampaignId())) {
        eligibleCampaignsBuilder.addMessages(t1);
      }

      if (!alreadySeen.contains(t2.getVanillaPayload().getCampaignId())) {
        eligibleCampaignsBuilder.addMessages(t2);
      }

      responseObserver.onNext(eligibleCampaignsBuilder.build());
      responseObserver.onCompleted();
    }
  }

  static class TestAnalyticsConnector implements AnalyticsConnector {

    private AnalyticsConnectorListener listener;

    TestAnalyticsConnector() {}

    @Override
    public AnalyticsConnectorHandle registerAnalyticsConnectorListener(
        String origin, AnalyticsConnectorListener listener) {
      if (origin.equals("fiam")) {
        this.listener = listener;
      }

      return new AnalyticsConnectorHandle() {
        @Override
        public void unregister() {}

        @Override
        public void unregisterEventNames() {}

        @Override
        public void registerEventNames(Set<String> eventNames) {}
      };
    }

    void invokeListenerOnEvent(String name) {
      Bundle b = new Bundle();
      b.putString("events", name);
      listener.onMessageTriggered(2, b);
    }

    @Override
    public void logEvent(String origin, String name, Bundle params) {}

    @Override
    public void setUserProperty(String origin, String name, Object value) {}

    @Override
    public Map<String, Object> getUserProperties(boolean includeInternal) {
      return new HashMap<>();
    }

    @Override
    public void setConditionalUserProperty(ConditionalUserProperty conditionalUserProperty) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clearConditionalUserProperty(
        String userPropertyName, String clearEventName, Bundle clearEventParams) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<ConditionalUserProperty> getConditionalUserProperties(
        String origin, String propertyNamePrefix) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxUserProperties(String origin) {
      return 0;
    }
  }

  private static class FaultyService extends InAppMessagingSdkServingImplBase {

    final CountDownLatch countDownLatch = new CountDownLatch(1);

    @Override
    public void fetchEligibleCampaigns(
        FetchEligibleCampaignsRequest request,
        StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {
      countDownLatch.countDown();
      throw t;
    }

    void waitUntilFailureExercised() throws InterruptedException {
      countDownLatch.await();
    }
  }

  private static class SlowFaultyService extends FaultyService {

    @Override
    public void fetchEligibleCampaigns(
        FetchEligibleCampaignsRequest request,
        StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {
      try {
        countDownLatch.countDown();
        Thread.sleep(1000);
        throw t;
      } catch (InterruptedException e) {
      }
    }
  }

  void assertSubsriberExactly(InAppMessage inAppMessage, TestSubscriber<InAppMessage> subsciber) {
    List<Object> triggeredMessages = getPlainValues(subsciber);
    assertThat(triggeredMessages.size()).isEqualTo(1);
    assertThat(triggeredMessages.get(0)).isEqualTo(inAppMessage);
  }

  void assertSubscriberListIs(List<InAppMessage> messages, TestSubscriber<InAppMessage> subsciber) {
    List<Object> triggeredMessages = getPlainValues(subsciber);
    assertThat(triggeredMessages.size()).isEqualTo(messages.size());
    for (int i = 0; i < messages.size(); i++) {
      assertThat(triggeredMessages.get(i)).isEqualTo(messages.get(i));
    }
  }
}
