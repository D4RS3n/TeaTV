package com.teatv.app

import android.os.Process
import com.teatv.app.player.PlayerHolder

/** Сторож пустого процесса: опрашивает факты, а не флаги. */
object ProcessGuard {

    @Volatile
    var appForeground: Boolean = false

    fun killIfEmpty() {
        if (appForeground) return
        if (TeaAudioService.instance != null) return
        if (FarmService.instance != null) return
        if (PlayerHolder.player != null) return
        Process.killProcess(Process.myPid())
    }
}