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
package com.google.firebase.messaging;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.ComponentDiscoveryService;
import com.google.firebase.messaging.AnalyticsTestHelper.Analytics;
import com.google.firebase.messaging.Constants.AnalyticsKeys;
import com.google.firebase.messaging.Constants.FirelogAnalytics;
import com.google.firebase.messaging.Constants.MessageNotificationKeys;
import com.google.firebase.messaging.Constants.MessagePayloadKeys;
import com.google.firebase.messaging.Constants.ScionAnalytics;
import com.google.firebase.messaging.testing.AnalyticsValidator;
import com.google.firebase.messaging.testing.AnalyticsValidator.LoggedEvent;
import com.google.firebase.messaging.testing.FakeConnectorComponent;
import com.google.firebase.messaging.testing.MessagingTestHelper;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Messaging Analytics tests */
@RunWith(RobolectricTestRunner.class)
public class MessagingAnalyticsRoboTest {

  private static final AnalyticsValidator analyticsValidator =
      FakeConnectorComponent.getAnalyticsValidator();

  // Copy from FirebaseMessagingService so the tests break if the constants are changed
  // TODO(dgiorgini) instead of copy&paste create a test to verify the original constants
  static final String ANALYTICS_PREFIX = "google.c.a.";
  static final String ANALYTICS_ENABLED = ANALYTICS_PREFIX + "e";
  static final String ANALYTICS_COMPOSER_ID = ANALYTICS_PREFIX + "c_id";
  static final String ANALYTICS_COMPOSER_LABEL = ANALYTICS_PREFIX + "c_l";
  static final String ANALYTICS_MESSAGE_TIMESTAMP = ANALYTICS_PREFIX + "ts";
  static final String ANALYTICS_MESSAGE_USE_DEVICE_TIME = ANALYTICS_PREFIX + "udt";
  static final String ANALYTICS_TRACK_CONVERSIONS = ANALYTICS_PREFIX + "tc";
  static final String ANALYTICS_ABT_EXPERIMENT = ANALYTICS_PREFIX + "abt";
  static final String ANALYTICS_MESSAGE_LABEL = ANALYTICS_PREFIX + "m_l";

  // Copy from MessagingAnalytics so the tests break if the constants are changed
  static final String REENGAGEMENT_SOURCE = "Firebase";
  static final String REENGAGEMENT_MEDIUM = "notification";

  private static final String FCM_PREFERENCES = "com.google.firebase.messaging";
  private static final String DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF = "export_to_big_query";
  private static final String MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED =
      "delivery_metrics_exported_to_big_query_enabled";

  private Context context;

  @Before
  public void setUp() {
    FirebaseApp.clearInstancesForTest();
    // Create a test FirebaseApp instance
    FirebaseOptions.Builder firebaseOptionsBuilder =
        new FirebaseOptions.Builder()
            .setApplicationId(MessagingTestHelper.GOOGLE_APP_ID)
            .setProjectId(MessagingTestHelper.PROJECT_ID)
            .setApiKey(MessagingTestHelper.KEY)
            .setGcmSenderId(MessagingTestHelper.SENDER);
    FirebaseApp.initializeApp(
        ApplicationProvider.getApplicationContext(), firebaseOptionsBuilder.build());
    analyticsValidator.reset();

    // Create and Initialize Firelog service components
    context = ApplicationProvider.getApplicationContext();
  }

  /** If the developer didn't include Analytics and Firelog, we should not crash. */
  @Test
  public void testNoCrashIfAnalyticsIsMissingAtRuntime() throws Exception {
    // Remove the Firebase Component Discovery to simulate Analytics SDK not being built in the app.
    context
        .getPackageManager()
        .getServiceInfo(
            new ComponentName(
                ApplicationProvider.getApplicationContext(), ComponentDiscoveryService.class),
            PackageManager.GET_META_DATA)
        .metaData
        .clear();

    // Re Init Firebase
    setUp();

    // Confirm that Analytics and Firelog are NOT available
    assertThat(FirebaseApp.getInstance().get(TransportFactory.class)).isNull();
    assertThat(FirebaseApp.getInstance().get(AnalyticsConnector.class)).isNull();

    // Calling LogEvent methods should not crash the app
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "composer_key");

