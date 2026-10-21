package com.onvexa.recorder.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import com.onvexa.recorder.core.model.formatClock
import com.onvexa.recorder.wear.MainActivity
import com.onvexa.recorder.wear.R
import com.onvexa.recorder.wear.data.WatchRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val RESOURCES_VERSION = "1"
private const val BACKGROUND = 0xFF000000.toInt()
private const val RECORD_FILL = 0xFFDC2626.toInt()
private const val TEXT = 0xFFECEEF3.toInt()
private const val TEXT_MUTED = 0xFF9AA3B2.toInt()

/** Tile con un botón para empezar a grabar y la duración de la última grabación. */
@AndroidEntryPoint
class RecordTileService : TileService() {

    @Inject lateinit var repository: WatchRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                val last = runCatching { repository.last().firstOrNull() }.getOrNull()
                val subtitle = last?.let { getString(R.string.tile_last, formatClock(it.durationMs)) }
                completer.set(buildTile(subtitle))
            }
            "RecordTile"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "RecordTileResources"
        }

    private fun buildTile(subtitle: String?): TileBuilders.Tile {
        val launch = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(packageName)
                    .setClassName(MainActivity::class.java.name)
                    .addKeyToExtraMapping(MainActivity.EXTRA_START_RECORDING, ActionBuilders.booleanExtra(true))
                    .build(),
            )
            .build()

        val button = LayoutElementBuilders.Box.Builder()
            .setWidth(dp(72f))
            .setHeight(dp(72f))
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(RECORD_FILL))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(36f)).build())
                            .build(),
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("record")
                            .setOnClick(launch)
                            .build(),
                    )
                    .build(),
            )
            .addContent(label(getString(R.string.tile_label), 14f, TEXT))
            .build()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(button)
            .apply {
                if (subtitle != null) {
                    addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
                    addContent(label(subtitle, 12f, TEXT_MUTED))
                }
            }
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(BACKGROUND)).build())
                    .build(),
            )
            .addContent(column)
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
            .build()
    }

    private fun label(text: String, size: Float, color: Int) =
        LayoutElementBuilders.Text.Builder()
            .setText(text)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .build(),
            )
            .build()
}
