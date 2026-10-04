version = "1.1.0"
description = "Delete call messages for yourself only, and optionally hide whole categories of system messages. Persists across restarts."

aliucord {
    changelog.set(
        """
        # 1.1.0
        * Added "Delete (only for me)" to the menu that opens when you tap a call message
        * Long-press a call message as a fallback way to delete it
        * Settings: restore deleted call messages
        * "Hide all calls" is now off by default
        # 1.0.0
        * Initial release
        """.trimIndent()
    )
}
