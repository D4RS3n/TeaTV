package com.teatv.app.chat

/**
 * Утилита для безопасного разделения длинных сообщений чата (превышающих лимит Twitch в 500 символов).
 * Разделяет текст по границам слов (пробелам или переводам строк), не разрывая слова и смайлики.
 */
object MessageSplitter {
    const val TWITCH_MAX_MESSAGE_LENGTH = 500

    fun split(text: String, maxLen: Int = TWITCH_MAX_MESSAGE_LENGTH): List<String> {
        val trimmed = text.trim()
        if (trimmed.length <= maxLen) {
            return if (trimmed.isEmpty()) emptyList() else listOf(trimmed)
        }

        val chunks = mutableListOf<String>()
        var remaining = trimmed

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLen) {
                chunks.add(remaining)
                break
            }

            // Ищем последний пробел или перевод строки в диапазоне [maxLen - 60 .. maxLen]
            val windowStart = (maxLen - 60).coerceAtLeast(0)
            val window = remaining.substring(windowStart, maxLen)
            val lastBreakInWindow = window.lastIndexOfAny(charArrayOf(' ', '\n', '\t'))

            val splitIndex = if (lastBreakInWindow >= 0) {
                windowStart + lastBreakInWindow
            } else {
                val lastBreak = remaining.substring(0, maxLen).lastIndexOfAny(charArrayOf(' ', '\n', '\t'))
                if (lastBreak > 0) lastBreak else maxLen
            }

            val chunk = remaining.substring(0, splitIndex).trim()
            if (chunk.isNotEmpty()) {
                chunks.add(chunk)
            }
            remaining = remaining.substring(splitIndex).trim()
        }

        return if (chunks.isEmpty()) listOf(trimmed.take(maxLen)) else chunks
    }
}
