package com.semo.keyboard.diag

enum class DiagLevel { PASS, WARN, FAIL, INFO }

data class DiagItem(
    val group: String,
    val name: String,
    val level: DiagLevel,
    val detail: String,
    val millis: Long
)
