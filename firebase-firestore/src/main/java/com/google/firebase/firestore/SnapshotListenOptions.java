// Copyright 2024 Google LLC
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

package com.google.firebase.firestore;

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import android.app.Activity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.util.Executors;
import java.util.concurrent.Executor;

/**
 * An options object that configures the behavior of {@code addSnapshotListener()} calls. Instances
 * of this class control settings such as whether metadata-only changes trigger events, the
 * preferred data source (server or cache), and the executor for listener callbacks.
 */
public final class SnapshotListenOptions {
  /** Indicates whether metadata-only changes should trigger snapshot events. */
  private final MetadataChanges metadataChanges;
  /** Specifies the data source for the listener. */
  private final ListenSource source;
  /** The executor to use to call the listener. */
  private final Executor executor;
  /** The activity to scope the listener to. */
  private final Activity activity;

  private SnapshotListenOptions(Builder builder) {
    this.metadataChanges = builder.metadataChanges;
    this.source = builder.source;
    this.executor = builder.executor;
    this.activity = builder.activity;
  }

  /**
   * Returns the setting for whether metadata-only changes should trigger snapshot events.
   *
   * @return The setting object for metadata-only changes.
   */
  @NonNull
  public MetadataChanges getMetadataChanges() {
    return metadataChanges;
  }

  /**
   * Returns the preferred source for retrieving data in snapshot listeners.
   *
   * @return The listen source for retrieving data.
   */
  @NonNull
  public ListenSource getSource() {
    return source;
  }

  /**
   * Returns the executor that will be used to execute the snapshot listener's callback.
   *
   * @return The executor for snapshot listener callbacks.
   */
  @NonNull
  public Executor getExecutor() {
    return executor;
  }

  /**
   * Returns the optional Activity that scopes this snapshot listener's lifespan. If provided, the
   * listener will automatically stop receiving events when the activity is destroyed.
   *
   * @return The Activity associated with this listener, or null if no activity is set.
   */
  @Nullable
  public Activity getActivity() {
    return activity;
  }

  /** Builder for constructing {@link SnapshotListenOptions} instances. */
  public static class Builder {
    private MetadataChanges metadataChanges = MetadataChanges.EXCLUDE;
    private ListenSource source = ListenSource.DEFAULT;
    private Executor executor = Executors.DEFAULT_CALLBACK_EXECUTOR;
    private Activity activity = null;

    public Builder() {}

    /**
     * Sets whether metadata-only changes should trigger snapshot events.
     *
     * @param metadataChanges The setting for metadata-only changes.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder setMetadataChanges(@NonNull MetadataChanges metadataChanges) {
      checkNotNull(metadataChanges, "metadataChanges must not be null.");
      this.metadataChanges = metadataChanges;
      return this;
    }

    /**
     * Sets the preferred source for retrieving data in snapshot listeners.
     *
     * @param source The preferred source for data retrieval.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder setSource(@NonNull ListenSource source) {
      checkNotNull(source, "listen source must not be null.");
      this.source = source;
      return this;
    }

    /**
     * Sets the executor to be used for snapshot listener callbacks.
     *
     * @param executor The executor to be used.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder setExecutor(@NonNull Executor executor) {
      checkNotNull(executor, "executor must not be null.");
      this.executor = executor;
      return this;
    }

    /**
     * Associates an Activity with this snapshot listener's lifecycle. If set, the listener will
     * automatically stop when the Activity is destroyed.
     *
     * @param activity The Activity to associate with the listener.
     * @return This Builder instance to allow chaining of method calls.
     */
    @NonNull
    public Builder setActivity(@NonNull Activity activity) {
      checkNotNull(activity, "activity must not be null.");
      this.activity = activity;
      return this;
    }

    /**
     * Constructs a {@link SnapshotListenOptions} instance using the current settings in this
     * Builder.
     *
     * @return The constructed SnapshotListenOptions instance.
     */
    @NonNull
    public SnapshotListenOptions build() {
      return new SnapshotListenOptions(this);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SnapshotListenOptions that = (SnapshotListenOptions) o;
    return metadataChanges == that.metadataChanges
        && source == that.source
        && executor.equals(that.executor)
        && activity.equals(that.activity);
  }

  @Override
  public int hashCode() {
    int result = metadataChanges.hashCode();
    result = 31 * result + source.hashCode();
    result = 31 * result + executor.hashCode();
    result = 31 * result + (activity != null ? activity.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SnapshotListenOptions{"
        + "metadataChanges="
        + metadataChanges
        + ", source="
        + source
        + ", executor="
        + executor
        + ", activity="
        + activity
        + '}';
  }
}
