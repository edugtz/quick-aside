package com.edu.quickaside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QuickAsideTheme {
                QuickAsideApp(
                    captureSubmission = (application as QuickAsideApplication).captureSubmission,
                    captureReader = (application as QuickAsideApplication).captureReader,
                    speechTranscriberFactory =
                        (application as QuickAsideApplication).speechTranscriberFactory,
                )
            }
        }
    }
}