    MessagingAnalytics.logNotificationReceived(intent);
    MessagingAnalytics.logNotificationOpen(intent);
    MessagingAnalytics.logNotificationDismiss(intent);
    // No Exception is thrown = no crash, yeah
  }

  /** Test that a message with analytics enabled should upload scion metrics. */
  @Test
  public void testShouldUploadScionMetrics() {
    Intent intent = new Intent(FirebaseMessagingService.ACTION_REMOTE_INTENT);
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    assertThat(MessagingAnalytics.shouldUploadScionMetrics(intent)).isTrue();
  }

  /** Test that a message with analytics not enabled should not upload scion metrics. */
  @Test
  public void testShouldUploadScionMetrics_notEnabled() {
    Intent intent = new Intent(FirebaseMessagingService.ACTION_REMOTE_INTENT);
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "0");

    assertThat(MessagingAnalytics.shouldUploadScionMetrics(intent)).isFalse();
  }

  /** Test that a message with analytics extra not set should not upload scion metrics. */
  @Test
  public void testShouldUploadScionMetrics_notSet() {
    Intent intent = new Intent(FirebaseMessagingService.ACTION_REMOTE_INTENT);

    assertThat(MessagingAnalytics.shouldUploadScionMetrics(intent)).isFalse();
  }

  /**
   * Test that a direct boot message with analytics enabled should not upload metrics since
   * analytics is not available in direct boot mode.
   */
  @Test
  public void testShouldUploadScionMetrics_directBootMessage() {
    Intent intent = new Intent(FirebaseMessagingService.ACTION_DIRECT_BOOT_REMOTE_INTENT);
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    assertThat(MessagingAnalytics.shouldUploadScionMetrics(intent)).isFalse();
  }

  /**
   * Test that if there is no manifest nor run-time specification of whether FCM should export
   * delivery metrics to big query, the expected behavior is to not upload the metrics.
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_noneManifestNoneSetter() {
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, null);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isFalse();
  }

  /** Test that when there is no manifest specification, but flag is set to true at run time. */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_noneManifestTrueSetter() {
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, null);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Ack
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(true);

    // Verify
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, null);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, true);
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isTrue();
  }

  /** Test that when there is no manifest specification, but flag is set to false at run time. */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_noneManifestFalseSetter() {
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, null);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Ack
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(false);

    // Verify
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, null);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, false);
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isFalse();
  }

  /**
   * Test that when manifest specifies the flag to be true, but no flag is set at run time. The
   * expected behavior is export delivery metrics to BQ is enabled.
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_trueManifestNoneSetter() throws Exception {
    editManifestApplicationMetadata()
        .putBoolean(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Verify
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isTrue();
  }

  /**
   * Test that when manifest specifies the flag to be true, and the flag is set to be true at run
   * time. The expected behavior is export delivery metrics to BQ is enabled.
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_trueManifestTrueSetter() throws Exception {
    editManifestApplicationMetadata()
        .putBoolean(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Ack
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(true);

    // Verify
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isTrue();
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, true);
  }

  /**
   * Test that when manifest specifies the flag to be true, and the flag is set to be false at run
   * time. The expected behavior is export delivery metrics to BQ is disabled. Because the run-time
   * flag should override the compile-time flag
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_trueManifestFalseSetter() throws Exception {
    editManifestApplicationMetadata()
        .putBoolean(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Ack
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(false);

    // Verify
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isFalse();
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, true);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, false);
  }

  /**
   * Test that when manifest specifies the flag to be false, and the flag is not set at run time.
   * The expected behavior is export delivery metrics to BQ is disabled.
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_falseManifestNoneSetter() throws Exception {
    editManifestApplicationMetadata()
        .putBoolean(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Verify
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isFalse();
  }

  /**
   * Test that when manifest specifies the flag to be false, and the flag is set to be false at run
   * time. The expected behavior is export delivery metrics to BQ is disabled.
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_falseManifestFalseSetter()
      throws Exception {
    editManifestApplicationMetadata()
        .putBoolean(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Ack
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(false);

    // Verify
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isFalse();
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, false);
  }

  /**
   * Test that when manifest specifies the flag to be false, but the flag is set to be true at run
   * time. The expected behavior is export delivery metrics to BQ is enabled. ecause the run-time
   * flag should override the compile-time flag.
   */
  @Test
  public void testShouldExportDeliveryMetricsToBigQuery_falseManifestTrueSetter() throws Exception {
    editManifestApplicationMetadata()
        .putBoolean(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, null);

    // Ack
    MessagingAnalytics.setDeliveryMetricsExportToBigQuery(true);

    // Verify
    assertThat(MessagingAnalytics.deliveryMetricsExportToBigQueryEnabled()).isTrue();
    assertManifestFieldWithValue(MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false);
    assertPreferencesFieldWithValue(DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_PREF, true);
  }

  private void assertPreferencesFieldWithValue(String field, Boolean expectedValue) {
    SharedPreferences preferences =
        context.getSharedPreferences(FCM_PREFERENCES, Context.MODE_PRIVATE);

    // expecting the field doesn't exist in the shared preference
    if (expectedValue == null) {
      assertThat(preferences.contains(field)).isFalse();
      return;
    }

    assertThat(preferences.getBoolean(field, false)).isEqualTo(expectedValue);
  }

  private void assertManifestFieldWithValue(String field, Boolean expectedValue) {
    try {
      PackageManager packageManager = context.getPackageManager();
      if (packageManager != null) {
        ApplicationInfo applicationInfo =
            packageManager.getApplicationInfo(
                context.getPackageName(), PackageManager.GET_META_DATA);
        if (applicationInfo != null
            && applicationInfo.metaData != null
            && applicationInfo.metaData.containsKey(field)) {

          // expecting the field doesn't exist in the manifest
          if (expectedValue == null) {
            assertThat(applicationInfo.metaData.containsKey(field)).isFalse();
            return;
          }

          assertThat(
                  applicationInfo.metaData.getBoolean(
                      MANIFEST_DELIVERY_METRICS_EXPORT_TO_BIG_QUERY_ENABLED, false))
              .isEqualTo(expectedValue);
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      // Shouldn't happen.
    }
  }

  private Bundle editManifestApplicationMetadata() throws Exception {
    return shadowOf(context.getPackageManager())
        .getInternalMutablePackageInfo(context.getPackageName())
        .applicationInfo
        .metaData;
  }

  /**
   * Notifications from Composer UI (ANALYTICS_COMPOSER_ID = "campaign_id") are reported to
   * Analytics with Param.MESSAGE_ID = "campaign_id".
   */
  @Test
  public void testComposerUiPopulatesParamMessageId() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);

    assertThat(event.getParams().getString(Analytics.PARAM_MESSAGE_ID)).isEqualTo("campaign_id");
  }

  /**
   * Notifications from HTTP Topic API (MessagePayloadKeys.FROM = "/topics/TOPIC") are reported to
   * Analytics with Param.TOPIC = "/topics/name".
   */
  @Test
  public void testTopicsApiPopulatesParamTopic_straightFromHttpTopicApi() {
    Intent intent = new Intent();
    intent.putExtra(MessagePayloadKeys.FROM, "/topics/test_topic");
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().containsKey(Analytics.PARAM_MESSAGE_ID)).isFalse();

    assertThat(event.getParams().getString(ScionAnalytics.PARAM_TOPIC))
        .isEqualTo("/topics/test_topic");
  }

  /**
   * Notifications from Composer UI to a Topic (ANALYTICS_COMPOSER_ID = "campaign_id" +
   * MessagePayloadKeys.FROM = "/topics/TOPIC") are reported to Analytics with both Param.MESSAGE_ID
   * and Param.TOPIC
   */
  @Test
  public void testTopicsApiPopulatesParamTopic_fromComposerUiUsingTopic() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(MessagePayloadKeys.FROM, "/topics/test_topic");
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);

    assertThat(event.getParams().getString(Analytics.PARAM_MESSAGE_ID)).isEqualTo("campaign_id");

    assertThat(event.getParams().getString(ScionAnalytics.PARAM_TOPIC))
        .isEqualTo("/topics/test_topic");
  }

  /* Notifications with FROM != "/topics/%" DO NOT report to Analytics Param.TOPIC */
  @Test
  public void testTopicsApiPopulatesParamTopic_fromComposerUiWithFromNotATopic() {
    Intent intent = new Intent();
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(MessagePayloadKeys.FROM, "not_a_topic_name");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);

    assertThat(event.getParams().getString(Analytics.PARAM_MESSAGE_ID)).isEqualTo("campaign_id");
    assertThat(event.getParams().containsKey(ScionAnalytics.PARAM_TOPIC)).isFalse();
  }

  /** Notifications with ANALYTICS_MESSAGE_TIMESTAMP should set Param.MESSAGE_TIME. */
  @Test
  public void analyticsMessageTimestamp() {
    Intent intent = new Intent();
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(ANALYTICS_MESSAGE_TIMESTAMP, "1234");

    // Notification with a valid timestamp
    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().getInt(ScionAnalytics.PARAM_MESSAGE_TIME)).isEqualTo(1234);
  }

  /** Notifications with invalid ANALYTICS_MESSAGE_TIMESTAMP should NOT set Param.MESSAGE_TIME. */
  @Test
  public void analyticsMessageTimestamp_invalid() {
    Intent intent = new Intent();
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Notification with a corrupted timestamp
    intent.putExtra(ANALYTICS_MESSAGE_TIMESTAMP, "1234_garbage");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().containsKey(ScionAnalytics.PARAM_MESSAGE_TIME)).isFalse();
  }

  @Test
  public void analyticsComposerLabel_missing() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    // ANALYTICS_COMPOSER_LABEL not set

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().containsKey(ScionAnalytics.PARAM_MESSAGE_TIME)).isFalse();
  }

  @Test
  public void analyticsComposerLabel() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(ANALYTICS_COMPOSER_LABEL, "human composer label");
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().getString(ScionAnalytics.PARAM_MESSAGE_NAME))
        .isEqualTo("human composer label");
  }

  @Test
  public void analyticsMessageLabel_missing() {
    Intent intent = new Intent();
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().containsKey(ScionAnalytics.PARAM_LABEL)).isFalse();
  }

  @Test
  public void analyticsMessageLabel_present() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_MESSAGE_LABEL, "developer-provided-label");
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().getString(ScionAnalytics.PARAM_LABEL))
        .isEqualTo("developer-provided-label");
  }

  @Test
  public void notificationLifecycle_eventReceived_dataMessage() {
    Intent intent = new Intent();
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().getString("_nmc")).isEqualTo("data");
  }

  @Test
  public void notificationLifecycle_eventReceived_notification() {
    Intent intent = new Intent();
    intent.putExtra(Constants.AnalyticsKeys.ENABLED, "1");

    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Set as notification.
    intent.putExtra("gcm.n.e", "1");

    MessagingAnalytics.logNotificationReceived(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_RECEIVE);
    assertThat(event.getParams().getString("_nmc")).isEqualTo("display");
  }

  @Test
  public void notificationLifecycle_eventOpen() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");

    MessagingAnalytics.logNotificationOpen(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_OPEN);
  }

  @Test
  public void notificationLifecycle_eventDismiss() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");

    MessagingAnalytics.logNotificationDismiss(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_DISMISS);
  }

  @Test
  public void notificationLifecycle_eventForeground_dataMessage() {
    Intent intent = new Intent();

    MessagingAnalytics.logNotificationForeground(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_FOREGROUND);
    assertThat(event.getParams().getString("_nmc")).isEqualTo("data");
  }

  @Test
  public void notificationLifecycle_eventForeground_notification() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Set as notification.
    intent.putExtra("gcm.n.e", "1");

    MessagingAnalytics.logNotificationForeground(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(1);
    AnalyticsValidator.LoggedEvent event = events.get(0);
    assertThat(event.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(event.getName()).isEqualTo(ScionAnalytics.EVENT_NOTIFICATION_FOREGROUND);
    assertThat(event.getParams().getString("_nmc")).isEqualTo("display");
  }

  @Test
  public void trackConversions_enabled_eventReceived() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(ANALYTICS_TRACK_CONVERSIONS, "1");

    // Notification received: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationReceived(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  /**
   * Notifications with ANALYTICS_TRACK_CONVERSIONS="1" should log
   * UserProperty.FIREBASE_LAST_NOTIFICATION and Event.FIREBASE_CAMPAIGN when opened.
   */
  @Test
  public void trackConversions_enabled_eventOpen() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(ANALYTICS_TRACK_CONVERSIONS, "1");

    // Notification opened
    // 2 events are created: Event.FIREBASE_CAMPAIGN and Event.NOTIFICATION_OPEN
    // 1 user-property is set: UserProperty.FIREBASE_LAST_NOTIFICATION
    MessagingAnalytics.logNotificationOpen(intent);

    List<AnalyticsValidator.LoggedEvent> events = analyticsValidator.getLoggedEvents();
    assertThat(events).hasSize(2);

    AnalyticsValidator.LoggedEvent campaignEvent = events.get(0);
    assertThat(campaignEvent.getOrigin()).isEqualTo(ScionAnalytics.ORIGIN_FCM);
    assertThat(campaignEvent.getName()).isEqualTo(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(campaignEvent.getParams().getString(ScionAnalytics.PARAM_SOURCE))
        .isEqualTo(REENGAGEMENT_SOURCE);
    assertThat(campaignEvent.getParams().getString(ScionAnalytics.PARAM_MEDIUM))
        .isEqualTo(REENGAGEMENT_MEDIUM);
    assertThat(campaignEvent.getParams().getString(ScionAnalytics.PARAM_CAMPAIGN))
        .isEqualTo("campaign_id");
    assertThat(campaignEvent.getParams().getString("_cis")).isEqualTo("fcm_integration");

    assertThat(
            analyticsValidator
                .getUserProperties(true)
                .get(ScionAnalytics.USER_PROPERTY_FIREBASE_LAST_NOTIFICATION))
        .isEqualTo("campaign_id");
  }

  @Test
  public void trackConversions_enabled_eventDismiss() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(ANALYTICS_TRACK_CONVERSIONS, "1");

    // Notification is dismissed: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationDismiss(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  @Test
  public void trackConversions_enabled_eventForeground() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    intent.putExtra(ANALYTICS_TRACK_CONVERSIONS, "1");
    // Notification foreground: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationForeground(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  /**
   * Notifications without ANALYTICS_TRACK_CONVERSIONS="1" should not log
   * UserProperty.FIREBASE_LAST_NOTIFICATION or Event.FIREBASE_CAMPAIGN.
   */
  @Test
  public void trackConversions_disabled_eventReceived() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Extra: ANALYTICS_TRACK_CONVERSIONS="1" NOT set

    // Notification received: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationReceived(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  /**
   * Notifications without ANALYTICS_TRACK_CONVERSIONS="1" should not log
   * UserProperty.FIREBASE_LAST_NOTIFICATION or Event.FIREBASE_CAMPAIGN.
   */
  @Test
  public void trackConversions_disabled_eventOpen() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Extra: ANALYTICS_TRACK_CONVERSIONS="1" NOT set

    // Notification opened: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationOpen(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  /**
   * Notifications without ANALYTICS_TRACK_CONVERSIONS="1" should not log
   * UserProperty.FIREBASE_LAST_NOTIFICATION or Event.FIREBASE_CAMPAIGN.
   */
  @Test
  public void trackConversions_disabled_eventDismiss() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Extra: ANALYTICS_TRACK_CONVERSIONS="1" NOT set

    // Notification is dismissed: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationDismiss(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  /**
   * Notifications without ANALYTICS_TRACK_CONVERSIONS="1" should not log
   * UserProperty.FIREBASE_LAST_NOTIFICATION or Event.FIREBASE_CAMPAIGN.
   */
  @Test
  public void trackConversions_disabled_eventForeground() {
    Intent intent = new Intent();
    intent.putExtra(ANALYTICS_COMPOSER_ID, "campaign_id");
    // Extra: ANALYTICS_TRACK_CONVERSIONS="1" NOT set

    // Notification foreground: NO user-property and NO Event.FIREBASE_CAMPAIGN is logged
    MessagingAnalytics.logNotificationForeground(intent);

    assertThat(analyticsValidator.getLoggedEventNames())
        .doesNotContain(ScionAnalytics.EVENT_FIREBASE_CAMPAIGN);
    assertThat(analyticsValidator.getUserProperties(true)).isEmpty();
  }

  @Test
  public void testGetTtl_validStringTtl() {
    Intent testIntent = new Intent();
    testIntent.putExtra(MessagePayloadKeys.TTL, "123");

    assertThat(MessagingAnalytics.getTtl(testIntent)).isEqualTo(123);
  }

  @Test
  public void testGetTtl_validIntTtl() {
    Intent testIntent = new Intent();
    testIntent.putExtra(MessagePayloadKeys.TTL, 123);

    assertThat(MessagingAnalytics.getTtl(testIntent)).isEqualTo(123);
  }

  @Test
  public void testGetTtl_invalidTtl() {
    Intent intent = new Intent();
    intent.putExtra(MessagePayloadKeys.TTL, "abc");

    assertThat(MessagingAnalytics.getTtl(intent)).isEqualTo(0);
  }

  @Test
  public void testLogToScion_invalidMessageTime_doesNotThrow() {
    Intent intent = new Intent();
    intent.putExtra(AnalyticsKeys.MESSAGE_TIMESTAMP, "invalid_message_time");

    assertThat(MessagingAnalytics.getMessageTime(intent)).isEqualTo("invalid_message_time");
    MessagingAnalytics.logToScion("test_event", intent);
    // no exception thrown means we are handling the NumberFormatException gracefully
  }

  // Since the notification type is named differently in the firelog and scion proto, we want to
  // distinguish them carefully, hence this test
  @Test
  public void testGetMessageTypeForScion_notification() {
    Intent intent = new Intent();
    // simulate a display notification
    intent.putExtra(MessageNotificationKeys.ENABLE_NOTIFICATION, "1");

    assertThat(MessagingAnalytics.getMessageTypeForScion(intent))
        .isEqualTo(ScionAnalytics.MessageType.DISPLAY_NOTIFICATION);
  }

  // Since the notification type is named differently in the firelog and scion proto, we want to
  // distinguish them carefully, hence this test
  @Test
  public void testGetMessageTypeForFirelog_dataMessage() {
    Intent intent = new Intent();
    // simulate a display notification
    intent.putExtra(MessageNotificationKeys.ENABLE_NOTIFICATION, "1");

    assertThat(MessagingAnalytics.getMessageTypeForFirelog(intent))
        .isEqualTo(FirelogAnalytics.MessageType.DISPLAY_NOTIFICATION);
  }
}
