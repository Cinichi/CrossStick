package cross.stick

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import cross.stick.ui.navigation.AppNavGraph
import cross.stick.ui.theme.CrossStickTheme
import cross.stick.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CrossStickTheme {
                AppNavGraph(viewModel = viewModel)
            }
        }
    }
}
