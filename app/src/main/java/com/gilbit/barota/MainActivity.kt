package com.gilbit.barota

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gilbit.barota.ui.navigation.BarotaNavHost
import com.gilbit.barota.ui.theme.SubwayBarotaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SubwayBarotaTheme {
                BarotaNavHost()
            }
        }
    }
}
