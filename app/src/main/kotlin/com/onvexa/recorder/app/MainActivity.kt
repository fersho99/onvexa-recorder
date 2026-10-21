package com.onvexa.recorder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onvexa.recorder.core.design.components.AudioWaveform
import com.onvexa.recorder.core.design.components.GlowCard
import com.onvexa.recorder.core.design.components.RecordButton
import com.onvexa.recorder.core.design.components.RecordButtonKind
import com.onvexa.recorder.core.design.phone.OnvexaPhoneTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.sin

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OnvexaPhoneTheme {
                HomeScreen()
            }
        }
    }
}

/** Pantalla provisional que valida tema y componentes del sistema de diseño. */
@Composable
private fun HomeScreen() {
    val levels = List(48) { (sin(it * 0.45f) + 1f) / 2f }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_symbol),
            contentDescription = null,
            modifier = Modifier.width(160.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        GlowCard(modifier = Modifier.fillMaxWidth()) {
            AudioWaveform(levels = levels, modifier = Modifier.fillMaxWidth().height(72.dp))
        }
        Spacer(Modifier.height(32.dp))
        RecordButton(
            kind = RecordButtonKind.Record,
            description = stringResource(R.string.cd_record),
            onClick = {},
            size = 72.dp,
        )
    }
}
