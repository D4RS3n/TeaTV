package com.teatv.app.player

import android.graphics.Bitmap
import androidx.media3.exoplayer.ExoPlayer

/** Мост между UI и фоновым сервисом. */
object PlayerHolder {
    var player: ExoPlayer? = null
    var streamName: String? = null
    var streamId: String? = null
    var streamGame: String? = null
    var streamAvatar: Bitmap? = null
}