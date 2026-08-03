package com.riddlesdiary

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.riddlesdiary.ui.DiaryScreen
import com.riddlesdiary.ui.theme.RiddlesDiaryTheme

/**
 * Main activity for the Riddle's Diary app.
 * A magical diary that responds to handwriting with AI-generated text,
 * inspired by Tom Riddle's Diary from Harry Potter.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen immersive mode for the diary experience
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowInsetsControllerCompat(
            window,
            window.decorView
        )
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Keep screen on while the diary is open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            RiddlesDiaryTheme {
                DiaryScreen()
            }
        }
    }
}
