package com.example.bruxismdetector.mibanddbconverter

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectImporter {

    // Java-friendly Interface for callback
    interface OnCompleteListener {
        fun onComplete()
    }

    companion object {
        @JvmStatic
        fun getRequiredPermissions(): Set<String> {
            return setOf(
                HealthPermission.getReadPermission(SleepSessionRecord::class),
                HealthPermission.getReadPermission(HeartRateRecord::class),
                HealthPermission.getReadPermission(OxygenSaturationRecord::class)
            )
        }
    }

    fun isAvailable(context: Context): Boolean {
        val status = HealthConnectClient.getSdkStatus(context)

        // Log the exact status code
        when (status) {
            HealthConnectClient.SDK_AVAILABLE -> android.util.Log.i("HealthConnect", "SDK_AVAILABLE: All good!")
            HealthConnectClient.SDK_UNAVAILABLE -> android.util.Log.e("HealthConnect", "SDK_UNAVAILABLE: Not supported or not installed.")
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> android.util.Log.w("HealthConnect", "UPDATE_REQUIRED: Health Connect app needs an update from Play Store.")
            else -> android.util.Log.e("HealthConnect", "UNKNOWN STATUS: $status")
        }

        return status == HealthConnectClient.SDK_AVAILABLE
    }

    fun importData(context: Context, progressReport: ProgressReport?, listener: OnCompleteListener) {
        if (!isAvailable(context)) {
            listener.onComplete()
            return
        }

        val client = HealthConnectClient.getOrCreate(context)
        val destDb = com.example.bruxismdetector.mibanddbconverter.SleepDatabaseHelper(context)

        // Health Connect requires async tasks. Let's use IO Dispatcher.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Health Connect allows reading up to the past 30 days
                val endTime = Instant.now()
                val startTime = endTime.minus(30, ChronoUnit.DAYS)
                val timeRange = TimeRangeFilter.between(startTime, endTime)

                // --- A. Import Sessions & Stages ---
                progressReport?.setTitle("Importing Sleep Sessions...")
                val sleepRequest = ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = timeRange
                )
                val sleepResponse = client.readRecords(sleepRequest)
                val sessions = sleepResponse.records

                var sessionProgress = 0
                for (session in sessions) {
                    val startSec = session.startTime.epochSecond
                    val endSec = session.endTime.epochSecond
                    val totalDurMins = ChronoUnit.MINUTES.between(session.startTime, session.endTime).toInt()

                    var deepMins = 0
                    var lightMins = 0
                    var remMins = 0
                    var awakeMins = 0

                    // Importiamo le single phases
                    for (stage in session.stages) {
                        val stageStartSec = stage.startTime.epochSecond
                        val stageDurMins = ChronoUnit.MINUTES.between(stage.startTime, stage.endTime).toInt()

                        val mappedStage = mapStage(stage.stage)

                        when (mappedStage) {
                            1 -> awakeMins += stageDurMins
                            2 -> lightMins += stageDurMins
                            3 -> deepMins += stageDurMins
                            4 -> remMins += stageDurMins
                        }

                        destDb.addStage(stageStartSec, mappedStage)
                    }

                    // Insert session (ignore duplicates thanks to SQLiteDatabase.CONFLICT_IGNORE)
                    destDb.addSleepSession(
                        startSec, endSec,
                        if (awakeMins > 0) 1 else 0,
                        totalDurMins, deepMins, lightMins, remMins, awakeMins
                    )

                    sessionProgress++
                    progressReport?.setProgress((100.0 * sessionProgress / sessions.size).toInt())
                }

                // --- B. Import Heart Rate ---
                progressReport?.setTitle("Importing Heart Rate...")
                val hrRequest = ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = timeRange
                )
                val hrResponse = client.readRecords(hrRequest)

                var hrProgress = 0
                for (record in hrResponse.records) {
                    for (sample in record.samples) {
                        destDb.addHeartRate(sample.time.epochSecond, sample.beatsPerMinute.toInt())
                    }
                    hrProgress++
                    progressReport?.setProgress((100.0 * hrProgress / hrResponse.records.size).toInt())
                }

                // --- C. Import SpO2 (Iterazione giornaliera sicura) ---
                progressReport?.setTitle("Importing SpO2...")

                var spo2Inserted = 0
                var spo2Duplicates = 0

                var currentStart = startTime
                while (currentStart.isBefore(endTime)) {
                    // Calcoliamo la fine del giorno (massimo fino a endTime)
                    val currentEnd = currentStart.plus(1, ChronoUnit.DAYS).coerceAtMost(endTime)

                    // Controllo di sicurezza: se per qualsiasi motivo coincidono, usciamo dal ciclo
                    if (!currentStart.isBefore(currentEnd)) {
                        break
                    }

                    try {
                        val spo2Request = ReadRecordsRequest(
                            recordType = OxygenSaturationRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(currentStart, currentEnd)
                        )

                        val spo2Response = client.readRecords(spo2Request)

                        for (record in spo2Response.records) {
                            val timestamp = record.time.epochSecond
                            val spo2 = record.percentage.value.toInt()

                            if (destDb.addSpO2(timestamp, spo2)) {
                                spo2Inserted++
                            } else {
                                spo2Duplicates++
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("HealthConnect", "Errore import SpO2 per il blocco: $currentStart", e)
                    }

                    // Avanziamo al giorno successivo
                    currentStart = currentEnd
                }

                android.util.Log.i(
                    "HealthConnect",
                    "SpO2 Import Complete: DB inserted=$spo2Inserted, duplicates=$spo2Duplicates"
                )

                progressReport?.setProgress(100)

                // Note: stress is not supported

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // notify ui
                listener.onComplete()
            }
        }
    }

    // Map health connect codes (1=Awake, 2=Light, 3=Deep, 4=REM)
    private fun mapStage(hcStage: Int): Int {
        return when (hcStage) {
            SleepSessionRecord.STAGE_TYPE_AWAKE -> 1
            SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> 1
            SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> 1
            SleepSessionRecord.STAGE_TYPE_LIGHT -> 2
            SleepSessionRecord.STAGE_TYPE_DEEP -> 3
            SleepSessionRecord.STAGE_TYPE_REM -> 4
            else -> 1 // Unknown stages: treat as awake
        }
    }
}