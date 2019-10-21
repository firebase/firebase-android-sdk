// Copyright 2019 Google LLC
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

package com.google.firebase.components;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Discovers {@link ComponentRegistrar}s in the running process.
 *
 * <p>Firebase Components that want to be discovered at runtime and participate in Dependency
 * Injection need to implement {@link ComponentRegistrar} and declare themselves in it.
 *
 * <p>Additionally those {@link ComponentRegistrar}s need to be declared in the metadata of the
 * AndroidManifest.xml:
 *
 * <pre>{@code
 * <meta-data
 * android:name="com.google.firebase.components:com.example.foo.FooRegistrar"
 * android:value="com.google.firebase.components.ComponentRegistrar" />
 * }</pre>
 */
public final class ComponentDiscovery<T> {

  @VisibleForTesting
  interface RegistrarNameRetriever<T> {
    List<String> retrieve(T ctx);
  }

  private static final String TAG = "ComponentDiscovery";
  private static final String COMPONENT_SENTINEL_VALUE =
      "com.google.firebase.components.ComponentRegistrar";
  private static final String COMPONENT_KEY_PREFIX = "com.google.firebase.components:";

  private final T context;
  private final RegistrarNameRetriever<T> retriever;

  public static ComponentDiscovery<Context> forContext(
      Context context, Class<? extends Service> discoveryService) {
    return new ComponentDiscovery<>(context, new MetadataRegistrarNameRetriever(discoveryService));
  }

  @VisibleForTesting
  ComponentDiscovery(T context, RegistrarNameRetriever<T> retriever) {
    this.context = context;
    this.retriever = retriever;
  }

  /** Returns the discovered {@link ComponentRegistrar}s. */
  public List<ComponentRegistrar> discover() {
    return instantiate(retriever.retrieve(context));
  }

  private static List<ComponentRegistrar> instantiate(List<String> registrarNames) {
    List<ComponentRegistrar> registrars = new ArrayList<>();
    for (String name : registrarNames) {
      try {
        Class<?> loadedClass = Class.forName(name);
        if (!ComponentRegistrar.class.isAssignableFrom(loadedClass)) {
          Log.w(
              TAG,
              String.format("Class %s is not an instance of %s", name, COMPONENT_SENTINEL_VALUE));
          continue;
        }
        registrars.add((ComponentRegistrar) loadedClass.getDeclaredConstructor().newInstance());
      } catch (ClassNotFoundException e) {
        Log.w(TAG, String.format("Class %s is not an found.", name), e);
      } catch (IllegalAccessException e) {
        Log.w(TAG, String.format("Could not instantiate %s.", name), e);
      } catch (InstantiationException e) {
        Log.w(TAG, String.format("Could not instantiate %s.", name), e);
      } catch (NoSuchMethodException e) {
        Log.w(TAG, String.format("Could not instantiate %s", name), e);
      } catch (InvocationTargetException e) {
        Log.w(TAG, String.format("Could not instantiate %s", name), e);
      }
    }

    return registrars;
  }

  private static class MetadataRegistrarNameRetriever implements RegistrarNameRetriever<Context> {

    private final Class<? extends Service> discoveryService;

    private MetadataRegistrarNameRetriever(Class<? extends Service> discoveryService) {
      this.discoveryService = discoveryService;
    }

    @Override
    public List<String> retrieve(Context ctx) {
      Bundle metadata = getMetadata(ctx);

      if (metadata == null) {
        Log.w(TAG, "Could not retrieve metadata, returning empty list of registrars.");
        return Collections.emptyList();
      }

      List<String> registrarNames = new ArrayList<>();
      for (String key : metadata.keySet()) {
        Object rawValue = metadata.get(key);
        if (COMPONENT_SENTINEL_VALUE.equals(rawValue) && key.startsWith(COMPONENT_KEY_PREFIX)) {
          registrarNames.add(key.substring(COMPONENT_KEY_PREFIX.length()));
        }
      }
      return registrarNames;
    }

    private Bundle getMetadata(Context context) {
      try {
        PackageManager manager = context.getPackageManager();
        if (manager == null) {
          Log.w(TAG, "Context has no PackageManager.");
          return null;
        }
        ServiceInfo info =
            manager.getServiceInfo(
                new ComponentName(context, discoveryService), PackageManager.GET_META_DATA);
        if (info == null) {
          Log.w(TAG, discoveryService + " has no service info.");
          return null;
        }
        return info.metaData;
      } catch (PackageManager.NameNotFoundException e) {
        Log.w(TAG, "Application info not found.");
        return null;
      }
    }
  }
}
