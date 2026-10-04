package com.github.hidesystemmessages

import android.annotation.SuppressLint
import android.view.View
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage
import com.discord.views.CheckedSetting

/**
 * Toggles are saved through Aliucord's SettingsAPI, which writes to
 * Aliucord/settings/HideSystemMessages.json, so choices survive app restarts.
 */
@SuppressLint("SetTextI18n")
class PluginSettings(private val settings: SettingsAPI) : SettingsPage() {

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Hide System Messages")

        val ctx = view.context
        HideSystemMessages.CATEGORIES.forEach { cat ->
            val toggle = Utils.createCheckedSetting(
                ctx, CheckedSetting.ViewType.SWITCH, "Hide ${cat.label}", cat.description
            ).apply {
                isChecked = settings.isHidden(cat)
                setOnCheckedListener { checked ->
                    settings.setBool(cat.key, checked)
                    Utils.showToast("Saved. Reopen the channel to refresh it.")
                }
            }
            addView(toggle)
        }
    }
}
