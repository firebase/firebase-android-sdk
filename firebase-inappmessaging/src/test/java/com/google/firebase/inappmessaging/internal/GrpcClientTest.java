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

import static com.google.firebase.inappmessaging.testutil.Assert.assertThrows;
import static junit.framework.Assert.assertEquals;

import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsRequest;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.FetchEligibleCampaignsResponse;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc.InAppMessagingSdkServingBlockingStub;
import com.google.internal.firebase.inappmessaging.v1.sdkserving.InAppMessagingSdkServingGrpc.InAppMessagingSdkServingImplBase;
import io.grpc.ClientInterceptors;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class GrpcClientTest {
  private static final String TEST_PROJECT_NUMBER = "123";
  private final Metadata testMetadata = new Metadata();
  private final FetchEligibleCampaignsResponse testFetchEligibleCampaignsResponse =
      FetchEligibleCampaignsResponse.getDefaultInstance();
  private final FetchEligibleCampaignsRequest fetchEligibleCampaignsRequest =
      FetchEligibleCampaignsRequest.newBuilder().setProjectNumber(TEST_PROJECT_NUMBER).build();
  @Rule public GrpcServerRule grpcServerRule = new GrpcServerRule().directExecutor();
  private GrpcClient grpcClient;
  private InAppMessagingSdkServingBlockingStub inAppMessagingSdkServingBlockingStub;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    inAppMessagingSdkServingBlockingStub =
        InAppMessagingSdkServingGrpc.newBlockingStub(
            ClientInterceptors.intercept(
                grpcServerRule.getChannel(),
                MetadataUtils.newAttachHeadersInterceptor(testMetadata)));
    grpcClient = new GrpcClient(inAppMessagingSdkServingBlockingStub);
  }

  @Test
  public void testFetchEligibleCampaignsSuccess() {
    grpcServerRule
        .getServiceRegistry()
        .addService(new FakeFetchService(r -> assertEquals(fetchEligibleCampaignsRequest, r)));

    assertEquals(
        testFetchEligibleCampaignsResponse,
        grpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequest));
  }

  @Test
  public void testFetchEligibleCampaignsFailure() {
    grpcServerRule
        .getServiceRegistry()
        .addService(
            new FakeFetchService(
                r -> {
                  throw new RuntimeException("any exception");
                }));

    assertThrows(
        StatusRuntimeException.class,
        () -> grpcClient.fetchEligibleCampaigns(fetchEligibleCampaignsRequest));
  }

  interface Callback {
    void exec(FetchEligibleCampaignsRequest r);
  }

  class FakeFetchService extends InAppMessagingSdkServingImplBase {
    private final Callback mCallback;

    protected FakeFetchService(Callback mCallback) {
      this.mCallback = mCallback;
    }

    @Override
    public void fetchEligibleCampaigns(
        FetchEligibleCampaignsRequest request,
        StreamObserver<FetchEligibleCampaignsResponse> responseObserver) {
      if (mCallback != null) {
        mCallback.exec(request);
      }
      responseObserver.onNext(testFetchEligibleCampaignsResponse);
      responseObserver.onCompleted();
    }
  }
}
