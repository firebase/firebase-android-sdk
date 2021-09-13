package com.google.firebase.messaging.testing;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import androidx.test.ext.truth.internal.FlagUtil;
import androidx.test.ext.truth.os.BundleSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.IterableSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.util.List;

/** Subject for making assertions about {@link Intent}s. */
public final class IntentSubject extends Subject {

  public static IntentSubject assertThat(Intent intent) {
    return Truth.assertAbout(intents()).that(intent);
  }

  public static Subject.Factory<IntentSubject, Intent> intents() {
    return IntentSubject::new;
  }

  private final Intent actual;

  private IntentSubject(FailureMetadata failureMetadata, Intent subject) {
    super(failureMetadata, subject);
    this.actual = subject;
  }

  /** @see #hasComponentClass(String) */
  public final void hasComponentClass(Class<?> componentClass) {
    hasComponentClass(componentClass.getName());
  }

  public final void hasComponent(String packageName, String className) {
    hasComponentPackage(packageName);
    hasComponentClass(className);
  }

  public final void hasComponent(ComponentName component) {
    hasComponent(component.getPackageName(), component.getClassName());
  }

  /** @see #hasComponentClass(Class) */
  public final void hasComponentClass(String className) {
    check("getComponent().getClassName()")
        .that(actual.getComponent().getClassName())
        .isEqualTo(className);
  }

  public final void hasComponentPackage(String packageName) {
    check("getComponent().getPackageName()")
        .that(actual.getComponent().getPackageName())
        .isEqualTo(packageName);
  }

  public final void hasPackage(String packageName) {
    check("getPackage()").that(actual.getPackage()).isEqualTo(packageName);
  }

  public final void hasAction(String action) {
    check("getAction()").that(actual.getAction()).isEqualTo(action);
  }

  public final void hasNoAction() {
    hasAction(null);
  }

  public final void hasData(Uri uri) {
    check("getData()").that(actual.getData()).isEqualTo(uri);
  }

  public final void hasType(String type) {
    check("getType()").that(actual.getType()).isEqualTo(type);
  }

  public final BundleSubject extras() {
    return check("getExtras()").about(BundleSubject.bundles()).that(actual.getExtras());
  }

  public final IterableSubject categories() {
    return check("getCategories()").that(actual.getCategories());
  }

  /** Assert that the intent has the given flag set. */
  public final void hasFlags(int flag) {
    List<String> actualFlags = FlagUtil.flagNames(actual.getFlags());
    List<String> expectedFlags = FlagUtil.flagNames(flag);
    check("getFlags()").that(actualFlags).containsAtLeastElementsIn(expectedFlags);
  }

  public final void filtersEquallyTo(Intent intent) {
    if (!actual.filterEquals(intent)) {
      failWithActual("expected to be equal for intent filters to", intent);
    }
  }
}
