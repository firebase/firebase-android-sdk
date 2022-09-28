package com.googletest.firebase.appdistribution.testapp

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import com.google.firebase.appdistribution.ktx.appDistribution
import com.google.firebase.ktx.Firebase

class SecondActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
    }

    override fun onResume() {
        super.onResume()
        val mainActivityButton = findViewById<AppCompatButton>(R.id.main_activity_button)
        mainActivityButton.setOnClickListener {
            startMainActivity()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.action_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.startFeedbackMenuItem -> {
                Firebase.appDistribution.startFeedback(R.string.terms_and_conditions)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
    }
}
