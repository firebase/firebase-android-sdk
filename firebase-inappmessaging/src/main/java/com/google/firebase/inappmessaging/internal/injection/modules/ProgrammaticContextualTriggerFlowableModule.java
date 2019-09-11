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

import static io.reactivex.BackpressureStrategy.BUFFER;

import com.google.firebase.inappmessaging.internal.ForegroundNotifier;
import com.google.firebase.inappmessaging.internal.ProgramaticContextualTriggers;
import com.google.firebase.inappmessaging.internal.injection.qualifiers.ProgrammaticTrigger;
import dagger.Module;
import dagger.Provides;
import io.reactivex.Flowable;
import io.reactivex.flowables.ConnectableFlowable;
import javax.inject.Singleton;

/**
 * Bindings for programatic contextual triggers created by {@link ForegroundNotifier}
 *
 * @hide
 */
@Module
public class ProgrammaticContextualTriggerFlowableModule {
  private ProgramaticContextualTriggers triggers;

  public ProgrammaticContextualTriggerFlowableModule(ProgramaticContextualTriggers triggers) {
    this.triggers = triggers;
  }

  @Provides
  @Singleton
  @ProgrammaticTrigger
  public ProgramaticContextualTriggers providesProgramaticContextualTriggers() {
    return triggers;
  }

  @Provides
  @Singleton
  @ProgrammaticTrigger
  public ConnectableFlowable<String> providesProgramaticContextualTriggerStream() {

    ConnectableFlowable<String> flowable =
        Flowable.<String>create(e -> triggers.setListener((trigger) -> e.onNext(trigger)), BUFFER)
            .publish();

    flowable.connect();
    // We ignore the subscription since this connected flowable is expected to last the lifetime of
    // the app.
    return flowable;
  }
}
