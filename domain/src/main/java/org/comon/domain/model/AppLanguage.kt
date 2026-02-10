package org.comon.domain.model

enum class AppLanguage(
    val localeTag: String,
    val displayName: String
) {
    SYSTEM("", "System"),
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    INDONESIAN("in", "Bahasa Indonesia");

    companion object {
        fun fromLocaleTag(tag: String): AppLanguage =
            entries.firstOrNull { it.localeTag.equals(tag, ignoreCase = true) } ?: SYSTEM
    }
}
