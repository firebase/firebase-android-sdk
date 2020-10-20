// Copyright 2020 Google LLC
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

package com.google.firebase.errorprone;

import static com.google.errorprone.matchers.Matchers.enclosingMethod;
import static com.google.errorprone.matchers.Matchers.hasAnnotation;
import static com.google.errorprone.matchers.Matchers.methodInvocation;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;

@BugPattern(
    name = "DeferredListenerApiCheck",
    summary =
        "Use of FirebaseApp#get(Class) is discouraged, and is only acceptable"
            + " in SDK#getInstance(...) methods. Instead declare dependencies explicitly in"
            + " your ComponentRegistrar and inject.",
    severity = BugPattern.SeverityLevel.ERROR)
@AutoService(BugChecker.class)
public class DeferredListenerApiCheck extends BugChecker implements BugChecker.MethodInvocationTreeMatcher {

  private static final Matcher<ExpressionTree> LISTENER_API_INVOCATION =
      methodInvocation(
          (expressionTree, state) -> ASTHelpers.hasAnnotation(ASTHelpers.getSymbol(expressionTree),
              "com.google.firebase.components.annotations.ListenerApi", state));

  private static final Matcher<ExpressionTree> WITHIN_LISTENER_API =
      enclosingMethod(hasAnnotation("com.google.firebase.components.annotations.ListenerApi"));

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if(!LISTENER_API_INVOCATION.matches(tree, state)) {
      return Description.NO_MATCH;
    }
    if(WITHIN_LISTENER_API.matches(tree, state)) {
      return Description.NO_MATCH;
    }
    return describeMatch(tree);
  }
}
