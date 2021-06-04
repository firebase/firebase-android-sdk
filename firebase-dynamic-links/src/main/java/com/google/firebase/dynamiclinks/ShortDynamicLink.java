package com.google.firebase.dynamiclinks;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.IntDef;
import com.google.firebase.dynamiclinks.DynamicLink.Builder;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * Response from {@link Builder#buildShortDynamicLink()} that returns the shortened Dynamic Link,
 * link flow chart, and warnings from the requested Dynamic Link.
 */
public interface ShortDynamicLink {

  /** Gets the short Dynamic Link value. */
  @Nullable
  Uri getShortLink();

  /** Gets the preview link to show the link flow chart. */
  @Nullable
  Uri getPreviewLink();

  /** Gets information about potential warnings on link creation. */
  @NonNull
  List<? extends Warning> getWarnings();

  /** Path generation option for short Dynamic Link length */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({Suffix.UNGUESSABLE, Suffix.SHORT})
  @interface Suffix {

    /**
     * Shorten the path to an unguessable string. Such strings are created by base62-encoding
     * randomly generated 96-bit numbers, and consist of 17 alphanumeric characters. Use unguessable
     * strings to prevent your Dynamic Links from being crawled, which can potentially expose
     * sensitive information.
     */
    int UNGUESSABLE = 1;

    /**
     * Shorten the path to a string that is only as long as needed to be unique, with a minimum
     * length of 4 characters. Use this method if sensitive information would not be exposed if a
     * short Dynamic Link URL were guessed.
     */
    int SHORT = 2;
  }

  /** Information about potential warnings on short Dynamic Link creation. */
  interface Warning {

    /**
     * Gets the warning code.
     *
     * @deprecated See {@link #getMessage()} for more information on this warning and how to correct
     *     it.
     */
    @Deprecated
    @Nullable
    String getCode();

    /** Gets the warning message to help developers improve their requests. */
    @Nullable
    String getMessage();
  }
}
