/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googletest.firebase.remoteconfig.bandwagoner;

import static com.googletest.firebase.remoteconfig.bandwagoner.TimeFormatHelper.getCurrentTimeString;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

/**
 * The main layout and logic for running Firebase Remote Config (FRC) API calls and displaying their
 * results.
 *
 * @author Miraziz Yusupov
 */
public class ApiFragment extends Fragment {

  private FirebaseRemoteConfig frc;
  private FirebaseInstallationsApi firebaseInstallations;
  private View rootView;
  private EditText minimumFetchIntervalText;
  private EditText parameterKeyText;
  private TextView parameterValueText;
  private TextView apiCallProgressText;
  private TextView apiCallResultsText;

  private static final String TAG = "Bandwagoner";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    frc = FirebaseRemoteConfig.getInstance();
    frc.setConfigSettingsAsync(
        new FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(0L).build());

    firebaseInstallations = FirebaseApp.getInstance().get(FirebaseInstallationsApi.class);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    rootView = inflater.inflate(R.layout.api_fragment, container, false);

    addListenerToButton(R.id.fetch_button, this::onFetch);
    addListenerToButton(R.id.activate_fetched_button, this::onActivateFetched);
    addListenerToButton(R.id.get_string_button, this::onGetString);
    addListenerToButton(R.id.reset_frc_button, this::onReset);

    minimumFetchIntervalText = rootView.findViewById(R.id.frc_minimum_fetch_interval);
    parameterKeyText = rootView.findViewById(R.id.frc_parameter_key);
    parameterValueText = rootView.findViewById(R.id.frc_parameter_value);
    apiCallProgressText = rootView.findViewById(R.id.api_call_progress);
    apiCallResultsText = rootView.findViewById(R.id.api_call_results);

    ToggleButton devModeButton = rootView.findViewById(R.id.dev_mode_toggle_button);
    devModeButton.setOnCheckedChangeListener((unusedView, isChecked) -> onDevModeToggle(isChecked));
    devModeButton.toggle();

    TextView sdkVersionText = rootView.findViewById(R.id.sdk_version_text);

    TextView iidText = rootView.findViewById(R.id.iid_text);

    Task<String> installationIdTask = firebaseInstallations.getId();
    Task<InstallationTokenResult> installationAuthTokenTask = firebaseInstallations.getToken(false);

    Tasks.whenAllComplete(installationIdTask, installationAuthTokenTask)
        .addOnCompleteListener(
            unusedCompletedTasks -> {
              if (installationIdTask.isSuccessful()) {
                iidText.setText(
                    String.format("Installation ID: %s", installationIdTask.getResult()));
              } else {
                Log.e(TAG, "Error getting installation ID", installationIdTask.getException());
              }

              if (installationAuthTokenTask.isSuccessful()) {
                Log.i(
                    TAG,
                    String.format(
                        "Installation authentication token: %s",
                        installationAuthTokenTask.getResult().getToken()));
              } else {
                Log.e(
                    TAG,
                    "Error getting installation authentication token",
                    installationAuthTokenTask.getException());
              }
            });

