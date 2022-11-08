# SDK Health Metrics

This directory contains the source code, configuration and documentation for health metrics
(size, app startup time, etc.) measurement for Firebase Android SDKs.

Available metrics are published in the Firebase
[Android SDK metrics](https://firebase.google.com/docs/android/sdk-metrics) page.

## Size

Refer to [README.md](apk-size/README.md) in the subdirectory `apk-size` for more details.

## App startup time

Firebase runs during different
[app lifecycle](https://d.android.com/guide/components/activities/process-lifecycle)
phases, and contributes to the overall
[app startup time](https://d.android.com/topic/performance/vitals/launch-time)
in many ways.

We are currently using
[benchmarking](https://d.android.com/topic/performance/benchmarking/benchmarking-overview)
and [tracing](https://d.android.com/topic/performance/tracing) to measure its
latency impact. Refer to [README.md](benchmark/README.md) in the subdirectory
`benchmark` for more details.
