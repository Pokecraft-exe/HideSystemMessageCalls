package com.github.hidesystemmessages

import android.annotation.SuppressLint
import android.view.View
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.discord.views.CheckedSetting
import com.lytefast.flexinput.R

/**
 * Everything is saved through Aliucord's SettingsAPI
 * (Aliucord/settings/HideSystemMessages.json), so it survives app restarts.
 */
@SuppressLint("SetTextI18n")
class PluginSettings(private val plugin: HideSystemMessages) : SettingsPage() {

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Hide System Messages")
        val ctx = view.context
        val settings = plugin.pluginSettings

        // Restore individually deleted messages
        addView(TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
            fun label() = "Restore deleted call messages (${plugin.deletedCount})"
            text = label()
            setOnClickListener {
                plugin.restoreAllDeleted()
                text = label()
                Utils.showToast("Restored")
            }
        })

        HideSystemMessages.CATEGORIES.forEach { cat ->
            addView(
                Utils.createCheckedSetting(
                    ctx, CheckedSetting.ViewType.SWITCH, "Hide all: ${cat.label}", cat.description
                ).apply {
                    isChecked = settings.isHidden(cat)
                    setOnCheckedListener { checked ->
                        settings.setBool(cat.key, checked)
                        Utils.showToast("Saved. Reopen the channel to refresh it.")
                    }
                }
            )
        }
    }
}
