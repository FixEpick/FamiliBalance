package com.example.familibalance.util

fun getCurrentDateString(): String {
    return java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

fun getCurrentTimeString(): String {
    return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}
