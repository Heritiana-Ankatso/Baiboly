package mg.baiboly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent { BaibolyApp(vm) }
    }
}
