package com.google.firebase.sessions

import androidx.datastore.dataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.sessions.testing.FakeFirebaseApp
import com.google.firebase.sessions.util.validateParentOrThrow
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileProducerTest {

    private val appContext = FakeFirebaseApp().firebaseApp.applicationContext

    @Test
    fun `sessionConfigsDataStore file path validation`() {
        val aqsTextFile = File(appContext.filesDir, "datastore/aqs")
        aqsTextFile.parentFile?.mkdirs()
        aqsTextFile.writeText("This is aqs text file")

        assertThrows(IllegalStateException::class.java) { appContext.dataStoreFile("aqs/sessionConfigsDataStore.data").validateParentOrThrow() }
    }
}