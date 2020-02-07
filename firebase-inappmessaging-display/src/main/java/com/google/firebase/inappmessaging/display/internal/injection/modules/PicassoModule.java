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

package com.google.firebase.inappmessaging.display.internal.injection.modules;

import android.app.Application;
import com.google.firebase.inappmessaging.display.internal.PicassoErrorListener;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import dagger.Module;
import dagger.Provides;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/** @hide */
@Module
public class PicassoModule {
  @Provides
  @FirebaseAppScope
  Picasso providesFiamController(
      Application application, PicassoErrorListener picassoErrorListener) {
    okhttp3.OkHttpClient client =
        new OkHttpClient.Builder()
            .addInterceptor(
                new Interceptor() {
                  @Override
                  public Response intercept(Chain chain) throws IOException {
                    return chain.proceed(
                        chain.request().newBuilder().addHeader("Accept", "image/*").build());
                  }
                })
            .build();

    Picasso.Builder builder = new Picasso.Builder(application);
    builder.listener(picassoErrorListener).downloader(new OkHttp3Downloader(client));
    return builder.build();
  }
}
