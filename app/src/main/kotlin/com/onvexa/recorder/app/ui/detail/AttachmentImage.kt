package com.onvexa.recorder.app.ui.detail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun AttachmentImage(id: String, viewModel: DetailViewModel) {
    var bitmap by remember(id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(id) {
        bitmap = try { withContext(Dispatchers.IO) {
            val path = viewModel.image(id).path
            BitmapFactory.decodeFile(path,BitmapFactory.Options().apply { inSampleSize = 2 })
        } } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
    }
    bitmap?.let { Image(it.asImageBitmap(),"Foto adjunta",Modifier.fillMaxSize(),contentScale = ContentScale.Crop) } ?: Text("Foto")
}
