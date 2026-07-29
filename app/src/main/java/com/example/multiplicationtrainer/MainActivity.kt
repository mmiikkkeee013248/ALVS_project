package com.example.multiplicationtrainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.multiplicationtrainer.ui.TrainerApp
import com.example.multiplicationtrainer.ui.theme.MultiplicationTrainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultiplicationTrainerTheme {
                TrainerApp()
            }
        }
    }
}
