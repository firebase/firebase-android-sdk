/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.testing.crashlytics

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.testing.crashlytics.databinding.FragmentFirstBinding

class FirstFragment : Fragment() {

  private var _binding: FragmentFirstBinding? = null
  private val binding
    get() = _binding!!

  // Single Crashlytics instance
  private val crashlytics = FirebaseCrashlytics.getInstance()

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentFirstBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // 1) Restore and display any previously saved user ID
    loadAndApplyUserId()

    // 1b) Has the app crashed before?
    loadAndApplyHasCrashed()

    // 2) Set up buttons. Each button sets a NEW user ID, saves it, then performs its logic.
    binding.buttonSharedInitializeCrashlytics.setOnClickListener {
      val userId = "SharedInitialize_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      crashlytics.log("Shared_Initialize_Crashlytics button clicked. userId=$userId")
      // Typically, Crashlytics auto-initializes. We do nothing special otherwise.
    }

    binding.buttonSharedGenerateCrash.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Shared_Generate_Crash"
      val userId = "SharedGenerate_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("Test: Shared_Generate_Crash")
    }

    binding.buttonSharedVerifyCrash.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Shared_Verify_Crash"
      val userId = "SharedVerifyCrash_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      crashlytics.log("Shared_Verify_Crash log before crash. userId=$userId")
      crashlytics.setCustomKey("SharedVerifyCrashKey", "SomeValue")
      Thread.sleep(1_000)
      throw RuntimeException("Test: Shared_Verify_Crash")
    }

    binding.buttonSharedVerifyNoCrash.setOnClickListener {
      val userId = "SharedVerifyNoCrash_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      crashlytics.log("Shared_Verify_No_Crash clicked, but not crashing. userId=$userId")
      // No crash, so user remains in the app.
    }

    binding.buttonFirebasecoreFatalError.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "FirebaseCore_Fatal_Error"
      val userId = "FirebaseCoreFatal_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("FirebaseCore_Fatal_Error has occurred.")
    }

    binding.buttonPublicApiLog.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Public_API_Log"
      val userId = "PublicApiLog_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      crashlytics.log("Custom log message 1 (Public_API_Log) userId=$userId")
      crashlytics.log("Custom log message 2 (Public_API_Log)")
      Thread.sleep(1_000)
      throw RuntimeException("Public_API_Log crash")
    }

    binding.buttonPublicApiSetcustomvalue.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Public_API_SetCustomValue"
      val userId = "PublicApiSetCustomValue_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      crashlytics.setCustomKey("key", "value")
      crashlytics.setCustomKey("number", 42)
      throw RuntimeException("Public_API_SetCustomValue crash")
    }

    binding.buttonPublicApiSetuserid.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Public_API_SetUserID"
      val userId = "PublicApiSetUserID_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("Public_API_SetUserID crash")
    }

    binding.buttonPublicApiDidcrashpreviously.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Public_API_DidCrashPreviously"
      val userId = "DidCrashPreviously_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("Public_API_DidCrashPreviously crash")
    }

    binding.buttonPublicApiRecordexception.setOnClickListener {
      val userId = "PublicApiRecordException_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      // Record multiple non-fatal exceptions, but do NOT crash.
      crashlytics.recordException(RuntimeException("public_API_RecordException: non-fatal 1"))
      crashlytics.recordException(RuntimeException("public_API_RecordException: non-fatal 2"))
      crashlytics.log(
        "Public_API_RecordException: recorded two non-fatals, no crash. userId=$userId"
      )
    }

    binding.buttonDatacollectionDefault.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "DataCollection_Default"
      val userId = "DataCollectionDefault_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("DataCollection_Default crash")
    }

    binding.buttonDatacollectionFirebaseOff.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "DataCollection_Firebase_Off"
      val userId = "DataCollectionFirebaseOff_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("DataCollection_Firebase_Off crash")
    }

    binding.buttonDatacollectionCrashlyticsOff.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "DataCollection_Crashlytics_Off"
      val userId = "DataCollectionCrashlyticsOff_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("DataCollection_Crashlytics_Off crash")
    }

    binding.buttonDatacollectionOffThenOn.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "DataCollection_Crashlytics_Off_Then_On"
      val userId = "DataCollectionOffThenOn_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("DataCollection_Crashlytics_Off_Then_On crash")
    }

    binding.buttonDatacollectionOffThenSend.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "DataCollection_Crashlytics_Off_Then_Send"
      val userId = "DataCollectionOffThenSend_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("DataCollection_Crashlytics_Off_Then_Send crash")
    }

    binding.buttonDatacollectionOffThenDelete.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "DataCollection_Crashlytics_Off_Then_Delete"
      val userId = "DataCollectionOffThenDelete_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      throw RuntimeException("DataCollection_Crashlytics_Off_Then_Delete crash")
    }

    binding.buttonInteroperabilityIid.setOnClickListener {
      PreFirebaseProvider.expectedMessage = "Interoperability_IID"
      val userId = "InteroperabilityIID_${System.currentTimeMillis()}"
      saveAndApplyUserId(userId)

      FirebaseInstallations.getInstance().delete().addOnCompleteListener {
        throw RuntimeException("Interoperability_IID crash after ID reset")
      }
    }
  }

  /**
   * Load the previously-saved userId (if any) from SharedPreferences, apply it to Crashlytics, and
   * display it in the TextView.
   */
  private fun loadAndApplyUserId() {
    val prefs = requireContext().getSharedPreferences("crashlytics_prefs", Context.MODE_PRIVATE)
    val savedId = prefs.getString("latest_user_id", null)
    if (!savedId.isNullOrEmpty()) {
      crashlytics.setUserId(savedId)
      binding.currentUserIdText.text = "UserId: $savedId"
    }
  }

  /** Load the previously-saved "hasCrashed" flag, */
  private fun loadAndApplyHasCrashed() {
    val hasCrashed = crashlytics.didCrashOnPreviousExecution()
    binding.currentHasCrashed.text = "HasCrashed: $hasCrashed"
  }

  /**
   * Save the given userId to SharedPreferences, apply it to Crashlytics, and update the TextView
   * accordingly.
   */
  private fun saveAndApplyUserId(newUserId: String) {
    // Save to SharedPreferences
    val prefs = requireContext().getSharedPreferences("crashlytics_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("latest_user_id", newUserId).apply()

    // Apply to Crashlytics and UI
    crashlytics.setUserId(newUserId)
    binding.currentUserIdText.text = "UserId: $newUserId"
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }
}
