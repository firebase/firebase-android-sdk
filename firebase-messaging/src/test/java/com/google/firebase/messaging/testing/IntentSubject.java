package com.google.firebase.messaging.testing;

import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.Truth;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Subject for making assertions about {@link Intent}s.
 *
 * @deprecated use androidx.test.ext.truth.content.IntentSubject instead.
 */
@Deprecated
public final class IntentSubject extends Subject {
    private final Intent actual;

    IntentSubject(FailureMetadata failureMetadata, Intent subject) {
        super(failureMetadata, subject);
        this.actual = subject;
    }

    public static IntentSubject assertThat(Intent intent) {
        return Truth.assertAbout(IntentSubjectFactory.intent()).that(intent);
    }

    /** @see #hasComponentClass(String) */
    public final void hasComponentClass(Class<?> componentClass) {
        hasComponentClass(componentClass.getName());
    }

    public final void hasComponent(String packageName, String className) {
        hasComponentPackage(packageName);
        hasComponentClass(className);
    }

    /** @see #hasComponentClass(Class) */
    public final void hasComponentClass(String className) {
        String subjectClassName = actual.getComponent().getClassName();
        if (!className.equals(subjectClassName)) {
            failWithBadResults("has component class", className, "has", subjectClassName);
        }
    }

    public final void hasComponentPackage(String packageName) {
        String subjectPackageName = actual.getComponent().getPackageName();
        if (!packageName.equals(subjectPackageName)) {
            failWithBadResults("has component package", packageName, "has", subjectPackageName);
        }
    }

    public final void hasPackage(String packageName) {
        String subjectPackage = actual.getPackage();
        if (!packageName.equals(subjectPackage)) {
            failWithBadResults("has package", packageName, "has", subjectPackage);
        }
    }

    public final void hasAction(String action) {
        String subjectAction = actual.getAction();
        if (!action.equals(subjectAction)) {
            failWithBadResults(
                    "has action", action, "has", subjectAction == null ? "no action" : subjectAction);
        }
    }

    public final void hasUri(Uri uri) {
        Uri subjectUri = actual.getData();
        if (!subjectUri.equals(uri)) {
            failWithBadResults("has uri", uri, "has", subjectUri);
        }
    }

    public final void hasType(String type) {
        String subjectType = actual.getType();
        if (!subjectType.equals(type)) {
            failWithBadResults("has type", type, "has", subjectType);
        }
    }

    public final void hasExtra(String key, Object value) {
        Bundle subjectExtras = actual.getExtras();
        if (!subjectExtras.containsKey(key)) {
            failWithBadResults("has key in extras", key, "does not have key", key);
        }
        if (!Objects.equal(subjectExtras.get(key), value)) {
            failWithBadResults("has extra", key + "=" + value, "has", key + "=" + subjectExtras.get(key));
        }
    }

    /**
     * Asserts that the extras have all the same keys and values
     *
     * @see #containsExtras(Bundle)
     */
    public final void hasAllExtras(Bundle extras) {
        Bundle subjectExtras = actual.getExtras();
        Set<String> subjectKeys = subjectExtras.keySet();
        Set<String> keys = extras.keySet();
        if (!keys.equals(subjectKeys)) {
            failWithBadResults("has extras", extras, "has", subjectExtras);
        }

        for (String key : keys) {
            Object value = extras.get(key);
            if (!Objects.equal(subjectExtras.get(key), value)) {
                failWithBadResults("has extras", extras, "has", subjectExtras);
            }
        }
    }

    /**
     * Asserts that all the extras in {@code extras} appear in the Intent's extras. The Intent may
     * contain additional extras that are not found in {@code extras.}
     *
     * @see #hasAllExtras(Bundle) (Bundle)
     */
    public final void containsExtras(Bundle extras) {
        Bundle subjectExtras = actual.getExtras();
        Set<String> keys = new HashSet<>(extras.keySet());
        keys.removeAll(extras.keySet());
        if (!keys.isEmpty()) {
            failWithBadResults("has all keys in", extras.keySet(), "has keys", subjectExtras.keySet());
        }
        for (String key : extras.keySet()) {
            if (!Objects.equal(extras.get(key), subjectExtras.get(key))) {
                failWithBadResults("has extras", extras, "has", subjectExtras);
            }
        }
    }

