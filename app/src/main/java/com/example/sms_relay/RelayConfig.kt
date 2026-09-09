package com.example.sms_relay

object RelayConfig {
    const val TELEGRAM_BOT_TOKEN = "DUMMY_BOT_TOKEN"
    const val TELEGRAM_CHAT_ID = "DUMMY_CHAT_ID"

    // Hardcoded Regex List for senders you want to relay
    val SENDER_REGEX_LIST = listOf(
        ".*",          // Matches everything (for testing)
        // ".*BANK.*",
        // ".*Google.*",
        // ".*Zomato.*"
    )
}
