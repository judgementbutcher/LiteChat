package app.litechat.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.litechat.android.ui.AppViewModel
import app.litechat.android.ui.LiteChatRoot
import app.litechat.android.ui.theme.LiteChatTheme

class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<AppViewModel> { AppViewModel.Factory((application as LiteChatApplication).container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LiteChatTheme(settings.themeMode, settings.dynamicColor) {
                LiteChatRoot(viewModel)
            }
        }
    }
}
