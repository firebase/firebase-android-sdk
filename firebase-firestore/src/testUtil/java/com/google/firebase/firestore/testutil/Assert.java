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

package com.google.firebase.firestore.testutil;

import com.google.firebase.firestore.util.ApiUtil;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;

// JUnit 4.13 class lifted from https://github.com/junit-team/junit4 with thanks.

/**
 * A set of assertion methods useful for writing tests. Only failed assertions
 * are recorded. These methods can be used directly:
 * <code>Assert.assertEquals(...)</code>, however, they read better if they
 * are referenced through static import:
 *
 * <pre>
 * import static org.junit.Assert.*;
 *    ...
 *    assertEquals(...);
 * </pre>
 */
public class Assert {
    /**
     * Protect constructor since it is a static only class
     */
    private Assert() {
    }

    private static boolean equalsRegardingNull(Object expected, Object actual) {
        if (expected == null) {
            return actual == null;
        }

        return isEquals(expected, actual);
    }

    private static boolean isEquals(Object expected, Object actual) {
        return expected.equals(actual);
    }

    static String format(String message, Object expected, Object actual) {
        String formatted = "";
        if (message != null && !"".equals(message)) {
            formatted = message + " ";
        }
        String expectedString = String.valueOf(expected);
        String actualString = String.valueOf(actual);
        if (equalsRegardingNull(expectedString, actualString)) {
            return formatted + "expected: "
                    + formatClassAndValue(expected, expectedString)
                    + " but was: " + formatClassAndValue(actual, actualString);
        } else {
            return formatted + "expected:<" + expectedString + "> but was:<"
                    + actualString + ">";
        }
    }

    private static String formatClass(Class<?> value) {
        String className = value.getCanonicalName();
        return className == null ? value.getName() : className;
    }

    private static String formatClassAndValue(Object value, String valueString) {
        String className = value == null ? "null" : value.getClass().getName();
        return className + "<" + valueString + ">";
    }

    /**
     * Asserts that <code>actual</code> satisfies the condition specified by
     * <code>matcher</code>. If not, an {@link AssertionError} is thrown with
     * information about the matcher and failing value. Example:
     *
     * <pre>
     *   assertThat(0, is(1)); // fails:
     *     // failure message:
     *     // expected: is &lt;1&gt;
     *     // got value: &lt;0&gt;
     *   assertThat(0, is(not(1))) // passes
     * </pre>
     *
     * <code>org.hamcrest.Matcher</code> does not currently document the meaning
     * of its type parameter <code>T</code>.  This method assumes that a matcher
     * typed as <code>Matcher&lt;T&gt;</code> can be meaningfully applied only
     * to values that could be assigned to a variable of type <code>T</code>.
     *
     * @param <T> the static type accepted by the matcher (this can flag obvious
     * compile-time problems such as {@code assertThat(1, is("a"))}
     * @param actual the computed value being compared
     * @param matcher an expression, built of {@link Matcher}s, specifying allowed
     * values
     * @see org.hamcrest.CoreMatchers
     * @see org.hamcrest.MatcherAssert
     * @deprecated use {@code org.hamcrest.junit.MatcherAssert.assertThat()}
     */
    @Deprecated
    public static <T> void assertThat(T actual, Matcher<? super T> matcher) {
        assertThat("", actual, matcher);
    }

    /**
     * Asserts that <code>actual</code> satisfies the condition specified by
     * <code>matcher</code>. If not, an {@link AssertionError} is thrown with
     * the reason and information about the matcher and failing value. Example:
     *
     * <pre>
     *   assertThat(&quot;Help! Integers don't work&quot;, 0, is(1)); // fails:
     *     // failure message:
     *     // Help! Integers don't work
     *     // expected: is &lt;1&gt;
     *     // got value: &lt;0&gt;
     *   assertThat(&quot;Zero is one&quot;, 0, is(not(1))) // passes
     * </pre>
     *
     * <code>org.hamcrest.Matcher</code> does not currently document the meaning
     * of its type parameter <code>T</code>.  This method assumes that a matcher
     * typed as <code>Matcher&lt;T&gt;</code> can be meaningfully applied only
     * to values that could be assigned to a variable of type <code>T</code>.
     *
     * @param reason additional information about the error
     * @param <T> the static type accepted by the matcher (this can flag obvious
     * compile-time problems such as {@code assertThat(1, is("a"))}
     * @param actual the computed value being compared
     * @param matcher an expression, built of {@link Matcher}s, specifying allowed
     * values
     * @see org.hamcrest.CoreMatchers
     * @see org.hamcrest.MatcherAssert
     * @deprecated use {@code org.hamcrest.junit.MatcherAssert.assertThat()}
     */
    @Deprecated
    public static <T> void assertThat(String reason, T actual,
                                      Matcher<? super T> matcher) {
        MatcherAssert.assertThat(reason, actual, matcher);
    }

    /**
     * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
     * executed. If it does not throw an exception, an {@link AssertionError} is thrown. If it
     * throws the wrong type of exception, an {@code AssertionError} is thrown describing the
     * mismatch; the exception that was actually thrown can be obtained by calling {@link
     * AssertionError#getCause}.
     *
     * @param expectedThrowable the expected type of the exception
     * @param runnable       a function that is expected to throw an exception when executed
     * @since 4.13
     */
    public static void assertThrows(Class<? extends Throwable> expectedThrowable, ThrowingRunnable runnable) {
        expectThrows(expectedThrowable, runnable);
    }

    /**
     * Asserts that {@code runnable} throws an exception of type {@code expectedThrowable} when
     * executed. If it does, the exception object is returned. If it does not throw an exception, an
     * {@link AssertionError} is thrown. If it throws the wrong type of exception, an {@code
     * AssertionError} is thrown describing the mismatch; the exception that was actually thrown can
     * be obtained by calling {@link AssertionError#getCause}.
     *
     * @param expectedThrowable the expected type of the exception
     * @param runnable       a function that is expected to throw an exception when executed
     * @return the exception thrown by {@code runnable}
     * @since 4.13
     */
    private static <T extends Throwable> T expectThrows(Class<T> expectedThrowable, ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable actualThrown) {
            if (expectedThrowable.isInstance(actualThrown)) {
                @SuppressWarnings("unchecked") T retVal = (T) actualThrown;
                return retVal;
            } else {
                String expected = formatClass(expectedThrowable);
                Class<? extends Throwable> actualThrowable = actualThrown.getClass();
                String actual = formatClass(actualThrowable);
                if (expected.equals(actual)) {
                    // There must be multiple class loaders. Add the identity hash code so the message
                    // doesn't say "expected: java.lang.String<my.package.MyException> ..."
                    expected += "@" + Integer.toHexString(System.identityHashCode(expectedThrowable));
                    actual += "@" + Integer.toHexString(System.identityHashCode(actualThrowable));
                }
                String mismatchMessage = format("unexpected exception type thrown;", expected, actual);

                throw ApiUtil.newAssertionError(mismatchMessage, actualThrown);
            }
        }
        String message = String.format("expected %s to be thrown, but nothing was thrown",
                formatClass(expectedThrowable));
        throw new AssertionError(message);
    }
}
