-optimizationpasses 3
-keepattributes SourceFile, LineNumberTable, *Annotation*

-keep @org.junit.runner.RunWith class *
-keep class androidx.test.**
-keep class org.junit.**

-keepclassmembers @org.junit.runner.RunWith class * {
  public <methods>;
  public <fields>;
}

-keepclassmembers class androidx.test.** {
  public <methods>;
}

-keepclassmembers class org.junit.** {
  protected <methods>;
  public <methods>;
}

-keepclassmembers class com.google.firebase.crashlytics.FirebaseCrashlytics {
  private com.google.firebase.crashlytics.internal.common.CrashlyticsCore core;
}

-keepclassmembers class com.google.firebase.crashlytics.internal.common.CrashlyticsCore {
  private com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource breadcrumbSource;
}

-dontwarn android.**
-dontwarn okio.**
