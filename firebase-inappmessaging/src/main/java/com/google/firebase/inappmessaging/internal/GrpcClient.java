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

package com.google.firebase.inappmessaging.internal;

import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsRequest;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc.InAppMessagingSdkServingBlockingStub;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

/**
 * Wrapper for rpc calls to the fiam backend
 *
 * @hide
 */
@FirebaseAppScope
public class GrpcClient {
  private final InAppMessagingSdkServingBlockingStub stub;

  @Inject
  GrpcClient(InAppMessagingSdkServingBlockingStub stub) {
    this.stub = stub;
  }

  public FetchEligibleCampaignsResponse fetchEligibleCampaigns(FetchEligibleCampaignsRequest req) {
    return stub.withDeadlineAfter(30000, TimeUnit.MILLISECONDS).fetchEligibleCampaigns(req);
  }
}
