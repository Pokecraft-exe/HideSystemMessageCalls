# HideSystemMessages (Aliucord plugin)

Locally hides Discord system messages — call messages by default, plus optional
joins, pins, boosts, group DM add/remove, name/icon changes, follows and thread notices.
Nothing is deleted on Discord; rows are just collapsed on your device.

Settings are stored with Aliucord's SettingsAPI (`Aliucord/settings/HideSystemMessages.json`),
so they persist across app restarts.

## Build
1. Clone the Aliucord plugin template: https://github.com/Aliucord/plugins-template
2. Copy this `HideSystemMessages` folder into the template root (next to `ExamplePlugin`).
3. Add `include(":HideSystemMessages")` to `settings.gradle.kts` if it isn't auto-included.
4. Run `./gradlew :HideSystemMessages:make`
5. Copy `HideSystemMessages/build/HideSystemMessages.zip` to `/sdcard/Aliucord/plugins/` and restart Discord.

## Use
Settings → Aliucord → Plugins → HideSystemMessages → gear icon, then toggle categories.
Reopen a channel to apply changes to messages already on screen.
