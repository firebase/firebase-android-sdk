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

import static com.google.errorprone.matchers.Matchers.allOf;
import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.enclosingClass;
import static com.google.errorprone.matchers.Matchers.enclosingMethod;
import static com.google.errorprone.matchers.Matchers.hasAnnotation;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.isStatic;
import static com.google.errorprone.matchers.Matchers.methodHasVisibility;
import static com.google.errorprone.matchers.Matchers.methodInvocation;
import static com.google.errorprone.matchers.Matchers.methodIsNamed;
import static com.google.errorprone.matchers.Matchers.methodReturns;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isSubtype;

import com.google.auto.service.AutoService;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.AbstractTypeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.MethodVisibility;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;

/**
 * Errorprone custom check that discourages use of FirebaseApp#get(Class) as it is only intended for
 * use in Sdk#getInstance() methods.
 */
@BugPattern(
    name = "FirebaseUseExplicitDependencies",
    summary =
        "Use of FirebaseApp#get(Class) is discouraged, and is only acceptable"
            + " in SDK#getInstance(...) methods. Instead declare dependencies explicitly in"
            + " your ComponentRegistrar and inject.",
    severity = BugPattern.SeverityLevel.ERROR)
@AutoService(BugChecker.class)
public class ComponentsAppGetCheck extends BugChecker
    implements BugChecker.MethodInvocationTreeMatcher {
  private static final String FIREBASE_APP = "com.google.firebase.FirebaseApp";
  private static final String GET_COMPONENT_METHOD = "<T>get(java.lang.Class<T>)";

  private static final Matcher<ExpressionTree> CALL_TO_GET =
      methodInvocation(
          instanceMethod().onExactClass(FIREBASE_APP).withSignature(GET_COMPONENT_METHOD));

  /**
   * This matches methods of the forms:
   *
   * <pre>{@code
   * class Foo {
   *     public static Foo getInstance(/* any number of parameters * /);
   * }
   *
   * class Foo extends/implements Bar {
   *     public static Bar getInstance(/* any number of parameters * /);
   * }
   * }</pre>
   */
  private static final Matcher<ExpressionTree> WITHIN_GET_INSTANCE =
      enclosingMethod(
          allOf(
              isStatic(),
              methodIsNamed("getInstance"),
              methodReturns(
                  isSupertypeOf(
                      state -> ASTHelpers.getType(state.findEnclosing(ClassTree.class))))));

  /**
   * This matches methods of the forms:
   *
   * <pre>{@code
   * class Foo {
   *     private static Foo getInstanceImpl(/* any number of parameters * /);
   * }
   *
   * class Foo extends/implements Bar {
   *     private static Bar getInstanceImpl(/* any number of parameters * /);
   * }
   * }</pre>
   */
  private static final Matcher<ExpressionTree> WITHIN_GET_INSTANCE_IMPL =
      enclosingMethod(
          allOf(
              isStatic(),
              methodHasVisibility(MethodVisibility.Visibility.PRIVATE),
              methodIsNamed("getInstanceImpl"),
              methodReturns(
                  isSupertypeOf(
                      state -> ASTHelpers.getType(state.findEnclosing(ClassTree.class))))));

  private static final Matcher<ExpressionTree> WITHIN_JUNIT_TEST =
      enclosingClass(hasAnnotation("org.junit.runner.RunWith"));

  private static final Matcher<ExpressionTree> ALLOWED_USAGES =
      anyOf(WITHIN_GET_INSTANCE, WITHIN_GET_INSTANCE_IMPL, WITHIN_JUNIT_TEST);

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    if (ALLOWED_USAGES.matches(tree, state) || !CALL_TO_GET.matches(tree, state)) {
      return Description.NO_MATCH;
    }
    return describeMatch(tree);
  }

  private static <T extends Tree> Matcher<T> isSupertypeOf(Supplier<Type> type) {
    return new IsSupertypeOf<T>(type);
  }

  private static class IsSupertypeOf<T extends Tree> extends AbstractTypeMatcher<T> {

    public IsSupertypeOf(Supplier<Type> typeToCompareSupplier) {
      super(typeToCompareSupplier);
    }

    public IsSupertypeOf(String typeString) {
      super(typeString);
    }

    @Override
    public boolean matches(T tree, VisitorState state) {
      return isSubtype(typeToCompareSupplier.get(state), getType(tree), state);
    }
  }
}
