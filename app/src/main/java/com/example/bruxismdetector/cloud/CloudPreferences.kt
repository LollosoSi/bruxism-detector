package com.example.bruxismdetector.cloud

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "cloud_prefs")

class CloudPreferences(private val context: Context) {
    companion object {
        val KEY_UUID = stringPreferencesKey("uuid")
        val KEY_PWD_ENC = stringPreferencesKey("cloud_pwd_enc")

        // Categorie Opt-In
        val KEY_OPT_IN_GENERAL = booleanPreferencesKey("opt_in_general") // Master switch (Sleep)
        val KEY_OPT_IN_SENSORS = booleanPreferencesKey("opt_in_sensors") // ACCEL, NOISE
        val KEY_OPT_IN_RAW = booleanPreferencesKey("opt_in_raw")         // RAW
        val KEY_OPT_IN_TRAINING = booleanPreferencesKey("opt_in_training")// TrainingData
        val KEY_OPT_IN_BEEP = booleanPreferencesKey("opt_in_beep")       // BeepData
        val KEY_OPT_IN_SLEEP = booleanPreferencesKey("opt_in_sleep")       // Sleep sensors data

        val KEY_ACCEPTED_AGREEMENT = intPreferencesKey("accepted_agreement_version") // Agreement versioning
    }

    fun getUUID(): String? = runBlocking { context.dataStore.data.map { it[KEY_UUID] }.first() }

    fun getPassword(): String? = runBlocking {
        context.dataStore.data.map { it[KEY_PWD_ENC] }.first()?.let { CryptoUtil.decrypt(it) }
    }

    fun getAcceptedAgreementVersion(): Int = runBlocking {
        context.dataStore.data.map { it[KEY_ACCEPTED_AGREEMENT] ?: 0 }.first()
    }

    fun setAcceptedAgreementVersion(version: Int) = runBlocking {
        context.dataStore.edit { it[KEY_ACCEPTED_AGREEMENT] = version }
    }

    fun setUUID(value: String) = runBlocking {
        context.dataStore.edit { it[KEY_UUID] = value }
    }

    // Remove UUID
    fun resetUUID() = runBlocking {
        context.dataStore.edit { it.remove(KEY_UUID) }
    }

    // Remove UUID
    fun resetPassword() = runBlocking {
        context.dataStore.edit { it.remove(KEY_PWD_ENC) }
    }

    fun setPassword(password: String) = runBlocking {
        val encrypted = CryptoUtil.encrypt(password)
        context.dataStore.edit { it[KEY_PWD_ENC] = encrypted }
    }

    // Opt In Setters
    fun setOptInGeneral(value: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_OPT_IN_GENERAL] = value }
    }

    fun setOptInSensors(value: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_OPT_IN_SENSORS] = value }
    }

    fun setOptInRaw(value: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_OPT_IN_RAW] = value }
    }

    fun setOptInTraining(value: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_OPT_IN_TRAINING] = value }
    }

    fun setOptInBeep(value: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_OPT_IN_BEEP] = value }
    }

    fun setOptInSleep(value: Boolean) = runBlocking {
        context.dataStore.edit { it[KEY_OPT_IN_SLEEP] = value }
    }






    // Lettura Permessi
    fun isOptInGeneral(): Boolean = runBlocking { context.dataStore.data.map { it[KEY_OPT_IN_GENERAL] ?: false }.first() }
    fun isOptInSensors(): Boolean = runBlocking { context.dataStore.data.map { it[KEY_OPT_IN_SENSORS] ?: false }.first() }
    fun isOptInRaw(): Boolean = runBlocking { context.dataStore.data.map { it[KEY_OPT_IN_RAW] ?: false }.first() }
    fun isOptInTraining(): Boolean = runBlocking { context.dataStore.data.map { it[KEY_OPT_IN_TRAINING] ?: false }.first() }
    fun isOptInBeep(): Boolean = runBlocking { context.dataStore.data.map { it[KEY_OPT_IN_BEEP] ?: false }.first() }
    fun isOptInSleep(): Boolean = runBlocking { context.dataStore.data.map { it[KEY_OPT_IN_SLEEP] ?: false }.first() }

}