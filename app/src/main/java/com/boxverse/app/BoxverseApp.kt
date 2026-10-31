package com.boxverse.app

import android.app.Application
import android.content.Context
import com.boxverse.app.data.db.BoxDatabase
import com.boxverse.app.data.prefs.SettingsRepository
import com.boxverse.app.data.repo.BackupManager
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.domain.AchievementDef
import com.boxverse.app.util.Haptics
import com.boxverse.app.util.ReminderScheduler
import com.boxverse.app.util.SoundFx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface AppEvent {
    data class AchievementUnlocked(val def: AchievementDef) : AppEvent
    data class Message(val text: String) : AppEvent
}

/** 简单的手动依赖注入容器 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database = BoxDatabase.build(appContext)
    val settings = SettingsRepository(appContext)
    val repo = BoxRepository(database, settings)
    val backup = BackupManager(appContext, repo)
    val haptics = Haptics(appContext)
    val sound = SoundFx()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppEvent> = _events

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError

    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }

    fun start() {
        scope.launch {
            runCatching { repo.ensureInitialized() }
                .onSuccess { _ready.value = true }
                .onFailure { _initError.value = it.message ?: "初始化失败" }
        }
        scope.launch {
            settings.settings.collect {
                haptics.enabled = it.hapticsEnabled
                sound.enabled = it.soundEnabled
            }
        }
        scope.launch {
            settings.settings.map { it.reminderEnabled }.distinctUntilChanged().collect { enabled ->
                if (enabled) ReminderScheduler.schedule(appContext) else ReminderScheduler.cancel(appContext)
            }
        }
        // 成就监听：统计变化时自动解锁并发出庆祝事件
        scope.launch {
            val announced = mutableSetOf<String>()
            combine(repo.collectorStats, repo.achievementUnlocks) { stats, unlocks ->
                stats to unlocks.map { it.id }.toSet()
            }.collect { (stats, unlocked) ->
                if (!_ready.value) return@collect
                announced += unlocked
                val reached = repo.newlyReached(stats, unlocked).filter { it.id !in announced }
                if (reached.isNotEmpty()) {
                    announced += reached.map { it.id }
                    repo.unlockAchievements(reached.map { it.id })
                    reached.forEach { _events.tryEmit(AppEvent.AchievementUnlocked(it)) }
                }
            }
        }
    }
}

class BoxverseApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
