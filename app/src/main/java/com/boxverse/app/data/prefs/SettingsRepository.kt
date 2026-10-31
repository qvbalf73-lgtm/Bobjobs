package com.boxverse.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.boxverse.app.domain.CurrencyConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "boxverse_settings")

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("明亮模式"), DARK("深色模式") }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reminderEnabled: Boolean = false,
    val baseCurrency: String = "CNY",
    /** 用户手动填写的汇率：1 单位外币 = rate 个基准币 */
    val exchangeRates: Map<String, Double> = emptyMap(),
    val seeded: Boolean = false,
    val lastSeriesViewDay: Long = -1,
) {
    val converter: CurrencyConverter get() = CurrencyConverter(baseCurrency, exchangeRates)
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val reminder = booleanPreferencesKey("reminder")
        val baseCurrency = stringPreferencesKey("base_currency")
        val rates = stringPreferencesKey("exchange_rates")
        val seeded = booleanPreferencesKey("demo_seeded")
        val lastSeriesView = longPreferencesKey("last_series_view_day")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AppSettings(
                themeMode = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.SYSTEM,
                soundEnabled = p[Keys.sound] ?: true,
                hapticsEnabled = p[Keys.haptics] ?: true,
                reminderEnabled = p[Keys.reminder] ?: false,
                baseCurrency = p[Keys.baseCurrency] ?: "CNY",
                exchangeRates = decodeRates(p[Keys.rates]),
                seeded = p[Keys.seeded] ?: false,
                lastSeriesViewDay = p[Keys.lastSeriesView] ?: -1,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.theme] = mode.name }
    suspend fun setSound(enabled: Boolean) = context.dataStore.edit { it[Keys.sound] = enabled }
    suspend fun setHaptics(enabled: Boolean) = context.dataStore.edit { it[Keys.haptics] = enabled }
    suspend fun setReminder(enabled: Boolean) = context.dataStore.edit { it[Keys.reminder] = enabled }
    suspend fun setBaseCurrency(code: String) = context.dataStore.edit { it[Keys.baseCurrency] = code }
    suspend fun setSeeded(seeded: Boolean) = context.dataStore.edit { it[Keys.seeded] = seeded }
    suspend fun setLastSeriesViewDay(day: Long) = context.dataStore.edit { it[Keys.lastSeriesView] = day }

    suspend fun setExchangeRate(code: String, rate: Double?) = context.dataStore.edit { p ->
        val map = decodeRates(p[Keys.rates]).toMutableMap()
        if (rate == null || rate <= 0) map.remove(code) else map[code] = rate
        p[Keys.rates] = encodeRates(map)
    }

    private fun encodeRates(map: Map<String, Double>): String = map.entries.joinToString(";") { "${it.key}=${it.value}" }

    private fun decodeRates(text: String?): Map<String, Double> {
        if (text.isNullOrBlank()) return emptyMap()
        return text.split(";").mapNotNull { pair ->
            val parts = pair.split("=")
            if (parts.size != 2) null else parts[1].toDoubleOrNull()?.let { parts[0] to it }
        }.toMap()
    }
}
