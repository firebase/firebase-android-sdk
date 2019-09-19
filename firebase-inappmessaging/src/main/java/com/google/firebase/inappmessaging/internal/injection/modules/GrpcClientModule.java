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

package com.google.firebase.inappmessaging.internal.injection.modules;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import androidx.annotation.NonNull;
import com.google.common.io.BaseEncoding;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc.InAppMessagingSdkServingBlockingStub;
import dagger.Module;
import dagger.Provides;
import io.grpc.Channel;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Bindings for grpc client
 *
 * @hide
 */
@Module
public class GrpcClientModule {
  private final FirebaseApp firebaseApp;

  public GrpcClientModule(FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
  }

  @Provides
  public Metadata providesApiKeyHeaders() {
    final Metadata.Key<String> apiClientKeyHeader =
        Metadata.Key.of("X-Goog-Api-Key", Metadata.ASCII_STRING_MARSHALLER);
    final Metadata.Key<String> androidPackageHeader =
        Metadata.Key.of("X-Android-Package", Metadata.ASCII_STRING_MARSHALLER);
    final Metadata.Key<String> androidCertHashHeader =
        Metadata.Key.of("X-Android-Cert", Metadata.ASCII_STRING_MARSHALLER);

    Metadata metadata = new Metadata();
    String packageName = firebaseApp.getApplicationContext().getPackageName();
    metadata.put(apiClientKeyHeader, firebaseApp.getOptions().getApiKey());
    metadata.put(androidPackageHeader, packageName);

    String signature =
        getSignature(firebaseApp.getApplicationContext().getPackageManager(), packageName);

    if (signature != null) {
      metadata.put(androidCertHashHeader, signature);
    }
    return metadata;
  }

  public static String getSignature(@NonNull PackageManager pm, @NonNull String packageName) {
    try {
      PackageInfo packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
      if (packageInfo == null
          || packageInfo.signatures == null
          || packageInfo.signatures.length == 0
          || packageInfo.signatures[0] == null) {
        return null;
      }
      return signatureDigest(packageInfo.signatures[0]);
    } catch (PackageManager.NameNotFoundException e) {
      return null;
    }
  }

  private static String signatureDigest(Signature sig) {
    byte[] signature = sig.toByteArray();
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      byte[] digest = md.digest(signature);
      return BaseEncoding.base16().upperCase().encode(digest);
    } catch (NoSuchAlgorithmException e) {
      return null;
    }
  }

  @Provides
  @FirebaseAppScope
  public InAppMessagingSdkServingBlockingStub providesInAppMessagingSdkServingStub(
      Channel channel, Metadata metadata) {
    return InAppMessagingSdkServingGrpc.newBlockingStub(
        ClientInterceptors.intercept(channel, MetadataUtils.newAttachHeadersInterceptor(metadata)));
  }
}
