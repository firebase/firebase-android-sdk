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

package com.google.firebase.crashlytics.internal.model;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event;
import com.google.firebase.crashlytics.internal.model.CrashlyticsReport.Session.Event.Application.Execution.Thread.Frame;
import com.google.firebase.encoders.annotations.Encodable;
import com.google.firebase.encoders.annotations.Encodable.Field;
import com.google.firebase.encoders.annotations.Encodable.Ignore;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.Charset;

/**
 * This class represents the data captured by and reported to Crashlytics.
 *
 * <p>It is an immutable value class implemented by AutoValue.
 *
 * @see <a
 *     href="https://github.com/google/auto/tree/master/value">https://github.com/google/auto/tree/master/value</a>
 */
@Encodable
@AutoValue
public abstract class CrashlyticsReport {

  @IntDef({
    Architecture.ARMV6,
    Architecture.ARMV7,
    Architecture.ARM64,
    Architecture.X86_32,
    Architecture.X86_64,
    Architecture.UNKNOWN
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface Architecture {
    int ARMV6 = 5;
    int ARMV7 = 6;
    int ARM64 = 9;
    int X86_32 = 0;
    int X86_64 = 1;
    int UNKNOWN = 7;
  }

  private static final Charset UTF_8 = Charset.forName("UTF-8");

  @NonNull
  public static Builder builder() {
    return new AutoValue_CrashlyticsReport.Builder();
  }

  @NonNull
  public abstract String getSdkVersion();

  @NonNull
  public abstract String getGmpAppId();

  public abstract int getPlatform();

  @NonNull
  public abstract String getInstallationUuid();

  @NonNull
  public abstract String getBuildVersion();

  @NonNull
  public abstract String getDisplayVersion();

  @NonNull
  public abstract Session getSession();

  /**
   * Augment an existing {@link CrashlyticsReport} with a given list of events.
   *
   * @return a new {@link CrashlyticsReport} with its events list set to the given list of events.
   */
  @NonNull
  public CrashlyticsReport withEvents(@NonNull ImmutableList<Event> events) {
    return toBuilder().setSession(getSession().withEvents(events)).build();
  }

  /**
   * Augment an existing {@link CrashlyticsReport} with a given organization ID.
   *
   * @return a new {@link CrashlyticsReport} with its Session.Application.Organization object
   *     containing the given organization ID.
   */
  @NonNull
  public CrashlyticsReport withOrganizationId(@NonNull String organizationId) {
    return toBuilder().setSession(getSession().withOrganizationId(organizationId)).build();
  }

  // TODO
  // @Nullable
  // public abstract byte[] getNdkPayload();

  @NonNull
  protected abstract Builder toBuilder();

  @AutoValue
  public abstract static class CustomAttribute {

    @NonNull
    public static Builder builder() {
      return new AutoValue_CrashlyticsReport_CustomAttribute.Builder();
    }

    @NonNull
    public abstract String getKey();

    @NonNull
    public abstract String getValue();

    /** Builder for {@link CustomAttribute}. */
    @AutoValue.Builder
    public abstract static class Builder {

      @NonNull
      public abstract Builder setKey(@NonNull String value);

      @NonNull
      public abstract Builder setValue(@NonNull String value);

      @NonNull
      public abstract CustomAttribute build();
    }
  }

  @AutoValue
  public abstract static class Session {

    @NonNull
    public static Builder builder() {
      return new AutoValue_CrashlyticsReport_Session.Builder();
    }

    @NonNull
    public abstract String getGenerator();

    @Ignore
    @NonNull
    public abstract String getIdentifier();

    @Field(name = "identifier")
    @NonNull
    public byte[] getIdentifierUtf8Bytes() {
      return getIdentifier().getBytes(UTF_8);
    }

    public abstract long getStartedAt();

    @NonNull
    public abstract Application getApp();

    @Nullable
    public abstract User getUser();

    @Nullable
    public abstract OperatingSystem getOs();

    @Nullable
    public abstract Device getDevice();

    @Nullable
    public abstract ImmutableList<Event> getEvents();

    @NonNull
    protected abstract Builder toBuilder();

    @NonNull
    Session withEvents(@NonNull ImmutableList<Event> events) {
      return toBuilder().setEvents(events).build();
    }

    @NonNull
    Session withOrganizationId(@NonNull String organizationId) {
      final Application app = getApp().withOrganizationId(organizationId);
      return toBuilder().setApp(app).build();
    }

    /** Builder for {@link Session}. */
    @AutoValue.Builder
    public abstract static class Builder {

      @NonNull
      public abstract Builder setGenerator(@NonNull String generator);

      @NonNull
      public abstract Builder setIdentifier(@NonNull String identifier);

      @NonNull
      public Builder setIdentifierFromUtf8Bytes(@NonNull byte[] utf8Bytes) {
        return setIdentifier(new String(utf8Bytes, UTF_8));
      }

      @NonNull
      public abstract Builder setStartedAt(long startedAt);

      @NonNull
      public abstract Builder setUser(@NonNull User value);

      @NonNull
      public abstract Builder setApp(@NonNull Application application);

      @NonNull
      public abstract Builder setOs(@NonNull OperatingSystem value);

      @NonNull
      public abstract Builder setDevice(@NonNull Device value);

      @NonNull
      public abstract Builder setEvents(@NonNull ImmutableList<Event> value);

      @NonNull
      public abstract Session build();
    }

    @AutoValue
    public abstract static class User {

      @NonNull
      public static Builder builder() {
        return new AutoValue_CrashlyticsReport_Session_User.Builder();
      }

      @NonNull
      public abstract String getIdentifier();

      /** Builder for {@link User}. */
      @AutoValue.Builder
      public abstract static class Builder {

        @NonNull
        public abstract Builder setIdentifier(@NonNull String value);

        @NonNull
        public abstract User build();
      }
    }

    @AutoValue
    public abstract static class Application {

      @NonNull
      public static Builder builder() {
        return new AutoValue_CrashlyticsReport_Session_Application.Builder();
      }

      @NonNull
      public abstract String getIdentifier();

      @NonNull
      public abstract String getVersion();

      @Nullable
      public abstract String getDisplayVersion();

      @Nullable
      public abstract Organization getOrganization();

      @Nullable
      public abstract String getInstallationUuid();

      @NonNull
      protected abstract Builder toBuilder();

      @NonNull
      Application withOrganizationId(@NonNull String organizationId) {
        final Organization organization = getOrganization();
        final Organization.Builder builder =
            (organization != null) ? organization.toBuilder() : Organization.builder();
        return toBuilder().setOrganization(builder.setClsId(organizationId).build()).build();
      }

      /** Builder for {@link Application}. */
      @AutoValue.Builder
      public abstract static class Builder {

        @NonNull
        public abstract Builder setIdentifier(@NonNull String identifier);

        @NonNull
        public abstract Builder setVersion(@NonNull String version);

        @NonNull
        public abstract Builder setDisplayVersion(@NonNull String displayVersion);

        @NonNull
        public abstract Builder setOrganization(@NonNull Organization value);

        @NonNull
        public abstract Builder setInstallationUuid(@NonNull String installationUuid);

        @NonNull
        public abstract Application build();
      }

      @AutoValue
      public abstract static class Organization {

        @NonNull
        public static Builder builder() {
          return new AutoValue_CrashlyticsReport_Session_Application_Organization.Builder();
        }

        @NonNull
        public abstract String getClsId();

        @NonNull
        protected abstract Builder toBuilder();

        /** Builder for {@link Organization}. */
        @AutoValue.Builder
        public abstract static class Builder {

          @NonNull
          public abstract Builder setClsId(@NonNull String value);

          @NonNull
          public abstract Organization build();
        }
      }
    }

    @AutoValue
    public abstract static class OperatingSystem {

      @NonNull
      public static Builder builder() {
        return new AutoValue_CrashlyticsReport_Session_OperatingSystem.Builder();
      }

      public abstract int getPlatform();

      @NonNull
      public abstract String getVersion();

      @NonNull
      public abstract String getBuildVersion();

      public abstract boolean isJailbroken();

      /** Builder for {@link OperatingSystem}. */
      @AutoValue.Builder
      public abstract static class Builder {

        @NonNull
        public abstract Builder setPlatform(int value);

        @NonNull
        public abstract Builder setVersion(@NonNull String value);

        @NonNull
        public abstract Builder setBuildVersion(@NonNull String value);

        @NonNull
        public abstract Builder setJailbroken(boolean value);

        @NonNull
        public abstract OperatingSystem build();
      }
    }

    @AutoValue
    public abstract static class Device {

      @NonNull
      public static Builder builder() {
        return new AutoValue_CrashlyticsReport_Session_Device.Builder();
      }

      @Architecture
      @NonNull
      public abstract int getArch();

      @NonNull
      public abstract String getModel();

      public abstract int getCores();

      public abstract long getRam();

      public abstract long getDiskSpace();

      public abstract boolean isSimulator();

      public abstract int getState(); // TODO Use DeviceState enum here for Bitmasking

      @NonNull
      public abstract String getManufacturer();

      @NonNull
      public abstract String getModelClass();

      /** Builder for {@link Device}. */
      @AutoValue.Builder
      public abstract static class Builder {

        @NonNull
        public abstract Builder setArch(@Architecture int value);

        @NonNull
        public abstract Builder setModel(@NonNull String value);

        @NonNull
        public abstract Builder setCores(int value);

        @NonNull
        public abstract Builder setRam(long value);

        @NonNull
        public abstract Builder setDiskSpace(long value);

        @NonNull
        public abstract Builder setSimulator(boolean value);

        @NonNull
        public abstract Builder setState(int value);

        @NonNull
        public abstract Builder setManufacturer(@NonNull String value);

        @NonNull
        public abstract Builder setModelClass(@NonNull String value);

        @NonNull
        public abstract Device build();
      }
    }

    @AutoValue
    public abstract static class Event {

      @NonNull
      public static Builder builder() {
        return new AutoValue_CrashlyticsReport_Session_Event.Builder();
      }

      public abstract long getTimestamp();

      @NonNull
      public abstract String getType();

      @NonNull
      public abstract Application getApp();

      @NonNull
      public abstract Device getDevice();

      @Nullable
      public abstract Log getLog();

      @AutoValue
      public abstract static class Application {

        @NonNull
        public static Builder builder() {
          return new AutoValue_CrashlyticsReport_Session_Event_Application.Builder();
        }

        @NonNull
        public abstract Execution getExecution();

        @Nullable
        public abstract ImmutableList<CustomAttribute> getCustomAttributes();

        public abstract boolean isBackground();

        public abstract int getUiOrientation();

        @AutoValue
        public abstract static class Execution {

          @NonNull
          public static Builder builder() {
            return new AutoValue_CrashlyticsReport_Session_Event_Application_Execution.Builder();
          }

          @NonNull
          public abstract ImmutableList<Thread> getThreads();

          @NonNull
          public abstract Exception getException();

          @NonNull
          public abstract Signal getSignal();

          @NonNull
          public abstract ImmutableList<BinaryImage> getBinaries();

          @AutoValue
          public abstract static class Thread {

            @NonNull
            public static Builder builder() {
              return new AutoValue_CrashlyticsReport_Session_Event_Application_Execution_Thread
                  .Builder();
            }

            @NonNull
            public abstract String getName();

            public abstract int getImportance();

            @NonNull
            public abstract ImmutableList<Frame> getFrames();

            @AutoValue
            public abstract static class Frame {

              @NonNull
              public static Builder builder() {
                return new AutoValue_CrashlyticsReport_Session_Event_Application_Execution_Thread_Frame
                    .Builder();
              }

              public abstract long getPc();

              @NonNull
              public abstract String getSymbol();

              @Nullable
              public abstract String getFile();

              public abstract long getOffset();

              public abstract int getImportance();

              /** Builder for {@link Frame}. */
              @AutoValue.Builder
              public abstract static class Builder {

                @NonNull
                public abstract Builder setPc(long value);

                @NonNull
                public abstract Builder setSymbol(@NonNull String value);

                @NonNull
                public abstract Builder setFile(@NonNull String value);

                @NonNull
                public abstract Builder setOffset(long value);

                @NonNull
                public abstract Builder setImportance(int value);

                @NonNull
                public abstract Frame build();
              }
            }

            /** Builder for {@link Thread}. */
            @AutoValue.Builder
            public abstract static class Builder {

              @NonNull
              public abstract Builder setName(@NonNull String value);

              @NonNull
              public abstract Builder setImportance(int value);

              @NonNull
              public abstract Builder setFrames(@NonNull ImmutableList<Frame> value);

              @NonNull
              public abstract Thread build();
            }
          }

          @AutoValue
          public abstract static class Exception {

            @NonNull
            public static Builder builder() {
              return new AutoValue_CrashlyticsReport_Session_Event_Application_Execution_Exception
                  .Builder();
            }

            @NonNull
            public abstract String getType();

            @NonNull
            public abstract String getReason();

            @NonNull
            public abstract ImmutableList<Frame> getFrames();

            @Nullable
            public abstract Exception getCausedBy();

            public abstract int getOverflowCount();

            /** Builder for {@link Exception}. */
            @AutoValue.Builder
            public abstract static class Builder {

              @NonNull
              public abstract Builder setType(@NonNull String value);

              @NonNull
              public abstract Builder setReason(@NonNull String value);

              @NonNull
              public abstract Builder setFrames(@NonNull ImmutableList<Frame> value);

              @NonNull
              public abstract Builder setCausedBy(@NonNull Exception value);

              @NonNull
              public abstract Builder setOverflowCount(int value);

              @NonNull
              public abstract Exception build();
            }
          }

          @AutoValue
          public abstract static class Signal {

            @NonNull
            public static Builder builder() {
              return new AutoValue_CrashlyticsReport_Session_Event_Application_Execution_Signal
                  .Builder();
            }

            @NonNull
            public abstract String getName();

            @NonNull
            public abstract String getCode();

            @NonNull
            public abstract long getAddress();

            /** Builder for {@link Signal}. */
            @AutoValue.Builder
            public abstract static class Builder {

              @NonNull
              public abstract Builder setName(@NonNull String value);

              @NonNull
              public abstract Builder setCode(@NonNull String value);

              @NonNull
              public abstract Builder setAddress(long value);

              @NonNull
              public abstract Signal build();
            }
          }

          @AutoValue
          public abstract static class BinaryImage {

            @NonNull
            public static Builder builder() {
              return new AutoValue_CrashlyticsReport_Session_Event_Application_Execution_BinaryImage
                  .Builder();
            }

            @NonNull
            public abstract long getBaseAddress();

            public abstract long getSize();

            @NonNull
            public abstract String getName();

            @Ignore
            @NonNull
            public abstract String getUuid();

            @Field(name = "uuid")
            @NonNull
            public byte[] getUuidUtf8Bytes() {
              return getUuid().getBytes(UTF_8);
            }

            /** Builder for {@link BinaryImage}. */
            @AutoValue.Builder
            public abstract static class Builder {

              @NonNull
              public abstract Builder setBaseAddress(long value);

              @NonNull
              public abstract Builder setSize(long value);

              @NonNull
              public abstract Builder setName(@NonNull String value);

              @NonNull
              public abstract Builder setUuid(@NonNull String value);

              @NonNull
              public Builder setUuidFromUtf8Bytes(@NonNull byte[] utf8Bytes) {
                return setUuid(new String(utf8Bytes, UTF_8));
              }

              @NonNull
              public abstract BinaryImage build();
            }
          }

          /** Builder for {@link Execution}. */
          @AutoValue.Builder
          public abstract static class Builder {

            @NonNull
            public abstract Builder setThreads(@NonNull ImmutableList<Thread> value);

            @NonNull
            public abstract Builder setException(@NonNull Exception value);

            @NonNull
            public abstract Builder setSignal(@NonNull Signal value);

            @NonNull
            public abstract Builder setBinaries(@NonNull ImmutableList<BinaryImage> value);

            @NonNull
            public abstract Execution build();
          }
        }

        /** Builder for {@link Application}. */
        @AutoValue.Builder
        public abstract static class Builder {

          @NonNull
          public abstract Builder setExecution(@NonNull Execution value);

          @NonNull
          public abstract Builder setCustomAttributes(
              @NonNull ImmutableList<CustomAttribute> value);

          @NonNull
          public abstract Builder setBackground(boolean value);

          @NonNull
          public abstract Builder setUiOrientation(int value);

          @NonNull
          public abstract Application build();
        }
      }

      @AutoValue
      public abstract static class Device {

        @NonNull
        public static Builder builder() {
          return new AutoValue_CrashlyticsReport_Session_Event_Device.Builder();
        }

        public abstract double getBatteryLevel();

        public abstract int getBatteryVelocity();

        public abstract boolean isProximityOn();

        public abstract int getOrientation();

        public abstract long getRamUsed();

        public abstract long getDiskUsed();

        /** Builder for {@link Device}. */
        @AutoValue.Builder
        public abstract static class Builder {

          @NonNull
          public abstract Builder setBatteryLevel(double value);

          @NonNull
          public abstract Builder setBatteryVelocity(int value);

          @NonNull
          public abstract Builder setProximityOn(boolean value);

          @NonNull
          public abstract Builder setOrientation(int value);

          @NonNull
          public abstract Builder setRamUsed(long value);

          @NonNull
          public abstract Builder setDiskUsed(long value);

          @NonNull
          public abstract Device build();
        }
      }

      @AutoValue
      public abstract static class Log {

        @NonNull
        public static Builder builder() {
          return new AutoValue_CrashlyticsReport_Session_Event_Log.Builder();
        }

        @NonNull
        public abstract String getContent();

        /** Builder for {@link Log}. */
        @AutoValue.Builder
        public abstract static class Builder {

          @NonNull
          public abstract Builder setContent(@NonNull String value);

          @NonNull
          public abstract Log build();
        }
      }

      /** Builder for {@link Event}. */
      @AutoValue.Builder
      public abstract static class Builder {

        @NonNull
        public abstract Builder setTimestamp(long value);

        @NonNull
        public abstract Builder setType(@NonNull String value);

        @NonNull
        public abstract Builder setApp(@NonNull Application value);

        @NonNull
        public abstract Builder setDevice(@NonNull Device value);

        @NonNull
        public abstract Builder setLog(@NonNull Log value);

        @NonNull
        public abstract Event build();
      }
    }
  }

  @AutoValue.Builder
  public abstract static class Builder {

    @NonNull
    public abstract Builder setSdkVersion(@NonNull String value);

    @NonNull
    public abstract Builder setGmpAppId(@NonNull String value);

    @NonNull
    public abstract Builder setPlatform(int value);

    @NonNull
    public abstract Builder setInstallationUuid(@NonNull String value);

    @NonNull
    public abstract Builder setBuildVersion(@NonNull String value);

    @NonNull
    public abstract Builder setDisplayVersion(@NonNull String value);

    @NonNull
    public abstract Builder setSession(@NonNull Session value);

    @NonNull
    public abstract CrashlyticsReport build();
  }
}
