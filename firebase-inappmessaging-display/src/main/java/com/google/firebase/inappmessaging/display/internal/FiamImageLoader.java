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

package com.google.firebase.inappmessaging.display.internal;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.google.firebase.inappmessaging.FirebaseInAppMessagingDisplayCallbacks;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.model.InAppMessage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

/**
 * Image loader abstraction around the Glide singleton to facilitate testing and injection
 *
 * @hide
 */
@FirebaseAppScope
public class FiamImageLoader {

  private final RequestManager requestManager;
  private final Map<String, Set<CustomTarget>> tags=new HashMap<>();

  @Inject
  FiamImageLoader(RequestManager requestManager) {
    this.requestManager = requestManager;
  }

  public FiamImageRequestCreator load(@Nullable String imageUrl) {
    Logging.logd("Starting Downloading Image : " + imageUrl);
    GlideUrl glideUrl = new GlideUrl(imageUrl,new LazyHeaders.Builder().addHeader("Accept",
            "image/*").build());
    RequestBuilder<Drawable> requestBuilder = requestManager.load(glideUrl);
    return new FiamImageRequestCreator(requestBuilder);
  }

  public void cancelTag(Class c) {
    String tag = c.getSimpleName();
    synchronized (tag) {
      if (tags.containsKey(tag)) {
        Set<CustomTarget> targets = tags.get(tag);
        for (CustomTarget target: targets) {
          if(target!=null) {
            requestManager.clear(target);
          }
        }
      }
    }
  }

  public final class FiamImageRequestCreator {
    private final RequestBuilder<Drawable> requestBuilder;
    private Callback target;
    private String tag;

    private FiamImageRequestCreator(RequestBuilder<Drawable> requestBuilder) {
     this. requestBuilder = requestBuilder;
    }

    public FiamImageRequestCreator placeholder(int placeholderResId) {
      requestBuilder.placeholder(placeholderResId);
      Logging.logd("Downloading Image Placeholder : " + placeholderResId);
      return this;
    }

    public FiamImageRequestCreator tag(Class c) {
      tag = c.getSimpleName();
      checkAndTag();
      return this;
    }

    private void checkAndTag(){
      if(target==null || TextUtils.isEmpty(tag)){
        return;
      }

      synchronized (tags){
        Set<CustomTarget> set;
        if(tags.containsKey(tag)){
          set = tags.get(tag);
        }else{
          set = new HashSet<>();
          tags.put(tag,set);
        }

        if(!set.contains(target)) {
          set.add(target);
        }
      }
    }

    public void into(ImageView imageView, Callback callback) {
      Logging.logd("Downloading Image Callback : " + callback);
      callback.setImageView(imageView);
      requestBuilder.into(callback);
      target = callback;
      checkAndTag();
    }
  }

  public static abstract class Callback extends CustomTarget<Drawable> {

    private ImageView imageView;

    @Override
    public void onLoadFailed(@Nullable Drawable errorDrawable) {
      Logging.logd("Downloading Image Failed");
      setImage(errorDrawable);
      onError(new Exception("Image loading failed!"));
    }

    private void setImage(Drawable drawable) {
      if(imageView!=null){
        imageView.setImageDrawable(drawable);
      }
    }

    @Override
    public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
      Logging.logd("Downloading Image Success!!!");
      setImage(resource);
      onSuccess();
    }

    @Override
    public void onLoadCleared(@Nullable Drawable placeholder) {
      Logging.logd("Downloading Image Cleared");
      setImage(placeholder);
      onSuccess();
    }

    public abstract void onSuccess();
    public abstract void onError(Exception e);

    void setImageView(ImageView imageView) {
      this.imageView=imageView;
    }
  }
}
