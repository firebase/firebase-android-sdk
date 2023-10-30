package com.google.firebase.processinfo;

import androidx.annotation.NonNull;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ProcessDetails {
    @NonNull
    public abstract String getProcessName();

    public abstract int getPid();

    public abstract int getImportance();

    public abstract boolean isDefaultProcess();

    @NonNull
    public static Builder builder() {
        return new AutoValue_ProcessDetails
                .Builder();
    }

    /** Builder for {@link ProcessDetails}. */
    @AutoValue.Builder
    public abstract static class Builder {
        @NonNull
        public abstract Builder setProcessName(@NonNull String processName);

        @NonNull
        public abstract Builder setPid(int pid);

        @NonNull
        public abstract Builder setImportance(int importance);

        @NonNull
        public abstract Builder setDefaultProcess(boolean isDefaultProcess);

        @NonNull
        public abstract ProcessDetails build();
    }
}
