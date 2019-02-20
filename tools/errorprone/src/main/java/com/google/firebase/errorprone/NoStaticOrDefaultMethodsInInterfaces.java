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

package com.google.firebase.errorprone;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Errorprone custom check that forbids use of static and default methods in interfaces. */
@BugPattern(
    name = "NoStaticOrDefaultMethodsInInterfaces",
    summary =
        "Avoid static/default methods in interfaces: We currently desugar SDKs with retrolambda "
            + "which has limited support for those.",
    severity = BugPattern.SeverityLevel.ERROR)
@AutoService(BugChecker.class)
public class NoStaticOrDefaultMethodsInInterfaces extends BugChecker
    implements BugChecker.MethodTreeMatcher {
  private static final Matcher<MethodTree> WITHIN_INTERFACE =
      Matchers.enclosingClass(new WithinInterface());

  private static final Matcher<MethodTree> HAS_STATIC_METHOD = new HasStaticOrDefaultMethod();

  @Override
  public Description matchMethod(MethodTree tree, VisitorState state) {
    if (WITHIN_INTERFACE.matches(tree, state) && HAS_STATIC_METHOD.matches(tree, state)) {
      return describeMatch(tree);
    }
    return Description.NO_MATCH;
  }

  private static class WithinInterface implements Matcher<ClassTree> {

    @Override
    public boolean matches(ClassTree classTree, VisitorState state) {
      return classTree.getKind() == Tree.Kind.INTERFACE;
    }
  }

  private static class HasStaticOrDefaultMethod implements Matcher<MethodTree> {

    @Override
    public boolean matches(MethodTree methodTree, VisitorState state) {
      Set<Modifier> flags = methodTree.getModifiers().getFlags();
      return flags.contains(Modifier.STATIC) || flags.contains(Modifier.DEFAULT);
    }
  }
}
