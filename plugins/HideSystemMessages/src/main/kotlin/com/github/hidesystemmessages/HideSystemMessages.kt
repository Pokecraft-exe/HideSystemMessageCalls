package com.github.hidesystemmessages

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.fragments.ConfirmDialog
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.discord.widgets.chat.list.WidgetChatList
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemCallMessage
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemSystemMessage
import com.discord.widgets.chat.list.entries.ChatListEntry
import com.discord.widgets.chat.list.entries.MessageEntry
import com.lytefast.flexinput.R
import java.util.WeakHashMap
import java.util.regex.Pattern

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
            Category("hide_calls", "Calls", "Hide ALL \"X started a call\" / missed call messages", setOf(3), false),
            Category("hide_joins", "Member joins", "Server welcome / join messages", setOf(7), false),
            Category("hide_group", "Group DM add / remove", "\"X added Y to the group\", \"X left the group\"", setOf(1, 2), false),
            Category("hide_pins", "Pinned messages", "\"X pinned a message to this channel\"", setOf(6), false),
            Category("hide_boosts", "Boosts", "Server boost and boost level-up messages", setOf(8, 9, 10, 11), false),
            Category("hide_changes", "Name / icon changes", "Channel or group name and icon changes", setOf(4, 5), false),
            Category("hide_follow", "Channel follows", "\"X has added Y to this channel\"", setOf(12), false),
            Category("hide_threads", "Thread created", "\"X started a thread\"", setOf(18), false),
        )

        const val KEY_DELETED = "deleted_ids"
        private const val TAP_WINDOW_MS = 2000L
        private val CALL_OPTION_PATTERN: Pattern = Pattern.compile("(voice|video) call", Pattern.CASE_INSENSITIVE)
    }

    /** Message IDs the user "deleted" for themselves. Saved in settings, so it survives restarts. */
    private val deletedIds = HashSet<Long>()

    /** Call-message rows currently on screen → the message ID they show. */
    private val callRows = WeakHashMap<View, Long>()

    /** Set when a call row is tapped, consumed when Discord's call menu opens. */
    private var pendingId: Long? = null
    private var pendingAt = 0L

    private val deleteItemId = View.generateViewId()

    init {
        settingsTab = SettingsTab(PluginSettings::class.java).withArgs(this)
    }

    // ---------- persistence ----------

    private fun loadDeleted() {
        deletedIds.clear()
        settings.getString(KEY_DELETED, "")
            .split(',').mapNotNullTo(deletedIds) { it.trim().toLongOrNull() }
    }

    private fun saveDeleted() = settings.setString(KEY_DELETED, deletedIds.joinToString(","))

    val deletedCount get() = deletedIds.size

    fun restoreAllDeleted() {
        deletedIds.clear()
        saveDeleted()
        refreshChat(null)
    }

    val pluginSettings: SettingsAPI get() = settings

    // ---------- lifecycle ----------

    override fun start(context: Context) {
        loadDeleted()
        val intType = Int::class.javaPrimitiveType!!

        // Call messages: hide if needed, remember the row, add long-press fallback
        patcher.after<WidgetChatListAdapterItemCallMessage>(
            "onConfigure", intType, ChatListEntry::class.java
        ) { param ->
            val entry = param.args[1] as? ChatListEntry
            val id = (entry as? MessageEntry)?.message?.id
            if (id != null) {
                callRows[itemView] = id
                itemView.setOnLongClickListener { confirmDelete(id); true }
            }
            applyVisibility(itemView, entry)
        }

        // All other system messages (joins, pins, boosts, group changes...)
        patcher.after<WidgetChatListAdapterItemSystemMessage>(
            "onConfigure", intType, ChatListEntry::class.java
        ) { param -> applyVisibility(itemView, param.args[1] as? ChatListEntry) }

        // Remember which call message was tapped
        patcher.before<View>("performClick") {
            var v: View? = this
            repeat(8) {
                val id = v?.let { callRows[it] }
                if (id != null) {
                    pendingId = id
                    pendingAt = SystemClock.uptimeMillis()
                    return@before
                }
                v = v?.parent as? View
            }
        }

        // When Discord's call menu opens right after that tap, add our "Delete" row to it
        patcher.after<Dialog>("show") {
            val id = pendingId ?: return@after
            if (SystemClock.uptimeMillis() - pendingAt > TAP_WINDOW_MS) { pendingId = null; return@after }
            // Content can still be attaching for bottom sheets, so wait one frame
            val dialog = this
            val decor = window?.decorView ?: return@after
            decor.post {
                if (injectDeleteOption(dialog, decor, id)) pendingId = null
            }
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
        callRows.clear()
    }

    // ---------- menu injection ----------

    private fun injectDeleteOption(dialog: Dialog, root: View, messageId: Long): Boolean {
        if (root.findViewById<View>(deleteItemId) != null) return true

        val options = ArrayList<TextView>()
        collectTextViews(root, options)
        val callOptions = options.filter { CALL_OPTION_PATTERN.matcher(it.text ?: "").find() }
        // Non-English clients: fall back to the clickable rows of the menu
        val anchor = (callOptions.ifEmpty { options.filter { it.isClickable } }).lastOrNull() ?: return false

        val item = makeDeleteItem(anchor) {
            dialog.dismiss()
            deleteForMe(messageId)
        }

        // Discord's option might be wrapped in a clickable row; insert next to the row
        var row: View = anchor
        while (row.parent is ViewGroup && !(row.parent as View).let { it is AdapterView<*> } &&
            (row.parent as ViewGroup).childCount == 1 && row.parent !== root) {
            row = row.parent as View
        }
        val container = row.parent
        when (container) {
            is ListView -> container.addFooterView(item)
            is AdapterView<*> -> return false
            is ViewGroup -> container.addView(
                item, container.indexOfChild(row) + 1,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            )
            else -> return false
        }
        return true
    }

    private fun collectTextViews(v: View, out: MutableList<TextView>) {
        if (v is TextView && v.visibility == View.VISIBLE && !v.text.isNullOrBlank()) out += v
        if (v is ViewGroup) for (i in 0 until v.childCount) collectTextViews(v.getChildAt(i), out)
    }

    private fun makeDeleteItem(template: TextView, onClick: () -> Unit): TextView {
        val ctx = template.context
        return TextView(ctx, null, 0, R.i.UiKit_Settings_Item_Icon).apply {
            id = deleteItemId
            text = "Delete (only for me)"
            val red = Color.parseColor("#ED4245")
            setTextColor(red)
            textSize = template.textSize / resources.displayMetrics.scaledDensity
            Utils.getResId("ic_delete_24dp", "drawable").takeIf { it != 0 }?.let { res ->
                ContextCompat.getDrawable(ctx, res)?.mutate()?.apply { setTint(red) }
                    ?.let { setCompoundDrawablesRelativeWithIntrinsicBounds(it, null, null, null) }
            }
            setOnClickListener { onClick() }
        }
    }

    private fun confirmDelete(messageId: Long) {
        ConfirmDialog()
            .setTitle("Delete call message?")
            .setDescription("It will be hidden only on this device, and stay hidden after restarts. You can restore it from the plugin settings.")
            .setIsDangerous(true)
            .apply { setOnOkListener { dismiss(); deleteForMe(messageId) } }
            .show(Utils.appActivity.supportFragmentManager, "HideSystemMessagesDelete")
    }

    // ---------- hiding ----------

    private fun deleteForMe(messageId: Long) {
        deletedIds += messageId
        saveDeleted()
        refreshChat(messageId)
        Utils.showToast("Call message deleted for you")
    }

    /** Re-renders one message (or the whole list) so the change shows immediately. */
    private fun refreshChat(messageId: Long?) = Utils.mainThread.post {
        val chat = Utils.widgetChatList ?: return@post
        val adapter = try { WidgetChatList.`access$getAdapter$p`(chat) } catch (e: Throwable) { null } ?: return@post
        if (messageId == null) { adapter.notifyDataSetChanged(); return@post }
        val idx = adapter.internalData.indexOfFirst { (it as? MessageEntry)?.message?.id == messageId }
        if (idx >= 0) adapter.notifyItemChanged(idx) else adapter.notifyDataSetChanged()
    }

    private fun shouldHide(entry: ChatListEntry?): Boolean {
        val message = (entry as? MessageEntry)?.message ?: return false
        if (message.id in deletedIds) return true
        val type = message.type ?: return false
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
        view.visibility = if (hide) View.GONE else View.VISIBLE
        lp.height = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
        view.layoutParams = lp
    }
}

/** Small helper so the settings page can read the same categories. */
internal fun SettingsAPI.isHidden(c: Category) = getBool(c.key, c.default)
