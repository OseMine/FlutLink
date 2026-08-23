package com.flutcloud.flutlink.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.flutcloud.flutlink.ui.FlutLinkRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as FlutLinkApplication).container
        setContent {
            FlutLinkRoot(container)
        }
    }
}
