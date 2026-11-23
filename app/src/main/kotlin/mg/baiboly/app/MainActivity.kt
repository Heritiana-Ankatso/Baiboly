package mg.baiboly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import mg.baiboly.app.ui.BaibolyApp
import mg.baiboly.app.ui.Silent

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Silent.applyTo(window.decorView)
        if (savedInstanceState == null) handleOpen(intent)
        setContent {
            // The screens get a language-specific context; the activity's own
            // result registry and back dispatcher are provided explicitly.
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this,
                LocalOnBackPressedDispatcherOwner provides this,
            ) { BaibolyApp(vm) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleOpen(intent)
    }

    /** The "new version" notification opens the update settings. */
    private fun handleOpen(intent: android.content.Intent?) {
        if (intent?.getStringExtra(mg.baiboly.app.update.UpdateCheck.EXTRA_OPEN) == mg.baiboly.app.update.UpdateCheck.OPEN_UPDATES) {
            intent.removeExtra(mg.baiboly.app.update.UpdateCheck.EXTRA_OPEN)
            vm.openUpdates()
        }
    }
}
