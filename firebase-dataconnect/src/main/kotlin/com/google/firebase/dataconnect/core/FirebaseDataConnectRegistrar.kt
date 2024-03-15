package com.google.firebase.dataconnect.core

import android.content.Context
import androidx.annotation.Keep
import androidx.annotation.RestrictTo
import com.google.firebase.FirebaseApp
import com.google.firebase.annotations.concurrent.Blocking
import com.google.firebase.annotations.concurrent.Lightweight
import com.google.firebase.components.Component
import com.google.firebase.components.ComponentRegistrar
import com.google.firebase.components.Dependency
import com.google.firebase.components.Qualified
import com.google.firebase.dataconnect.*
import com.google.firebase.platforminfo.LibraryVersionComponent
import java.util.concurrent.Executor

/**
 * [ComponentRegistrar] for setting up [FirebaseDataConnect].
 *
 * @hide
 */
@Keep
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class FirebaseDataConnectRegistrar : ComponentRegistrar {

  @Keep
  override fun getComponents() =
    listOf(
      Component.builder(FirebaseDataConnectFactory::class.java)
        .name(LIBRARY_NAME)
        .add(Dependency.required(firebaseApp))
        .add(Dependency.required(context))
        .add(Dependency.required(blockingExecutor))
        .add(Dependency.required(nonBlockingExecutor))
        .factory { container ->
          FirebaseDataConnectFactory(
            context = container.get(context),
            firebaseApp = container.get(firebaseApp),
            blockingExecutor = container.get(blockingExecutor),
            nonBlockingExecutor = container.get(nonBlockingExecutor),
          )
        }
        .build(),
      LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME)
    )

  companion object {
    private const val LIBRARY_NAME = "fire-dataconnect"

    private val firebaseApp = Qualified.unqualified(FirebaseApp::class.java)
    private val context = Qualified.unqualified(Context::class.java)
    private val blockingExecutor = Qualified.qualified(Blocking::class.java, Executor::class.java)
    private val nonBlockingExecutor =
      Qualified.qualified(Lightweight::class.java, Executor::class.java)
  }
}
