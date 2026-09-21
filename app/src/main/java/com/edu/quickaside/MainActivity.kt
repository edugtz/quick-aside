package com.edu.quickaside

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.edu.quickaside.ui.QuickAsideApp
import com.edu.quickaside.ui.theme.QuickAsideTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as QuickAsideApplication
        setContent {
            QuickAsideTheme {
                QuickAsideApp(
                    captureSubmission = app.captureSubmission,
                    capturePlanListExecutor = app.capturePlanListExecutor,
                    captureReader = app.captureReader,
                    listStore = app.listStore,
                    reversibleListItemActions = app.reversibleListItemActions,
                    taskStore = app.taskStore,
                    reversibleTaskActions = app.reversibleTaskActions,
                    memoryStore = app.memoryStore,
                    localSearch = app.localSearch,
                    captureTranscriptCorrector = app.captureTranscriptCorrector,
                    speechTranscriberFactory = app.speechTranscriberFactory,
                    devicePairer = app.devicePairer,
                )
            }
        }
    }
}
