package com.example.ads_001

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class AssistantConversationStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConversationSummaries(): List<AssistantConversationSession> = synchronized(lock) {
        loadSessionsWithMigration()
            .filter { it.messages.isNotEmpty() }
            .sortedByDescending { it.updatedAtEpochMs }
    }

    fun getActiveConversationId(): String? = synchronized(lock) {
        prefs.getString(KEY_ACTIVE_CONVERSATION_ID, null)
    }

    fun getActiveConversation(): AssistantConversationSession = synchronized(lock) {
        val sessions = loadSessionsWithMigration().toMutableList()
        val activeId = prefs.getString(KEY_ACTIVE_CONVERSATION_ID, null)
        val activeConversation = sessions.firstOrNull { it.conversationId == activeId }
            ?: sessions.maxByOrNull { it.updatedAtEpochMs }
            ?: AssistantConversationSession.create()

        if (sessions.none { it.conversationId == activeConversation.conversationId }) {
            sessions.add(activeConversation)
            saveSessions(sessions)
        }
        saveActiveConversationId(activeConversation.conversationId)
        return activeConversation
    }

    fun createConversation(): AssistantConversationSession = synchronized(lock) {
        val sessions = loadSessionsWithMigration().toMutableList()
        val activeId = prefs.getString(KEY_ACTIVE_CONVERSATION_ID, null)
        val existingEmptyConversation = sessions.firstOrNull {
            it.conversationId == activeId && it.messages.isEmpty()
        }
        if (existingEmptyConversation != null) {
            saveActiveConversationId(existingEmptyConversation.conversationId)
            return existingEmptyConversation
        }

        val conversation = AssistantConversationSession.create()
        sessions.add(conversation)
        saveSessions(sessions)
        saveActiveConversationId(conversation.conversationId)
        return conversation
    }

    fun selectConversation(conversationId: String): AssistantConversationSession? = synchronized(lock) {
        val sessions = loadSessionsWithMigration()
        val conversation = sessions.firstOrNull { it.conversationId == conversationId } ?: return null
        saveActiveConversationId(conversation.conversationId)
        return conversation
    }

    fun appendMessageToActive(message: AssistantConversationMessage): AssistantConversationSession = synchronized(lock) {
        val sessions = loadSessionsWithMigration().toMutableList()
        val activeId = prefs.getString(KEY_ACTIVE_CONVERSATION_ID, null)
        val currentConversation = sessions.firstOrNull { it.conversationId == activeId }
            ?: AssistantConversationSession.create()

        val updatedMessages = currentConversation.messages
            .filterNot { it.messageId == message.messageId }
            .plus(message)
            .sortedBy { it.createdAtEpochMs }
            .takeLast(MAX_MESSAGES_PER_CONVERSATION)

        val updatedConversation = currentConversation.copy(
            title = buildConversationTitle(currentConversation.title, updatedMessages),
            updatedAtEpochMs = updatedMessages.lastOrNull()?.createdAtEpochMs ?: System.currentTimeMillis(),
            messages = updatedMessages
        )

        val replaced = sessions.map {
            if (it.conversationId == updatedConversation.conversationId) updatedConversation else it
        }.toMutableList()
        if (replaced.none { it.conversationId == updatedConversation.conversationId }) {
            replaced.add(updatedConversation)
        }

        saveSessions(replaced)
        saveActiveConversationId(updatedConversation.conversationId)
        return updatedConversation
    }

    fun deleteConversation(conversationId: String): Boolean = synchronized(lock) {
        val sessions = loadSessionsWithMigration().toMutableList()
        val remaining = sessions.filterNot { it.conversationId == conversationId }
        val removed = remaining.size != sessions.size
        if (!removed) return false

        saveSessions(remaining)
        val currentActiveId = prefs.getString(KEY_ACTIVE_CONVERSATION_ID, null)
        if (currentActiveId == conversationId) {
            val nextConversation = remaining.maxByOrNull { it.updatedAtEpochMs }
            if (nextConversation != null) {
                saveActiveConversationId(nextConversation.conversationId)
            } else {
                val newConversation = AssistantConversationSession.create()
                saveSessions(listOf(newConversation))
                saveActiveConversationId(newConversation.conversationId)
            }
        }
        return true
    }

    private fun loadSessionsWithMigration(): List<AssistantConversationSession> {
        val sessionsJson = prefs.getString(KEY_CONVERSATIONS, null)
        if (!sessionsJson.isNullOrBlank()) {
            val type = object : TypeToken<List<AssistantConversationSession>>() {}.type
            val sessions = runCatching { gson.fromJson<List<AssistantConversationSession>>(sessionsJson, type) }
                .getOrNull()
                .orEmpty()
                .map { session ->
                    session.copy(
                        title = buildConversationTitle(session.title, session.messages),
                        messages = session.messages.sortedBy { it.createdAtEpochMs }.takeLast(MAX_MESSAGES_PER_CONVERSATION)
                    )
                }
            saveSessions(sessions)
            return sessions
        }

        val legacyJson = prefs.getString(KEY_LEGACY_MESSAGES, null)
        if (legacyJson.isNullOrBlank()) {
            return emptyList()
        }

        val legacyType = object : TypeToken<List<AssistantConversationMessage>>() {}.type
        val legacyMessages = runCatching { gson.fromJson<List<AssistantConversationMessage>>(legacyJson, legacyType) }
            .getOrNull()
            .orEmpty()
            .sortedBy { it.createdAtEpochMs }
            .takeLast(MAX_MESSAGES_PER_CONVERSATION)

        val migratedSessions = if (legacyMessages.isEmpty()) {
            emptyList()
        } else {
            listOf(
                AssistantConversationSession.create(messages = legacyMessages)
                    .copy(title = buildConversationTitle(DEFAULT_CONVERSATION_TITLE, legacyMessages))
            )
        }

        prefs.edit().remove(KEY_LEGACY_MESSAGES).apply()
        saveSessions(migratedSessions)
        migratedSessions.firstOrNull()?.let { saveActiveConversationId(it.conversationId) }
        return migratedSessions
    }

    private fun saveSessions(conversations: List<AssistantConversationSession>) {
        prefs.edit().putString(KEY_CONVERSATIONS, gson.toJson(conversations)).apply()
    }

    private fun saveActiveConversationId(conversationId: String) {
        prefs.edit().putString(KEY_ACTIVE_CONVERSATION_ID, conversationId).apply()
    }

    private fun buildConversationTitle(
        currentTitle: String,
        messages: List<AssistantConversationMessage>
    ): String {
        val firstUserMessage = messages.firstOrNull { it.isUser }
            ?.text
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()

        if (firstUserMessage.isNotBlank()) {
            return firstUserMessage.take(TITLE_MAX_LENGTH)
        }
        return currentTitle.ifBlank { DEFAULT_CONVERSATION_TITLE }
    }

    companion object {
        private const val PREFS_NAME = "ADS_ASSISTANT_CONVERSATION"
        private const val KEY_CONVERSATIONS = "assistant_conversations"
        private const val KEY_ACTIVE_CONVERSATION_ID = "assistant_active_conversation_id"
        private const val KEY_LEGACY_MESSAGES = "assistant_messages"
        private const val DEFAULT_CONVERSATION_TITLE = "Yeni sohbet"
        private const val TITLE_MAX_LENGTH = 42
        const val MAX_MESSAGES_PER_CONVERSATION = 40

        private val gson = Gson()
        private val lock = Any()
    }
}

data class AssistantConversationSession(
    val conversationId: String,
    val title: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val messages: List<AssistantConversationMessage>
) {
    companion object {
        fun create(
            createdAtEpochMs: Long = System.currentTimeMillis(),
            messages: List<AssistantConversationMessage> = emptyList()
        ): AssistantConversationSession {
            val updatedAt = messages.lastOrNull()?.createdAtEpochMs ?: createdAtEpochMs
            return AssistantConversationSession(
                conversationId = UUID.randomUUID().toString(),
                title = "Yeni sohbet",
                createdAtEpochMs = createdAtEpochMs,
                updatedAtEpochMs = updatedAt,
                messages = messages
            )
        }
    }
}

data class AssistantConversationMessage(
    val messageId: String,
    val text: String,
    val isUser: Boolean,
    val createdAtEpochMs: Long
) {
    companion object {
        fun create(
            text: String,
            isUser: Boolean,
            createdAtEpochMs: Long = System.currentTimeMillis()
        ): AssistantConversationMessage {
            return AssistantConversationMessage(
                messageId = UUID.randomUUID().toString(),
                text = text,
                isUser = isUser,
                createdAtEpochMs = createdAtEpochMs
            )
        }
    }
}
