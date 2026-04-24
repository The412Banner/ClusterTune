package com.aure.androidtuner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aure.androidtuner.ui.MainTunerScreen
import com.aure.androidtuner.ui.SettingsScreen
import com.aure.androidtuner.ui.TunerViewModel

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }
    private val viewModel by viewModels<TunerViewModel> {
        TunerViewModel.factory(
            repository = container.repository,
            settingsStorage = container.settingsStorage,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MaterialTheme {
                Surface {
                    val state = viewModel.state.collectAsStateWithLifecycle().value
                    val settings = viewModel.settings.collectAsStateWithLifecycle().value
                    var showSettings by rememberSaveable { mutableStateOf(false) }

                    if (showSettings) {
                        SettingsScreen(
                            settings = settings,
                            onBack = { showSettings = false },
                            onTileTapBehaviorChange = viewModel::setTileTapBehavior,
                            onTileLongPressBehaviorChange = viewModel::setTileLongPressBehavior,
                            onApplyLastPresetOnBootChange = viewModel::setApplyLastPresetOnBoot,
                            onResetProfiles = viewModel::resetProfilesToDefault,
                        )
                    } else {
                        MainTunerScreen(
                            state = state,
                            onPolicyValueChange = viewModel::setPolicyValue,
                            onApplyProfile = viewModel::applyProfile,
                            onClearSelection = viewModel::clearSelection,
                            onApplyCurrent = { tunerState -> viewModel.applyCurrent(tunerState) },
                            onCreatePreset = viewModel::createUserPreset,
                            onUpdatePreset = viewModel::updateProfile,
                            onDeletePreset = viewModel::deletePreset,
                            onMovePreset = viewModel::moveProfile,
                            onResetProfiles = viewModel::resetProfilesToDefault,
                            onOpenSettings = { showSettings = true },
                            onRefresh = viewModel::refreshState,
                        )
                    }
                }
            }
        }
    }
}
