package com.github.andreyasadchy.xtra.ui.chat

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.github.andreyasadchy.xtra.model.LoggedIn
import com.github.andreyasadchy.xtra.model.User
import com.github.andreyasadchy.xtra.model.chat.*
import com.github.andreyasadchy.xtra.repository.PlayerRepository
import com.github.andreyasadchy.xtra.repository.TwitchService
import com.github.andreyasadchy.xtra.ui.common.BaseViewModel
import com.github.andreyasadchy.xtra.ui.player.ChatReplayManager
import com.github.andreyasadchy.xtra.ui.view.chat.ChatView
import com.github.andreyasadchy.xtra.ui.view.chat.MAX_ADAPTER_COUNT
import com.github.andreyasadchy.xtra.util.SingleLiveEvent
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.chat.*
import com.github.andreyasadchy.xtra.util.nullIfEmpty
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.asReversed
import kotlin.collections.associateBy
import kotlin.collections.chunked
import kotlin.collections.contains
import kotlin.collections.containsKey
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.hashSetOf
import kotlin.collections.isNotEmpty
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set

class ChatViewModel @Inject constructor(
        private val repository: TwitchService,
        private val playerRepository: PlayerRepository) : BaseViewModel(), ChatView.MessageSenderCallback {

    val recentEmotes: LiveData<List<Emote>> by lazy {
        MediatorLiveData<List<Emote>>().apply {
            addSource(emotesFromSets) { twitch ->
                removeSource(emotesFromSets)
                addSource(_otherEmotes) { other ->
                    removeSource(_otherEmotes)
                    addSource(playerRepository.loadRecentEmotes()) { recent ->
                        value = recent.filter { (twitch.contains<Emote>(it) || other.contains(it)) }
                    }
                }
            }
        }
    }
    private val _otherEmotes = MutableLiveData<List<Emote>>()
    val otherEmotes: LiveData<List<Emote>>
        get() = _otherEmotes

    val recentMessages = MutableLiveData<List<LiveChatMessage>>()
    val globalBadges = MutableLiveData<List<TwitchBadge>?>()
    val channelBadges = MutableLiveData<List<TwitchBadge>>()
    val cheerEmotes = MutableLiveData<List<CheerEmote>>()
    var emoteSetsAdded = false
    val emotesFromSets = MutableLiveData<List<Emote>>()
    val emotesLoaded = MutableLiveData<Boolean>()
    val roomState = MutableLiveData<RoomState>()
    val command = MutableLiveData<Command>()

    private val _chatMessages by lazy {
        MutableLiveData<MutableList<ChatMessage>>().apply { value = Collections.synchronizedList(ArrayList(MAX_ADAPTER_COUNT + 1)) }
    }
    val chatMessages: LiveData<MutableList<ChatMessage>>
        get() = _chatMessages
    private val _newMessage by lazy { MutableLiveData<ChatMessage>() }
    val newMessage: LiveData<ChatMessage>
        get() = _newMessage

    private var chat: ChatController? = null

    private val _newChatter by lazy { SingleLiveEvent<Chatter>() }
    val newChatter: LiveData<Chatter>
        get() = _newChatter

    val chatters: Collection<Chatter>
        get() = (chat as LiveChatController).chatters.values

    fun startLive(user: User, useHelix: Boolean, helixClientId: String?, gqlClientId: String, channelId: String?, channelLogin: String?, channelName: String?, enableRecentMsg: Boolean? = false, recentMsgLimit: String? = null) {
        if (chat == null && channelLogin != null && channelName != null) {
            chat = LiveChatController(user, helixClientId, channelId, channelLogin, channelName)
            if (channelId != null) {
                init(useHelix, helixClientId, gqlClientId, user.token.nullIfEmpty(), channelId, channelLogin, enableRecentMsg, recentMsgLimit)
            }
        }
    }

    fun startReplay(user: User, useHelix: Boolean, helixClientId: String?, gqlClientId: String, channelId: String?, videoId: String, startTime: Double, getCurrentPosition: () -> Double) {
        if (chat == null) {
            chat = VideoChatController(gqlClientId, videoId, startTime, getCurrentPosition)
            if (channelId != null) {
                init(useHelix, helixClientId, gqlClientId, user.token.nullIfEmpty(), channelId)
            }
        }
    }

    fun start() {
        chat?.start()
    }

    fun stop() {
        chat?.pause()
    }

    override fun send(message: CharSequence) {
        chat?.send(message)
    }

    override fun onCleared() {
        chat?.stop()
        super.onCleared()
    }

    private fun init(useHelix: Boolean, helixClientId: String?, gqlClientId: String, token: String?, channelId: String, channelLogin: String? = null, enableRecentMsg: Boolean? = false, recentMsgLimit: String? = null) {
        chat?.start()
        viewModelScope.launch {
            if (channelLogin != null && enableRecentMsg == true && recentMsgLimit != null) {
                try {
                    val get = playerRepository.loadRecentMessages(channelLogin, recentMsgLimit).body()?.messages
                    if (get != null && get.isNotEmpty()) {
                        recentMessages.postValue(get!!)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load recent messages for channel $channelLogin", e)
                }
            }
            savedGlobalBadges.also {
                if (it != null) {
                    globalBadges.value = it
                } else {
                    try {
                        val badges = playerRepository.loadGlobalBadges().body()?.badges
                        if (badges != null) {
                            savedGlobalBadges = badges
                            globalBadges.value = badges
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global badges", e)
                    }
                }
            }
            try {
                channelBadges.postValue(playerRepository.loadChannelBadges(channelId).body()?.badges)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load badges for channel $channelId", e)
            }
            val list = mutableListOf<Emote>()
            try {
                val channelStv = playerRepository.loadStvEmotes(channelId)
                channelStv.body()?.emotes?.let(list::addAll)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load 7tv emotes for channel $channelId", e)
            }
            try {
                val channelBttv = playerRepository.loadBttvEmotes(channelId)
                channelBttv.body()?.emotes?.let(list::addAll)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load BTTV emotes for channel $channelId", e)
            }
            try {
                val channelFfz = playerRepository.loadBttvFfzEmotes(channelId)
                channelFfz.body()?.emotes?.let(list::addAll)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load FFZ emotes for channel $channelId", e)
            }
            globalStvEmotes.also {
                if (it != null) {
                    list.addAll(it)
                } else {
                    try {
                        val emotes = playerRepository.loadGlobalStvEmotes().body()?.emotes
                        if (emotes != null) {
                            globalStvEmotes = emotes
                            list.addAll(emotes)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global 7tv emotes", e)
                    }
                }
            }
            globalBttvEmotes.also {
                if (it != null) {
                    list.addAll(it)
                } else {
                    try {
                        val emotes = playerRepository.loadGlobalBttvEmotes().body()?.emotes
                        if (emotes != null) {
                            globalBttvEmotes = emotes
                            list.addAll(emotes)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global BTTV emotes", e)
                    }
                }
            }
            globalFfzEmotes.also {
                if (it != null) {
                    list.addAll(it)
                } else {
                    try {
                        val emotes = playerRepository.loadBttvGlobalFfzEmotes().body()?.emotes
                        if (emotes != null) {
                            globalFfzEmotes = emotes
                            list.addAll(emotes)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to load global FFZ emotes", e)
                    }
                }
            }
            (chat as? LiveChatController)?.addEmotes(list)
            _otherEmotes.postValue(list)
            try {
                val get = if (useHelix) repository.loadCheerEmotes(helixClientId, token, channelId)
                else repository.loadCheerEmotesGQLQuery(gqlClientId, channelId)
                if (get != null) {
                    cheerEmotes.postValue(get!!)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load cheermotes for channel $channelId", e)
            }
            emotesLoaded.value = true
        }
    }

    private inner class LiveChatController(
            private val user: User,
            private val helixClientId: String?,
            private val channelId: String?,
            private val channelLogin: String,
            displayName: String) : ChatController() {

        private var chat: LiveChatThread? = null
        private val allEmotesMap = mutableMapOf<String, Emote>()

        val chatters = ConcurrentHashMap<String?, Chatter>()

        init {
            chatters[displayName] = Chatter(displayName)
        }

        override fun send(message: CharSequence) {
            chat?.send(message)
            val usedEmotes = hashSetOf<RecentEmote>()
            val currentTime = System.currentTimeMillis()
            message.split(' ').forEach { word ->
                allEmotesMap[word]?.let { usedEmotes.add(RecentEmote(word, it.url, currentTime)) }
            }
            if (usedEmotes.isNotEmpty()) {
                playerRepository.insertRecentEmotes(usedEmotes)
            }
        }

        override fun start() {
            pause()
            chat = TwitchApiHelper.startChat(channelLogin, user.name.nullIfEmpty(), user.token.nullIfEmpty(), this, this, this, this)
        }

        override fun pause() {
            chat?.disconnect()
        }

        override fun stop() {
            pause()
        }

        override fun onMessage(message: ChatMessage) {
            super.onMessage(message)
            if (message.displayName != null && !chatters.containsKey(message.displayName)) {
                val chatter = Chatter(message.displayName)
                chatters[message.displayName] = chatter
                _newChatter.postValue(chatter)
            }
        }

        override fun onUserState(sets: List<String>?) {
            if (!emoteSetsAdded && helixClientId != null && user.token.nullIfEmpty() != null) {
                if (savedEmoteSets != sets) {
                    viewModelScope.launch {
                        val emotes = mutableListOf<Emote>()
                        sets?.asReversed()?.chunked(25)?.forEach {
                            try {
                                val list = repository.loadEmotesFromSet(helixClientId, user.token, it)
                                if (list != null) {
                                    emotes.addAll(list)
                                }
                            } catch (e: Exception) {
                            }
                        }
                        if (emotes.isNotEmpty()) {
                            savedEmoteSets = sets
                            savedEmotesFromSets = emotes
                            emoteSetsAdded = true
                            val items = emotes.filter { it.ownerId == channelId }
                            for (item in items.asReversed()) {
                                emotes.add(0, item)
                            }
                            addEmotes(emotes)
                            emotesFromSets.value = emotes
                        }
                    }
                } else {
                    val emotes = mutableListOf<Emote>()
                    savedEmotesFromSets?.let { emotes.addAll(it) }
                    if (emotes.isNotEmpty()) {
                        emoteSetsAdded = true
                        val items = emotes.filter { it.ownerId == channelId }
                        for (item in items.asReversed()) {
                            emotes.add(0, item)
                        }
                        addEmotes(emotes)
                        viewModelScope.launch {
                            try {
                                emotesFromSets.value = emotes!!
                            } catch (e: Exception) {
                            }
                        }
                    }
                }
            }
        }

        override fun onRoomState(list: RoomState) {
            roomState.postValue(list)
        }

        override fun onCommand(list: Command) {
            command.postValue(list)
        }

        fun addEmotes(list: List<Emote>) {
            if (user is LoggedIn) {
                allEmotesMap.putAll(list.associateBy { it.name })
            }
        }
    }

    private inner class VideoChatController(
            private val clientId: String,
            private val videoId: String,
            private val startTime: Double,
            private val getCurrentPosition: () -> Double) : ChatController() {

        private var chatReplayManager: ChatReplayManager? = null

        override fun send(message: CharSequence) {

        }

        override fun start() {
            stop()
            chatReplayManager = ChatReplayManager(clientId, repository, videoId, startTime, getCurrentPosition, this, { _chatMessages.postValue(ArrayList()) }, viewModelScope)
        }

        override fun pause() {
            chatReplayManager?.stop()
        }

        override fun stop() {
            chatReplayManager?.stop()
        }

        override fun onUserState(list: List<String>?) {
        }

        override fun onRoomState(list: RoomState) {
        }

        override fun onCommand(list: Command) {
        }
    }

    private abstract inner class ChatController : OnChatMessageReceivedListener, OnUserStateReceivedListener, OnRoomStateReceivedListener, OnCommandReceivedListener {
        abstract fun send(message: CharSequence)
        abstract fun start()
        abstract fun pause()
        abstract fun stop()

        override fun onMessage(message: ChatMessage) {
            _chatMessages.value!!.add(message)
            _newMessage.postValue(message)
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"

        private var savedEmoteSets: List<String>? = null
        private var savedEmotesFromSets: List<Emote>? = null
        private var savedGlobalBadges: List<TwitchBadge>? = null
        private var globalStvEmotes: List<StvEmote>? = null
        private var globalBttvEmotes: List<BttvEmote>? = null
        private var globalFfzEmotes: List<FfzEmote>? = null
    }
}