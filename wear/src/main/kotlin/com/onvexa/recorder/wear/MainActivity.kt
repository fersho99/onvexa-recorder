package com.onvexa.recorder.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.onvexa.recorder.core.design.components.AudioWaveform
import com.onvexa.recorder.core.design.components.RecordButton
import com.onvexa.recorder.core.design.components.RecordButtonKind
import com.onvexa.recorder.core.design.wear.OnvexaWearTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sin

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OnvexaWearTheme {
                HomeScreen()
            }
        }
    }
}

/** Pantalla provisional (circular) que valida tema y componentes en el reloj. */
@Composable
private fun HomeScreen() {
    val levels = List(32) { (sin(it * 0.5f) + 1f) / 2f }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // Márgenes seguros para pantalla redonda.
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.timer_zero),
                style = MaterialTheme.typography.display1,
                color = MaterialTheme.colors.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            AudioWaveform(
                levels = levels,
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
            Spacer(Modifier.height(10.dp))
            RecordButton(
                kind = RecordButtonKind.Record,
                description = stringResource(R.string.cd_record),
                onClick = {},
                size = 56.dp,
            )
        }
    }
}
