package com.cva.duoscreen.source

data class SpeedAlert(
    val speedKmh: Int = 0,
    val speedLimit: Int? = null,
    val alertText: String = "",
    val isOverLimit: Boolean = false,
    val hasGps: Boolean = false,
    val updatedAt: Long = 0L
)