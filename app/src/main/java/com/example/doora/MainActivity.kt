package com.example.doora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.example.doora.ui.navigation.DooraNavGraph
import com.example.doora.ui.theme.DooraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as DooraApplication).repository
        setContent {
            DooraTheme {
                val navController = rememberNavController()
                DooraNavGraph(navController = navController, repository = repository)
            }
        }
    }
}