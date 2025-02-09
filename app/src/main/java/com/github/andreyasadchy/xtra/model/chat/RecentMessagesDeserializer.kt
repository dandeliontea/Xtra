package com.github.andreyasadchy.xtra.model.chat

import android.content.Context
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.MessageListenerImpl
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class RecentMessagesDeserializer : JsonDeserializer<RecentMessagesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): RecentMessagesResponse {
        val messages = mutableListOf<LiveChatMessage>()
        for (i in json.asJsonObject.getAsJsonArray("messages")) {
            val appContext = XtraApp.INSTANCE.applicationContext
            val message = i.asString
            val liveMsg = when {
                message.contains("PRIVMSG") -> onMessage(message)
                message.contains("USERNOTICE") -> onUserNotice(message)
                message.contains("CLEARMSG") -> onClearMessage(appContext, message)
                message.contains("CLEARCHAT") -> onClearChat(appContext, message)
                message.contains("NOTICE") -> onNotice(message)
                else -> null
            }
            if (liveMsg != null) {
                messages.add(liveMsg)
            }
        }
        return RecentMessagesResponse(messages)
    }

    private fun onMessage(message: String): LiveChatMessage {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefix = parts[0]
        val prefixes = splitAndMakeMap(prefix, ";", "=")
        val messageInfo = parts[1] //:<user>!<user>@<user>.tmi.twitch.tv PRIVMSG #<channelName> :<message>
        val userName = messageInfo.substring(1, messageInfo.indexOf("!"))
        val userMessage: String
        val isAction: Boolean
        val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val index2 = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        messageInfo.substring(if (msgIndex != -1) msgIndex + 1 else index2 + 1).let { //from <message>
            if (!it.startsWith(MessageListenerImpl.ACTION)) {
                userMessage = it
                isAction = false
            } else {
                userMessage = it.substring(8, it.lastIndex)
                isAction = true
            }
        }
        var emotesList: MutableList<TwitchEmote>? = null
        val emotes = prefixes["emotes"]
        if (emotes != null) {
            val entries = splitAndMakeMap(emotes, "/", ":").entries
            emotesList = ArrayList(entries.size)
            entries.forEach { emote ->
                emote.value?.split(",")?.forEach { indexes ->
                    val index = indexes.split("-")
                    emotesList.add(TwitchEmote(emote.key, index[0].toInt(), index[1].toInt()))
                }
            }
        }
        var badgesList: MutableList<Badge>? = null
        val badges = prefixes["badges"]
        if (badges != null) {
            val entries = splitAndMakeMap(badges, ",", "/").entries
            badgesList = ArrayList(entries.size)
            entries.forEach {
                it.value?.let { value ->
                    badgesList.add(Badge(it.key, value))
                }
            }
        }
        return LiveChatMessage(
            id = prefixes["id"],
            userId = prefixes["user-id"],
            userName = userName,
            displayName = prefixes["display-name"]?.replace("""\\s""".toRegex(), " "),
            message = userMessage,
            color = prefixes["color"],
            isAction = isAction,
            isReward = prefixes["custom-reward-id"] != null,
            isFirst = prefixes["first-msg"] == "1",
            emotes = emotesList,
            badges = badgesList,
            timestamp = prefixes["tmi-sent-ts"]?.toLong()
        )
    }

    private fun onClearMessage(context: Context, message: String): LiveChatMessage {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefix = parts[0]
        val prefixes = splitAndMakeMap(prefix, ";", "=")
        val user = prefixes["login"]
        val messageInfo = parts[1]
        val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val index2 = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        val msg = messageInfo.substring(if (msgIndex != -1) msgIndex + 1 else index2 + 1)
        return LiveChatMessage(
            message = context.getString(R.string.chat_clearmsg, user, msg),
            color = "#999999",
            isAction = true,
            timestamp = prefixes["tmi-sent-ts"]?.toLong()
        )
    }

    private fun onClearChat(context: Context, message: String): LiveChatMessage? {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefix = parts[0]
        val prefixes = splitAndMakeMap(prefix, ";", "=")
        val duration = prefixes["ban-duration"]
        val messageInfo = parts[1]
        val userIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val index2 = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        val user = if (userIndex != -1) messageInfo.substring(userIndex + 1) else if (index2 != -1) messageInfo.substring(index2 + 1) else null
        val type = if (user == null) { "clearchat" } else { if (duration != null) { "timeout" } else { "ban" } }
        return LiveChatMessage(
            message = when (type) {
                "clearchat" -> context.getString(R.string.chat_clear)
                "timeout" -> context.getString(R.string.chat_timeout, user, TwitchApiHelper.getDurationFromSeconds(context, duration))
                "ban" -> context.getString(R.string.chat_ban, user)
                else -> return null
            },
            color = "#999999",
            isAction = true,
            timestamp = prefixes["tmi-sent-ts"]?.toLong()
        )
    }

    private fun onNotice(message: String): LiveChatMessage {
        val index = message.indexOf(":", message.indexOf(":") + 1)
        val index2 = message.indexOf(" ", message.indexOf("#") + 1)
        return LiveChatMessage(
            message = message.substring(if (index != -1) index + 1 else index2 + 1),
            color = "#999999",
            isAction = true
        )
    }

    private fun onUserNotice(message: String): LiveChatMessage? {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefix = parts[0]
        val prefixes = splitAndMakeMap(prefix, ";", "=")
        val system = prefixes["system-msg"]?.replace("\\s", " ")
        val messageInfo = parts[1]
        val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val index2 = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        val msg = if (msgIndex != -1) messageInfo.substring(msgIndex + 1) else if (index2 != -1) messageInfo.substring(index2 + 1) else null
        if (system != null) {
            var emotesList: MutableList<TwitchEmote>? = null
            val emotes = prefixes["emotes"]
            if (emotes != null && msg != null) {
                val entries = splitAndMakeMap(emotes, "/", ":").entries
                emotesList = ArrayList(entries.size)
                entries.forEach { emote ->
                    emote.value?.split(",")?.forEach { indexes ->
                        val index = indexes.split("-")
                        emotesList.add(TwitchEmote(emote.key, index[0].toInt() + system.length + 1, index[1].toInt() + system.length + 1))
                    }
                }
            }
            if (msg == null)
                println("null un")
            return LiveChatMessage(
                message = if (msg != null) "$system $msg" else system,
                color = "#999999",
                isAction = true,
                emotes = emotesList,
                timestamp = prefixes["tmi-sent-ts"]?.toLong()
            )
        } else return null
    }

    private fun splitAndMakeMap(string: String, splitRegex: String, mapRegex: String): Map<String, String?> {
        val list = string.split(splitRegex.toRegex()).dropLastWhile { it.isEmpty() }
        val map = LinkedHashMap<String, String?>()
        for (pair in list) {
            val kv = pair.split(mapRegex.toRegex()).dropLastWhile { it.isEmpty() }
            map[kv[0]] = if (kv.size == 2) kv[1] else null
        }
        return map
    }
}
