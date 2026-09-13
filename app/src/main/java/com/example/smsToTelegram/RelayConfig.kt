package com.example.smsToTelegram

object RelayConfig {
    // Hardcoded Regex List for senders you want to relay
    val SENDER_REGEX_LIST = listOf(
        ".*",          // Matches everything
        // ".*BANK.*",
        // ".*Google.*"
    )
}
