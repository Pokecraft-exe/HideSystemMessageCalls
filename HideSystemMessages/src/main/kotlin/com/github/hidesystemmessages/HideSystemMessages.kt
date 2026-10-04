package com.github.hidesystemmessages

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemCallMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemSystemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry

/** A group of Discord message types that can be toggled together. */
data class Category(
    val key: String,
    val label: String,
    val description: String,
    val types: Set<Int>,
    val default: Boolean,
)

@AliucordPlugin(requiresRestart = false)
class HideSystemMessages : Plugin() {

    companion object {
        // Discord message type IDs: https://discord.com/developers/docs/resources/message#message-object-message-types
        val CATEGORIES = listOf(
            Category("hide_calls", "Calls", "\"X started a call\" / missed call messages", setOf(3), true),
            Category("hide_joins", "Member joins", "Server welcome / join messages", setOf(7), false),
            Category("hide_group", "Group DM add / remove", "\"X added Y to the group\", \"X left the group\"", setOf(1, 2), false),
            Category("hide_pins", "Pinned messages", "\"X pinned a message to this channel\"", setOf(6), false),
            Category("hide_boosts", "Boosts", "Server boost and boost level-up messages", setOf(8, 9, 10, 11), false),
            Category("hide_changes", "Name / icon changes", "Channel or group name and icon changes", setOf(4, 5), false),
            Category("hide_follow", "Channel follows", "\"X has added Y to this channel\"", setOf(12), false),
            Category("hide_threads", "Thread created", "\"X started a thread\"", setOf(18), false),
        )
    }

    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(settings)
    }

    override fun start(context: Context) {
        val intType = Int::class.javaPrimitiveType!!

        // Call messages have their own view holder
        patcher.after<WidgetChatListAdapterItemCallMessage>(
            "onConfigure", intType, ChatListEntry::class.java
        ) { param -> applyVisibility(itemView, param.args[1] as? ChatListEntry) }

        // All other system messages (joins, pins, boosts, group changes...)
        patcher.after<WidgetChatListAdapterItemSystemMessage>(
            "onConfigure", intType, ChatListEntry::class.java
        ) { param -> applyVisibility(itemView, param.args[1] as? ChatListEntry) }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    private fun shouldHide(entry: ChatListEntry?): Boolean {
        val type = (entry as? MessageEntry)?.message?.type ?: return false
        return CATEGORIES.any { type in it.types && settings.getBool(it.key, it.default) }
    }

    /**
     * Collapses the row instead of removing it from the list, so Discord's adapter
     * stays consistent. Recycled views are restored when they show a non-hidden message.
     */
    private fun applyVisibility(view: View, entry: ChatListEntry?) {
        val hide = shouldHide(entry)
        val lp = view.layoutParams ?: RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (hide) {
            view.visibility = View.GONE
            lp.height = 0
        } else {
            view.visibility = View.VISIBLE
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        view.layoutParams = lp
    }
}

/** Small helper so the settings page can read the same categories. */
internal fun SettingsAPI.isHidden(c: Category) = getBool(c.key, c.default)
