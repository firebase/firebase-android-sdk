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

package com.google.firebase.installations;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class AwaitListener implements OnCompleteListener<Void> {
  private final CountDownLatch latch = new CountDownLatch(1);

  public void onSuccess() {
    latch.countDown();
  }

  public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
    return latch.await(timeout, unit);
  }

  @Override
  public void onComplete(@NonNull Task<Void> task) {
    latch.countDown();
  }
}
