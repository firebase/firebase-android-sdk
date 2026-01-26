/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.perf.plugin.instrumentation.annotation;

import com.google.firebase.perf.plugin.instrumentation.InstrumentationContext;
import com.google.firebase.perf.plugin.instrumentation.model.AnnotationInfo;
import com.google.firebase.perf.plugin.sdk.FirebaseTrace;
import org.objectweb.asm.commons.AdviceAdapter;

/** The class that processes firebase timer annotations. */
public class FirebaseTimerAnnotationProcessor implements AnnotatedMethodAdapter {

  private static final String TIMER_ANNOTATION_ATTR_NAME = "name";
  private static final String TIMER_ANNOTATION_ATTR_ENABLED = "enabled";

  private final FirebaseTrace firebaseTrace;
  private final AnnotationInfo annotationInfo;

  private boolean traceAdded = false;

  private FirebaseTimerAnnotationProcessor(
      final AdviceAdapter adviceAdapter, final AnnotationInfo annotationInfo) {
    firebaseTrace = new FirebaseTrace(adviceAdapter);
    this.annotationInfo = annotationInfo;
  }

  @Override
  public void onMethodEnter() {
    /*
     * When constructor FirebaseTimerAnnotationProcessor() is called, the annotation attribute
     * "enabled" is not in the "annotationInfo.values" map yet. So we retrieve "enabled" value when
     * "onMethodEnter()" is called.
     */
    Object enabled = annotationInfo.values.get(TIMER_ANNOTATION_ATTR_ENABLED);

    boolean startTrace = (enabled instanceof Boolean) ? (Boolean) enabled : true;

    if (startTrace) {
      firebaseTrace.start(annotationInfo.values.get(TIMER_ANNOTATION_ATTR_NAME).toString());
      traceAdded = true;
    }
  }

  @Override
  public void onMethodExit() {
    if (traceAdded) {
      firebaseTrace.stop();
    }
  }

  public static class Factory implements AnnotatedMethodInstrumentationFactory {

    @Override
    public AnnotatedMethodAdapter newAnnotatedMethodInstrumentation(
        InstrumentationContext instrContext,
        AdviceAdapter adviceAdapter,
        AnnotationInfo annotationInfo,
        String methodName,
        String methodDesc) {

      return new FirebaseTimerAnnotationProcessor(adviceAdapter, annotationInfo);
    }
  }
}
