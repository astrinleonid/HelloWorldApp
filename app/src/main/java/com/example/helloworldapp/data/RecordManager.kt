// RecordManager.kt
package com.example.helloworldapp.data

import AppConfig
import android.util.Log
import com.example.helloworldapp.R
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object RecordManager {
    private val pointRecords = mutableMapOf<String, MutableMap<Int, PointRecord>>() // recordId -> (pointNumber -> record)
    private var currentRecordId: String? = null
    private val recordsList = mutableSetOf<String>()  // Store all record IDs

    fun initializeRecord(recordId: String, numPoints: Int = 10) {
        currentRecordId = recordId
        recordsList.add(recordId)  // Add to list of records
        val recordMap = mutableMapOf<Int, PointRecord>()
        for (i in 1..numPoints) {
            recordMap[i] = PointRecord(i)
        }
        pointRecords[recordId] = recordMap
    }

    fun setRecorded(recordId: String, pointNumber: Int, isRecorded: Boolean) {
        pointRecords[recordId]?.get(pointNumber)?.isRecorded = isRecorded
    }

    fun setLabel(recordId: String, pointNumber: Int, label: RecordLabel) {
        pointRecords[recordId]?.get(pointNumber)?.label = label
    }

    fun getPointRecord(recordId: String, pointNumber: Int): PointRecord? {
        return pointRecords[recordId]?.get(pointNumber)
    }

    fun getAllPointRecords(recordId: String): Map<Int, PointRecord>? {
        return pointRecords[recordId]?.toMap()
    }

    fun syncWithServer(recordId: String, callback: (Boolean) -> Unit) {
        if (!AppConfig.online) {
            callback(true)
            return
        }

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/get_wav_files?folderId=$recordId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val jsonData = response.body?.string() ?: return
                    val json = JSONObject(jsonData)
                    val files = json.getString("files").split(" ")
                    val labels = json.getJSONObject("labels")

                    // Reset all records to not recorded
                    pointRecords[recordId]?.values?.forEach { it.isRecorded = false }

                    // Update recorded status and labels from server data
                    files.forEach { filename ->
                        val pointNumber = extractPointNumber(filename)
                        if (pointNumber != null) {
                            setRecorded(recordId, pointNumber, true)

                            // Update label if exists
                            val label = when(labels.optString(filename)) {
                                "positive" -> RecordLabel.POSITIVE
                                "negative" -> RecordLabel.NEGATIVE
                                else -> RecordLabel.UNDETERMINED
                            }
                            setLabel(recordId, pointNumber, label)
                        }
                    }
                    callback(true)
                } catch (e: Exception) {
                    callback(false)
                }
            }
        })
    }

    private fun extractPointNumber(filename: String): Int? {
        return if (AppConfig.online) {
            val match = Regex("""(\d+)\.wav$""").find(filename)
            match?.groupValues?.get(1)?.toIntOrNull()
        } else {
            filename.substringAfter("btn_")
                .substringBefore("_")
                .toIntOrNull()
        }
    }

    fun getAllRecordIds(): List<String> {
        return recordsList.toList()
    }
    fun getRecordInfo(recordId: String): Map<String, Int> {
        val record = pointRecords[recordId]
        return mapOf(
            "totalPoints" to (record?.size ?: 0),
            "completedPoints" to (record?.count { it.value.isRecorded } ?: 0)
        )
    }


    fun getButtonColor(record: PointRecord): Int {
        return when {
            !record.isRecorded -> R.drawable.button_not_recorded
            record.label == RecordLabel.POSITIVE -> R.drawable.button_positive
            record.label == RecordLabel.NEGATIVE -> R.drawable.button_negative
            record.label == RecordLabel.UNDETERMINED -> R.drawable.button_undetermined
            else -> R.drawable.button_recorded
        }
    }
    fun deleteRecording(uniqueId: String?) {
        if (AppConfig.online) {
            deleteFromServer(uniqueId) { success, message ->
                if (success) {
                    // Clear the record from internal storage
                    pointRecords.remove(uniqueId)
                }
                // You could log the message if needed
                Log.d("RecordManager", message ?: "No message received")
            }
        } else {
            // Handle offline deletion
            pointRecords.remove(uniqueId)
        }
    }

    private fun deleteFromServer(uniqueId: String?, callback: (Boolean, String?) -> Unit) {
        val url = "${AppConfig.serverIP}/record_delete?record_id=${uniqueId}"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Error deleting record")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    callback(true, "Record deleted successfully")
                } else {
                    callback(false, "Failed to delete record: ${response.message}")
                }
            }
        })
    }

    private fun fetchButtonColorsFromServer(recordId: String?, callback: (List<Boolean>?) -> Unit) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("${AppConfig.serverIP}/get_button_states?record_id=$recordId")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (res.isSuccessful) {
                        val jsonData = res.body?.string()
                        val jsonArray = JSONArray(jsonData)
                        val buttonColors = mutableListOf<Boolean>()
                        for (i in 0 until jsonArray.length()) {
                            buttonColors.add(jsonArray.getBoolean(i))
                        }
                        callback(buttonColors)
                    } else {
                        callback(null)
                    }
                }
            }
        })
    }


}