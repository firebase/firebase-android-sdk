package com.google.firebase.remoteconfig.internal.rollouts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.remoteconfig.testutil.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableSet;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import org.json.JSONArray;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RolloutsStateFactoryTest {
  @Mock ConfigGetParameterHandler mockConfigGetParameterHandler;

  private static final String PARAMETER_KEY = "my_feature";
  private static final String PARAMETER_VALUE = "true";

  private static final RolloutAssignment rolloutAssignment =
      RolloutAssignment.builder()
          .setRolloutId("rollout_1")
          .setVariantId("control")
          .setParameterKey(PARAMETER_KEY)
          .setParameterValue(PARAMETER_VALUE)
          .setTemplateVersion(1L)
          .build();
  private static final RolloutsState rolloutsState =
      RolloutsState.create(ImmutableSet.of(rolloutAssignment));
  private static JSONArray rolloutMetadata;

  private RolloutsStateFactory factory;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    rolloutMetadata =
        new JSONArray(
            "["
                + "{"
                + "\"rollout_id\": \"rollout_1\","
                + "\"variant_id\": \"control\","
                + "\"affected_parameter_keys\": [\""
                + PARAMETER_KEY
                + "\"],"
                + "\"template_version\": 1"
                + "}]");

    factory = new RolloutsStateFactory(mockConfigGetParameterHandler);
  }

  @Test
  public void getActiveRolloutsState_noRollouts_returnsEmptyState() throws Exception {
    RolloutsState actual = factory.getActiveRolloutsState(new JSONArray());

    assertThat(actual).isEqualTo(RolloutsState.create(ImmutableSet.of()));
  }

  @Test
  public void getActiveRolloutsState_hasRolloutsMetadata_populatesRolloutsState() throws Exception {
    when(mockConfigGetParameterHandler.getString(PARAMETER_KEY)).thenReturn(PARAMETER_VALUE);

    RolloutsState actual = factory.getActiveRolloutsState(rolloutMetadata);

    assertThat(actual).isEqualTo(rolloutsState);
  }

  @Test
  public void getActiveRolloutsState_jsonException_throwsRemoteConfigException() throws Exception {
    when(mockConfigGetParameterHandler.getString(PARAMETER_KEY)).thenReturn(PARAMETER_VALUE);
    JSONArray rolloutMetadatamMssingTemplateVersion =
        new JSONArray(
            "["
                + "{"
                + "\"rollout_id\": \"rollout_1\","
                + "\"variant_id\": \"control\","
                + "\"affected_parameter_keys\": [\""
                + PARAMETER_KEY
                + "\"]"
                + "}]");

    assertThrows(
        FirebaseRemoteConfigClientException.class,
        () -> factory.getActiveRolloutsState(rolloutMetadatamMssingTemplateVersion));
  }
}
