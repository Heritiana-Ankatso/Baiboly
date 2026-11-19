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
        setContent {
            // The screens get a language-specific context; the activity's own
            // result registry and back dispatcher are provided explicitly.
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this,
                LocalOnBackPressedDispatcherOwner provides this,
            ) { BaibolyApp(vm) }
        }
    }
}
