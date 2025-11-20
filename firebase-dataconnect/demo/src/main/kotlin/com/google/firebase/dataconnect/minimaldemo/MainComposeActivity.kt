/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:OptIn(ExperimentalMaterial3Api::class)

package com.google.firebase.dataconnect.minimaldemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainComposeActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Scaffold(
          topBar = {
            TopAppBar(
              title = { Text("Data Connect Minimal Demo") },
              navigationIcon = {
                IconButton(onClick = { /* "Open nav drawer" */ }) {
                  Icon(Icons.Filled.Menu, contentDescription = "Hamburger Menu")
                }
              },
            )
          },
          modifier = Modifier.fillMaxSize(),
        ) { innerPadding ->
          Greeting(Modifier.padding(innerPadding).fillMaxSize().wrapContentSize())
        }
      }
    }
  }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
  Column(verticalArrangement = Arrangement.Center, modifier = modifier) {
    Text(
      "foobar 1 2 3 4 5",
      fontSize = 100.sp,
      lineHeight = 110.sp,
      modifier = Modifier.padding(8.dp),
    )
  }
}

@Preview
@Composable
fun GreetingPreview() {
  MaterialTheme { Greeting() }
}
