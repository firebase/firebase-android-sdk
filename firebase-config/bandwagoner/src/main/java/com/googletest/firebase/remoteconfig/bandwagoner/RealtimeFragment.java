// Copyright 2024 Google LLC
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

package com.googletest.firebase.remoteconfig.bandwagoner;

import static com.googletest.firebase.remoteconfig.bandwagoner.Constants.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RealtimeFragment extends Fragment {
  private View rootView;
  private TextView updatedParamsText;
  private TextView activeParamsText;
  private TextView realtimeExceptionText;
  private ToggleButton realtimeToggleButton;
  private Button activateButton;

  private FirebaseRemoteConfig frc;
  private ConfigUpdateListenerRegistration realtimeRegistration;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    super.onCreateView(inflater, container, savedInstanceState);

    rootView = inflater.inflate(R.layout.realtime_fragment, container, false);

    updatedParamsText = rootView.findViewById(R.id.updated_params_text);
    activeParamsText = rootView.findViewById(R.id.active_params_text);
    realtimeExceptionText = rootView.findViewById(R.id.realtime_exception_text);

    realtimeToggleButton = rootView.findViewById(R.id.realtime_toggle_button);
    realtimeToggleButton.setOnCheckedChangeListener(this::toggleRealtime);

    activateButton = rootView.findViewById(R.id.realtime_activate_button);
    activateButton.setOnClickListener(this::activateConfig);

    return rootView;
  }

  private void toggleRealtime(View view, Boolean isChecked) {
    frc = FirebaseRemoteConfig.getInstance();

    Log.d(TAG, "Toggle realtime: " + isChecked);
    if (isChecked && realtimeRegistration != null) {
      Log.w(TAG, "Tried to toggle realtime on, but it's already on! Doing nothing.");
      return;
    }

    // Toggle off case
    if (!isChecked && realtimeRegistration != null) {
      realtimeRegistration.remove();
      realtimeRegistration = null;
      return;
    }

    realtimeRegistration =
        frc.addOnConfigUpdateListener(
            new ConfigUpdateListener() {
              @Override
              public void onUpdate(ConfigUpdate configUpdate) {
                Log.d(TAG, String.join(", ", configUpdate.getUpdatedKeys()));
                updatedParamsText.setText(String.join(", ", configUpdate.getUpdatedKeys()));
              }

              @Override
              public void onError(@NonNull FirebaseRemoteConfigException error) {
                Log.w(TAG, "Realtime threw an exception!", error);
                realtimeExceptionText.setText(
                    String.format(
                        "Message: %s, Code: %s", error.getLocalizedMessage(), error.getCode()));
              }
            });
  }

  private void activateConfig(View view) {
    frc = FirebaseRemoteConfig.getInstance();

    frc.activate()
        .addOnCompleteListener(
            new OnCompleteListener<Boolean>() {
              @Override
              public void onComplete(@NonNull Task<Boolean> task) {
                List<String> keyValueStrings = new ArrayList<>();
                Map<String, FirebaseRemoteConfigValue> allParams = frc.getAll();
                for (String key : allParams.keySet()) {
                  keyValueStrings.add(String.format("%s: %s", key, allParams.get(key).asString()));
                }

                activeParamsText.setText(String.join(", ", keyValueStrings));
              }
            });
  }
}
