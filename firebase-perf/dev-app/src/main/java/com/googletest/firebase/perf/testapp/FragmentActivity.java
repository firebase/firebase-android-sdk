// Copyright 2021 Google LLC
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

package com.googletest.firebase.perf.testapp;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class FragmentActivity extends AppCompatActivity {
  SharedViewModel model;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // Listening on FragmentManager's FragmentLifeCycleCallbacks
    registerListeners();
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_fragment);
    Toolbar toolbar = findViewById(R.id.toolbar_fragment_activity);
    setSupportActionBar(toolbar);
    BottomNavigationView navView = findViewById(R.id.nav_view);
    // Passing each menu ID as a set of Ids because each
    // menu should be considered as top level destinations.
    AppBarConfiguration appBarConfiguration =
        new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
            .build();
    NavController navController =
        Navigation.findNavController(this, R.id.nav_host_fragment_activity_fragment);
    // Listening on navigation events using NavController
    navController.addOnDestinationChangedListener(
        (controller, destination, arguments) -> {
          Log.d("Navigation", destination.getLabel().toString() + "Fragment");
        });
    NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
    NavigationUI.setupWithNavController(
        (BottomNavigationView) findViewById(R.id.nav_view), navController);
    model = new ViewModelProvider(this).get(SharedViewModel.class);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      int itemId = item.getItemId();
          if(itemId == R.id.action_change_gif) {
              model.changeImage();
              return true;
          } else {
              return super.onOptionsItemSelected(item);
          }
  }

  @Override
  protected void onStart() {
    super.onStart();
    Log.d(
        "FragmentActivity",
        "onStart; View Hardware Accel: "
            + decorViewAcceleratedFlag()
            + ", FrameMetricsObservers won't be observing, only queued.");
  }

  @Override
  public void onAttachedToWindow() {
    super.onAttachedToWindow();
    Log.d(
        "FragmentActivity",
        "onAttachedToWindow; View Hardware Accel: "
            + decorViewAcceleratedFlag()
            + ", FrameMetricsObservers in queue and any new observers will start observing.");
  }

  private boolean decorViewAcceleratedFlag() {
    View v = this.getWindow().getDecorView();
    return v.isHardwareAccelerated();
  }

  private void registerListeners() {
    getSupportFragmentManager()
        .registerFragmentLifecycleCallbacks(
            new FragmentManager.FragmentLifecycleCallbacks() {
              @Override
              public void onFragmentCreated(
                  @NonNull FragmentManager fm,
                  @NonNull Fragment f,
                  @Nullable Bundle savedInstanceState) {
                super.onFragmentCreated(fm, f, savedInstanceState);
                Log.d("FragmentManager", "Fragment created " + f.getClass().getSimpleName());
              }

              @Override
              public void onFragmentViewCreated(
                  @NonNull FragmentManager fm,
                  @NonNull Fragment f,
                  @NonNull View v,
                  @Nullable Bundle savedInstanceState) {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState);
                Log.d("FragmentManager", "View created " + f.getClass().getSimpleName());
              }

              @Override
              public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
                super.onFragmentStarted(fm, f);
                Log.d("FragmentManager", "Fragment started " + f.getClass().getSimpleName());
              }

              @Override
              public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                super.onFragmentResumed(fm, f);
                Log.d("FragmentManager", "Fragment resumed " + f.getClass().getSimpleName());
              }

              @Override
              public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
                super.onFragmentStopped(fm, f);
                Log.d("FragmentManager", "Fragment stopped " + f.getClass().getSimpleName());
              }

              @Override
              public void onFragmentViewDestroyed(
                  @NonNull FragmentManager fm, @NonNull Fragment f) {
                super.onFragmentViewDestroyed(fm, f);
                Log.d("FragmentManager", "View destroyed " + f.getClass().getSimpleName());
              }
            },
            true);
  }
}
