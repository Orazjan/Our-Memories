package com.example.ourmemories.Models

object Zodiac {
    fun getZodiacSign(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return ""
        try {
            val parts = dateString.split("[.,/-]".toRegex())

            if (parts.size < 2) return ""

            val day = parts[0].toInt()
            val month = parts[1].toInt()

            return when (month) {
                1 -> if (day < 20) "♑" else "♒"
                2 -> if (day < 19) "♒" else "♓"
                3 -> if (day < 21) "♓" else "♈"
                4 -> if (day < 20) "♈" else "♉"
                5 -> if (day < 21) "♉" else "♊"
                6 -> if (day < 21) "♊" else "♋"
                7 -> if (day < 23) "♋" else "♌"
                8 -> if (day < 23) "♌" else "♍"
                9 -> if (day < 23) "♍" else "♎"
                10 -> if (day < 23) "♎" else "♏"
                11 -> if (day < 22) "♏" else "♐"
                12 -> if (day < 22) "♐" else "♑"
                else -> ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }
}