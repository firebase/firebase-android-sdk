package com.google.firebase.lint.checks

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

fun taskSource(): String {
  return """
        package com.google.android.gms.tasks;
        
        import java.util.concurrent.Executor;

        public interface Task<TResult> {
          Task<TResult> continueWith(Executor executor, String dummy);
          Task<TResult> continueWith(String dummy);
        }
    """
    .trimIndent()
}

class TasksMainThreadDetectorTests : LintDetectorTest() {
  override fun getDetector(): Detector = TasksMainThreadDetector()

  override fun getIssues(): MutableList<Issue> =
    mutableListOf(TasksMainThreadDetector.TASK_MAIN_THREAD)

  fun test_continueWith_withoutExecutor_shouldFail() {
    lint()
      .files(
        java(taskSource()),
        java(
          """
            import com.google.android.gms.tasks.Task;
            class Hello {
              public static Task<String> useTask(Task<String> task) {
                return task.continueWith("hello");
              }
            }
        """
            .trimIndent()
        )
      )
      .run()
      .checkContains("Use an Executor explicitly to avoid running on the main thread")
  }

  fun test_continueWith_withExecutor_shouldSucceed() {
    lint()
      .files(
        java(taskSource()),
        java(
          """
            import com.google.android.gms.tasks.Task;
            import java.util.concurrent.Executor;
            class Hello {
              public static Task<String> useTask(Executor executor, Task<String> task) {
                return task.continueWith(executor, "hello");
              }
            }
        """
            .trimIndent()
        )
      )
      .run()
      .expectClean()
  }

  fun test_continueWith_withoutExecutor_whileImplementingOverload_shouldSucceed() {
    lint()
      .files(
        java(taskSource()),
        java(
          """
            import com.google.android.gms.tasks.Task;
            import java.util.concurrent.Executor;
            class Hello<TResult> implements Task<TResult> {
              private final Task<TResult> delegate;
              
              Hello(Task<TResult> delegate) { this.delegate = delegate;}

              @Override
              public Task<TResult> continueWith(Executor executor, String dummy) {
                return delegate.continueWith(executor, dummy);
              }
              public Task<TResult> continueWith(String dummy) {
                return delegate.continueWith(dummy);
              }
            }
        """
            .trimIndent()
        )
      )
      .run()
      .expectClean()
  }

  fun test_continueWith_withoutExecutor_fromWrongOverload_shouldFail() {
    lint()
      .files(
        java(taskSource()),
        java(
          """
            import com.google.android.gms.tasks.Task;
            import java.util.concurrent.Executor;
            class Hello<TResult> implements Task<TResult> {
              private final Task<TResult> delegate;
              
              Hello(Task<TResult> delegate) { this.delegate = delegate;}

              @Override
              public Task<TResult> continueWith(Executor executor, String dummy) {
                return delegate.continueWith(dummy);
              }
              public Task<TResult> continueWith(String dummy) {
                return delegate.continueWith(dummy);
              }
            }
        """
            .trimIndent()
        )
      )
      .run()
      .checkContains("Use an Executor explicitly to avoid running on the main thread")
  }
}
