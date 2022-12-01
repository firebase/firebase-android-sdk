package com.google.firebase.remoteconfig;

import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;
import java.util.Set;

/**
 * Information about the updated config passed to the {@code onUpdate} callback of {@link
 * ConfigUpdateListener}.
 */
@AutoValue
public abstract class ConfigUpdate {
  @NonNull
  public static ConfigUpdate create(@NonNull Set<String> updatedParams) {
    return new AutoValue_ConfigUpdate(updatedParams);
  }

  /**
   * Parameter keys whose values have been updated from the currently activated values. Includes
   * keys that are added, deleted, and whose value, value source, or metadata has changed.
   */
  @NonNull
  abstract Set<String> getUpdatedParams();
}
