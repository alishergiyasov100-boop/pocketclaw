package com.musornibak.pocketclaw

import android.app.Application
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.musornibak.pocketclaw.data.SettingsRepository
import com.musornibak.pocketclaw.service.BubbleService
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class PocketClawApp : Application() {

    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                scope.launch {
                    val s = settings.flow.first()
                    if (!s.bubbleEnabled) return@launch
                    if (!Settings.canDrawOverlays(this@PocketClawApp)) return@launch
                    val intent = Intent(this@PocketClawApp, BubbleService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }

            override fun onStart(owner: LifecycleOwner) {
                stopService(Intent(this@PocketClawApp, BubbleService::class.java))
            }
        })
    }
}
