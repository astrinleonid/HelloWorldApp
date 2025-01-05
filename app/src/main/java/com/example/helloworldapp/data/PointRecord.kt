package com.example.helloworldapp.data

data class PointRecord(
    val pointNumber: Int,
    var isRecorded: Boolean = false,
    var label: RecordLabel = RecordLabel.NOLABEL
)

enum class RecordLabel {
    NOLABEL,
    POSITIVE,
    NEGATIVE,
    UNDETERMINED
}

