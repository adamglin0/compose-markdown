package com.adamglin.compose.markdown.sample.chat.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.adamglin.compose.markdown.sample.chat.ComposeMarkdownSampleScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComposeMarkdownSampleScreen()
        }
    }
}
