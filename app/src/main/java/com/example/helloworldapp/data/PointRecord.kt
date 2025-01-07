package com.example.helloworldapp.data


data class Recording(
    val id: String,
    val points: Map<Int, PointRecord> = (1..10).associateWith { PointRecord(it) }
)
data class PointRecord(
    val pointNumber: Int,
    var isRecorded: Boolean = false,
    var label: RecordLabel = RecordLabel.NOLABEL,
    var fileName: String? = null
)

enum class RecordLabel {
    NOLABEL,
    POSITIVE,
    NEGATIVE,
    UNDETERMINED
}

