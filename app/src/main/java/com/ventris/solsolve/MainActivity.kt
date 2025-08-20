package com.ventris.solsolve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.ventris.solsolve.ui.scanner.SolitaireScannerScreen
import com.ventris.solsolve.ui.theme.SolSolveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SolSolveTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SolitaireScannerScreen()
                }
            }
        }
    }
}