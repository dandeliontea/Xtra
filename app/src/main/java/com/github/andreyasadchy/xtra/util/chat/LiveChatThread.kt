package com.github.andreyasadchy.xtra.util.chat

import android.util.Log
import com.github.andreyasadchy.xtra.ui.view.chat.ChatView
import java.io.*
import java.net.Socket
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val TAG = "LiveChatThread"

class LiveChatThread(
        private val userName: String?,
        private val userToken: String?,
        private val channelName: String,
        private val listener: OnMessageReceivedListener) : Thread(), ChatView.MessageSenderCallback {
    private var socketIn: Socket? = null
    private var socketOut: Socket? = null
    private lateinit var readerIn: BufferedReader
    private var readerOut: BufferedReader? = null
    private lateinit var writerIn: BufferedWriter
    private var writerOut: BufferedWriter? = null
    private val hashChannelName: String = "#$channelName"
    private val messageSenderExecutor: Executor = Executors.newSingleThreadExecutor()
    private var isActive = true

    override fun run() {

        fun handlePing(writer: BufferedWriter) {
            write("PONG :tmi.twitch.tv", writer)
            writer.flush()
        }

        do {
            try {
                connect()
                while (true) {
                    val messageIn = readerIn.readLine()!!
                    messageIn.run {
                        when {
                            contains("PRIVMSG") -> listener.onMessage(this)
                            contains("USERNOTICE") -> listener.onUserNotice(this)
                            contains("CLEARMSG") -> listener.onClearMessage(this)
                            contains("CLEARCHAT") -> listener.onClearChat(this)
                            contains("NOTICE") -> listener.onNotice(this)
                            contains("ROOMSTATE") -> listener.onRoomState(this)
                            startsWith("PING") -> handlePing(writerIn)
                        }
                    }

                    if (userName != null) {
                        val messageOut = readerOut!!.readLine()!!
                        messageOut.run {
                            when {
                                contains("USERSTATE") -> listener.onUserState(this)
                                startsWith("PING") -> handlePing(writerOut!!)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Disconnecting from $hashChannelName")
                listener.onCommand("Disconnecting from $channelName - $e")
                close()
                sleep(1000L)
            } catch (e: Exception) {
                close()
                sleep(1000L)
            }
        } while (isActive)
    }

    private fun connect() {
        Log.d(TAG, "Connecting to Twitch IRC")
        try {
            socketIn = Socket("irc.twitch.tv", 6667).apply {
                readerIn = BufferedReader(InputStreamReader(getInputStream()))
                writerIn = BufferedWriter(OutputStreamWriter(getOutputStream()))
            }
            userName?.let {
                socketOut = Socket("irc.twitch.tv", 6667).apply {
                    readerOut = BufferedReader(InputStreamReader(getInputStream()))
                    writerOut = BufferedWriter(OutputStreamWriter(getOutputStream()))
                    write("PASS oauth:$userToken", writerOut)
                    write("NICK $it", writerOut)
                }
            }
            write("NICK justinfan${Random().nextInt(((9999 - 1000) + 1)) + 1000}", writerIn) //random number between 1000 and 9999
            write("CAP REQ :twitch.tv/tags twitch.tv/commands", writerIn, writerOut)
            write("JOIN $hashChannelName", writerIn, writerOut)
            writerIn.flush()
            writerOut?.flush()
            Log.d(TAG, "Successfully connected to - $hashChannelName")
            listener.onCommand(channelName, "join")
        } catch (e: IOException) {
            Log.e(TAG, "Error connecting to Twitch IRC", e)
            throw e
        }
    }

    fun disconnect() {
        if (isActive) {
            isActive = false
            close()
        }
    }

    private fun close() {
        try {
            socketIn?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error while closing socketIn", e)
            listener.onCommand("Error while closing socketIn - $e")
        }
        try {
            socketOut?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error while closing socketOut", e)
            listener.onCommand("Error while closing socketOut - $e")
        }
    }

    @Throws(IOException::class)
    private fun write(message: String, vararg writers: BufferedWriter?) {
        writers.forEach { it?.write(message + System.getProperty("line.separator")) }
    }

    override fun send(message: CharSequence) {
        messageSenderExecutor.execute {
            try {
                write("PRIVMSG $hashChannelName :$message", writerOut)
                writerOut?.flush()
                Log.d(TAG, "Sent message to $hashChannelName: $message")
            } catch (e: IOException) {
                Log.e(TAG, "Error sending message", e)
                listener.onCommand("Error sending message - $e")
            }
        }
    }

    interface OnMessageReceivedListener {
        fun onMessage(message: String)
        fun onCommand(message: String, type: String? = null)
        fun onClearMessage(message: String)
        fun onClearChat(message: String)
        fun onNotice(message: String)
        fun onUserNotice(message: String)
        fun onRoomState(message: String)
        fun onUserState(message: String)
    }
}