    public final void hasFlags(Integer... flags) {
        List<Integer> missingFlags = Lists.newArrayList();
        int subjectFlags = actual.getFlags();
        for (Integer flag : flags) {
            if ((subjectFlags & flag) == 0) {
                missingFlags.add(flag);
            }
        }
        List<String> flagNames =
                Lists.transform(
                        missingFlags,
                        new Function<Integer, String>() {
                            @Override
                            public String apply(Integer flag) {
                                return flagToFlagName(flag);
                            }
                        });
        if (!flagNames.isEmpty()) {
            failWithActual("expected to have flags", Joiner.on(", ").join(flagNames));
        }
    }

    public final void hasCategoryThatContains(String categorySubstring) {
        Set<String> categories = actual.getCategories();
        if (categories == null) {
            fail("has any categories with the substring", categorySubstring, "It has no categories.");
        }
        for (String category : categories) {
            if (category.contains(categorySubstring)) {
                return;
            }
        }
        failWithBadResults(
                "has a category with the substring",
                categorySubstring,
                "had the categories: ",
                categories);
    }

    private static String flagToFlagName(int flag) {
        Field[] declaredFields = Intent.class.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            if (Modifier.isStatic(declaredField.getModifiers())) {
                String name = declaredField.getName();
                if (name.startsWith("FLAG_")) {
                    int fieldValue;
                    try {
                        fieldValue = declaredField.getInt(null);
                    } catch (IllegalAccessException e) {
                        return "[unknown flag]";
                    }
                    if (fieldValue == flag) {
                        return name;
                    }
                }
            }
        }
        return "[unknown flag]";
    }

    /** @deprecated Intent doesn't implement equals() */
    @Deprecated
    @Override
    public void isEqualTo(Object object) {
        throw new UnsupportedOperationException(
                "Intent doesn't override equals. Use either exactlyMatches or isSameAs instead.");
    }

    public void exactlyMatches(Intent intent) {
        // TODO(clm) migrate all these to having names
        final Intent subject = actual;
        if (subject == null || intent == null) {
            check().that(subject).isEqualTo(intent);
            return;
        }
        check().that(subject.getAction()).isEqualTo(intent.getAction());
        check().that(subject.getComponent()).isEqualTo(intent.getComponent());
        check().that(subject.getCategories()).isEqualTo(intent.getCategories());
        check().that(subject.getData()).isEqualTo(intent.getData());
        check().that(subject.getFlags()).isEqualTo(intent.getFlags());
        check().that(subject.getPackage()).isEqualTo(intent.getPackage());
        check().that(subject.getScheme()).isEqualTo(intent.getScheme());
        if (Build.VERSION.SDK_INT >= 15) {
            internalAssertThat(subject.getSelector()).exactlyMatches(intent.getSelector());
        }
        check().that(subject.getSourceBounds()).isEqualTo(intent.getSourceBounds());
        check().that(subject.getType()).isEqualTo(intent.getType());
        check()
                .about(BundleSubjectFactory.bundle())
                .that(subject.getExtras())
                .exactlyMatches(intent.getExtras());

        if (Build.VERSION.SDK_INT >= 16) {
            final ClipData clipData = subject.getClipData();
            final ClipData otherClipData = intent.getClipData();
            if (clipData == null) {
                check().that(otherClipData).isNull();
            } else {
                check().that(otherClipData).isNotNull();
                check().that(clipData.getItemCount()).isEqualTo(otherClipData.getItemCount());
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    final Item itemAt = clipData.getItemAt(i);
                    final Item otherItemAt = otherClipData.getItemAt(i);
                    check().that(itemAt.getHtmlText()).isEqualTo(otherItemAt.getHtmlText());
                    check().that(itemAt.getText()).isEqualTo(otherItemAt.getText());
                    check().that(itemAt.getUri()).isEqualTo(otherItemAt.getUri());
                    internalAssertThat(itemAt.getIntent()).exactlyMatches(otherItemAt.getIntent());
                }
            }
        }
    }

    private IntentSubject internalAssertThat(Intent subject) {
        return check().about(IntentSubjectFactory.intent()).that(subject);
    }
}
