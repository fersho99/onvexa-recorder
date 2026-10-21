package com.onvexa.recorder.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.wear.MainActivity
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.data.WatchRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

/** Texto corto con la duración de la última grabación; al tocarlo abre la app. */
@AndroidEntryPoint
class LastRecordingComplicationService : SuspendingComplicationDataSourceService() {

    @Inject lateinit var repository: WatchRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return NoDataComplicationData()
        val last = runCatching { repository.last().firstOrNull() }.getOrNull()
            ?: return NoDataComplicationData()
        return build(formatClock(last.durationMs))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type == ComplicationType.SHORT_TEXT) build("12:30") else null

    private fun build(text: String): ComplicationData {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.complication_label)).build(),
        )
            .setTapAction(open)
            .build()
    }
}
