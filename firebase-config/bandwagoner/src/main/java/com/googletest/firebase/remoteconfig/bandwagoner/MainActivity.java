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

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import com.google.android.material.tabs.TabLayout;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;

/**
 * A tabbed activity that has a primary tab with a {@link ApiFragment} for testing the Firebase
 * Remote Config (FRC) SDK API.
 *
 * @author Miraziz Yusupov
 */
public class MainActivity extends AppCompatActivity {

  private static final ImmutableList<Tab> TABS =
      ImmutableList.of(Tab.create("Api", new ApiFragment()));

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    toolbar.setTitle(R.string.app_name);
    setSupportActionBar(toolbar);

    setupTabs();
  }

  private void setupTabs() {

    PagerAdapter pagerAdapter =
        new FragmentPagerAdapter(getSupportFragmentManager()) {
          @Override
          public int getCount() {
            return TABS.size();
          }

          @Override
          public Fragment getItem(int position) {
            return TABS.get(position).fragment();
          }

          @Override
          public CharSequence getPageTitle(int position) {
            return TABS.get(position).name();
          }
        };

    ViewPager pages = (ViewPager) findViewById(R.id.view_pager);
    pages.setAdapter(pagerAdapter);
    pages.setCurrentItem(0);

    TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
    tabLayout.setupWithViewPager(pages);
  }

  @AutoValue
  abstract static class Tab {
    static Tab create(String name, Fragment fragment) {
      return new AutoValue_MainActivity_Tab(name, fragment);
    }

    abstract String name();

    abstract Fragment fragment();
  }
}