    return rootView;
  }

  /** Adds the given {@link OnClickListener} to the button specified by {@code buttonResourceId}. */
  private void addListenerToButton(@IdRes int buttonResourceId, OnClickListener onClickListener) {
    rootView.findViewById(buttonResourceId).setOnClickListener(onClickListener);
  }

  /** Sets the version of the FRC server the SDK fetches from. */
  private void onDevModeToggle(boolean isChecked) {
    hideSoftKeyboard();

    FirebaseRemoteConfigSettings.Builder settingsBuilder =
        new FirebaseRemoteConfigSettings.Builder();
    String minimumFetchIntervalString = minimumFetchIntervalText.getText().toString();

    if (isChecked || TextUtils.isEmpty(minimumFetchIntervalString)) {
      settingsBuilder.setMinimumFetchIntervalInSeconds(0L);
    } else {
      settingsBuilder.setMinimumFetchIntervalInSeconds(
          Integer.parseInt(minimumFetchIntervalString));
    }

    frc.setConfigSettingsAsync(settingsBuilder.build());
  }

  /**
   * Fetches configs from the FRC server.
   *
   * <p>Logs the result of the operation in the {@code api_call_results} {@link TextView}.
   */
  private void onFetch(View unusedView) {
    hideSoftKeyboard();

    IdlingResourceManager.getInstance().increment();

    String minimumFetchIntervalString = minimumFetchIntervalText.getText().toString();

    apiCallProgressText.setText("Fetching...");
    Task<Void> fetchTask;
    if (!minimumFetchIntervalString.isEmpty()) {
      fetchTask =
          frc.fetch(
              /* minimumFetchIntervalInSeconds= */ Integer.valueOf(minimumFetchIntervalString));
    } else {
      fetchTask = frc.fetch();
    }

    fetchTask.addOnCompleteListener(
        (completedFetchTask) -> {
          if (isFragmentDestroyed()) {
            Log.w(TAG, "Fragment was destroyed before fetch was completed.");
            IdlingResourceManager.getInstance().decrement();
            return;
          }

          String currentTimeString = getCurrentTimeString();
          if (completedFetchTask.isSuccessful()) {
            apiCallResultsText.setText(
                String.format("%s - Fetch was successful!", currentTimeString));
            Log.i(TAG, "Fetch was successful!");
          } else {
            apiCallResultsText.setText(
                String.format(
                    "%s - Fetch failed with exception: %s",
                    currentTimeString, completedFetchTask.getException()));
            Log.e(TAG, "Fetch failed!", completedFetchTask.getException());
          }

          apiCallProgressText.setText("");
          IdlingResourceManager.getInstance().decrement();
        });
  }

  /**
   * Activates the most recently fetched configs.
   *
   * <p>Logs the result of the operation in the {@code api_call_results} {@link TextView}.
   */
  private void onActivateFetched(View unusedView) {
    hideSoftKeyboard();

    frc.activate()
        .addOnCompleteListener(
            activateTask -> {
              if (activateTask.isSuccessful()) {
                apiCallResultsText.setText(
                    String.format(
                        "%s - activate %s!",
                        getCurrentTimeString(),
                        activateTask.getResult() ? "was successful" : "returned false"));
              } else {
                apiCallResultsText.setText(
                    String.format("%s - activate failed!", getCurrentTimeString()));
              }
            });
  }

  /**
   * Gets an FRC parameter value in a {@link String} format.
   *
   * <p>Sets the {@code frc_parameter_value} {@link TextView} to the value of the FRC parameter for
   * the key in the {@code frc_parameter_key} {@link EditText}.
   */
  private void onGetString(View unusedView) {
    hideSoftKeyboard();

    String paramKey = parameterKeyText.getText().toString();
    String paramValue = frc.getString(paramKey);

    parameterValueText.setText(
        String.format("%s - String: (%s, %s)", getCurrentTimeString(), paramKey, paramValue));
  }

  /** Resets all FRC configs and settings, both in memory and disk. */
  private void onReset(View unusedView) {
    hideSoftKeyboard();

    IdlingResourceManager.getInstance().increment();

    apiCallProgressText.setText("Resetting...");
    frc.reset()
        .addOnCompleteListener(
            (resetTask) -> {
              if (isFragmentDestroyed()) {
                Log.w(TAG, "Fragment was destroyed before fetch was completed.");
                IdlingResourceManager.getInstance().decrement();
                return;
              }

              String currentTimeString = getCurrentTimeString();
              if (resetTask.isSuccessful()) {
                // Reset means dev mode was turned off.
                ((ToggleButton) rootView.findViewById(R.id.dev_mode_toggle_button))
                    .setChecked(false);

                apiCallResultsText.setText(
                    String.format("%s - Reset was successful!", currentTimeString));
                Log.i(TAG, "Reset was successful!");
              } else {
                apiCallResultsText.setText(
                    String.format(
                        "%s - Reset failed with exception: %s",
                        currentTimeString, resetTask.getException()));
                Log.e(TAG, "Reset failed!", resetTask.getException());
              }

              apiCallProgressText.setText("");
              IdlingResourceManager.getInstance().decrement();
            });
  }

  /** Hides the soft keyboard, usually after clicking a button. */
  public void hideSoftKeyboard() {
    Activity activity = getActivity();
    if (activity == null || activity.getCurrentFocus() == null) {
      return;
    }

    InputMethodManager inputMethodManager =
        (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
    inputMethodManager.hideSoftInputFromWindow(activity.getCurrentFocus().getWindowToken(), 0);
  }

  private boolean isFragmentDestroyed() {
    return this.isRemoving()
        || this.getActivity() == null
        || this.isDetached()
        || !this.isAdded()
        || this.getView() == null;
  }
}
