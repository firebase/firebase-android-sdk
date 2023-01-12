package com.googletest.firebase.remoteconfig.bandwagoner;

import static com.googletest.firebase.remoteconfig.bandwagoner.Constants.TAG;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import com.google.firebase.remoteconfig.ConfigUpdate;
import com.google.firebase.remoteconfig.ConfigUpdateListener;
import com.google.firebase.remoteconfig.ConfigUpdateListenerRegistration;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;

public class RealtimeDemoFragment extends Fragment {
  private View rootView;

  private Button loginButton;
  private Button signupButton;

  private ToggleButton realtimeToggle;

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

    rootView = inflater.inflate(R.layout.demo_fragment, container, false);

    loginButton = rootView.findViewById(R.id.login_button);
    signupButton = rootView.findViewById(R.id.signup_button);
    loginButton.setVisibility(View.GONE);
    signupButton.setVisibility(View.GONE);

    realtimeToggle = rootView.findViewById(R.id.realtime_toggle);
    realtimeToggle.setOnCheckedChangeListener(this::toggleRealtime);

    FirebaseRemoteConfig.getInstance()
        .setConfigSettingsAsync(
            new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(43200)
                .build())
        .addOnCompleteListener(
            unused -> {
              //            fetch();
            });
    return rootView;
  }

  private void fetch() {
    frc = FirebaseRemoteConfig.getInstance();

    frc.fetchAndActivate()
        .addOnCompleteListener(
            activated -> {
              loginButton.setVisibility(View.VISIBLE);
              if (frc.getBoolean("signup_button_enabled")) {
                signupButton.setVisibility(View.VISIBLE);
              } else {
                signupButton.setVisibility(View.GONE);
              }
            });
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
                Log.d(TAG, String.join(", ", configUpdate.getUpdatedParams()));

                if (!configUpdate.getUpdatedParams().contains("signup_button_enabled")) {
                  return;
                }

                frc.activate()
                    .addOnCompleteListener(
                        activated -> {
                          signupButton.setVisibility(View.VISIBLE);

                          if (frc.getBoolean("signup_button_enabled")) {
                            loginButton.setVisibility(View.VISIBLE);
                          } else {
                            signupButton.setVisibility(View.GONE);
                          }
                        });
              }

              @Override
              public void onError(@NonNull FirebaseRemoteConfigException error) {
                Log.w(TAG, "Realtime threw an exception!", error);
              }
            });
  }

  //    private void activateConfig(View view) {
  //        frc = FirebaseRemoteConfig.getInstance();
  //
  //        frc.activate()
  //                .addOnCompleteListener(
  //                        new OnCompleteListener<Boolean>() {
  //                            @Override
  //                            public void onComplete(@NonNull Task<Boolean> task) {
  //                                List<String> keyValueStrings = new ArrayList<>();
  //                                Map<String, FirebaseRemoteConfigValue> allParams = frc.getAll();
  //                                for (String key : allParams.keySet()) {
  //                                    keyValueStrings.add(String.format("%s: %s", key,
  // allParams.get(key).asString()));
  //                                }
  //
  //                                activeParamsText.setText(String.join(", ", keyValueStrings));
  //                            }
  //                        });
  //    }
}
