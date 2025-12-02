package org.ufku.ii20task

import ai.z.openapi.ZaiClient
import ai.z.openapi.service.model.*
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit


class Zai(
    private val ollama: Ollama
) {
    private val apiKey = dotenv().get("API_KEY")

    private var isHelpMode = false

    private val zaiClient: ZaiClient = ZaiClient.builder()
        .apiKey(apiKey)
        .baseUrl("https://api.z.ai/api/coding/paas/v4/")
        .enableTokenCache()
        .tokenExpire(3600000) // 1 hour
        .connectionPool(10, 5, TimeUnit.MINUTES)
        .build()



    fun setHelpMode(isHelpMode: Boolean) {
        this.isHelpMode = isHelpMode
    }

    /**
     * Собираем ChatCompletion-запрос.
     * Обрати внимание, что первым идёт systemMessage.
     */
    private fun createUserRequest(userMessage: ChatMessage): ChatCompletionCreateParams {
        return ChatCompletionCreateParams.builder()
            .model("glm-4.6")
            .messages(listOf(systemMessage, userMessage))
            .temperature(0.2f)
            .maxTokens(16384)
            .thinking(
                ChatThinking.builder()
                    .type(ChatThinkingType.DISABLED.value())
                    .build()
            )
            .build()
    }


    /**
     * Основной метод: отправка запроса в LLM.
     *
     * Если isHelpMode = true:
     *  - строим embedding через Ollama.embed
     *  - читаем топ-K чанков через Ollama.readTopKChunksFromJson
     *  - добавляем их как контекст в запрос.
     */
    suspend fun invokeRequest(userText: String): String {
        // формируем user-message (с учётом helpMode)
        val userMessage = createUserMessage(userText)

        val request = createUserRequest(userMessage)

        val response = withContext(Dispatchers.IO) {
            zaiClient.chat().createChatCompletion(request)
        }

        if (!response.isSuccess()) {
            throw RuntimeException("Z.ai error: ${response.msg}")
        }

        val data = response.data ?: return "Пустой ответ от модели"

        // Подстрой под реальную структуру ответа: здесь общий паттерн
        val firstChoice = data.choices?.firstOrNull()
            ?: return "Нет choices в ответе"

        val assistantMessage = firstChoice.message
            ?: return "Нет message в первом choice"

        // в SDK контент может быть либо строкой, либо списком частей;
        // упрощённый вариант:
        return assistantMessage.content?.toString() ?: "Пустой content в ответе"
    }

    suspend fun invokeReviewRequest(diff: String): String {
        val reviewMessage = createReviewMessage(diff)

        val request = createUserRequest(reviewMessage)

        val response = withContext(Dispatchers.IO) {
            zaiClient.chat().createChatCompletion(request)
        }

        if (!response.isSuccess()) {
            throw RuntimeException("Z.ai error: ${response.msg}")
        }

        val data = response.data ?: return "Пустой ответ от модели"

        // Подстрой под реальную структуру ответа: здесь общий паттерн
        val firstChoice = data.choices?.firstOrNull()
            ?: return "Нет choices в ответе"

        val assistantMessage = firstChoice.message
            ?: return "Нет message в первом choice"

        // в SDK контент может быть либо строкой, либо списком частей;
        // упрощённый вариант:
        return assistantMessage.content?.toString() ?: "Пустой content в ответе"
    }

    private val systemMessage = ChatMessage.builder()
        .role(ChatMessageRole.SYSTEM.value())
        .content("Ты опытный разработчик и даешь советы новичкам")
        .build()

    /**
     * Создаёт сообщение пользователя.
     * Если isHelpMode = true, то добавляет RAG-контекст перед вопросом.
     */
    private suspend fun createUserMessage(userText: String): ChatMessage {
        return if (isHelpMode) {
            val context = buildContextFromOllama(userText)

            val promptWithContext = buildString {
                appendLine("Ниже приведён контекст из базы знаний (может быть частичным):")
                appendLine()
                appendLine(context)
                appendLine()
                appendLine("На основе этого контекста ответь на вопрос пользователя.")
                appendLine("Если контекст не даёт достаточно информации, честно скажи об этом.")
                appendLine()
                appendLine("Вопрос пользователя:")
                appendLine(userText)
            }

            ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(promptWithContext)
                .build()
        } else {
            // обычный режим — просто вопрос
            ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(userText)
                .build()
        }
    }

    private suspend fun createReviewMessage(
        diff: String,
    ): ChatMessage {
        val context = ollama.readChunksFromFolder()

        val contextString = context.joinToString("\n\n") { chunk ->
            buildString {
                if (chunk.fileName.isNotBlank()) {
                    appendLine("Файл: ${chunk.fileName}")
                }
                appendLine(chunk.chunk)
            }
        }

        val prompt = buildString {
            appendLine("Проанализируй изменения из pull request:")
            appendLine()
            appendLine("=== BEGIN DIFF ===")
            appendLine(diff)
            appendLine("=== END DIFF ===")
            appendLine()

            appendLine("Вот информация о проекте:")
            appendLine(contextString)
            appendLine()

            appendLine("На основе diff ответь:")
            appendLine("1. Соответствуют ли изменения направлению развития проекта?")
            appendLine("2. Не противоречат ли они будущим фичам?")
            appendLine("3. Есть ли риски, которые стоит учесть?")
            appendLine("4. Рекомендуешь ли принять эти изменения?")
        }

        return ChatMessage.builder()
            .role(ChatMessageRole.USER.value())
            .content(prompt)
            .build()
    }

    /**
     * Строим контекст на основе Ollama:
     *  1) embed запроса
     *  2) берём топ-K чанков
     *  3) склеиваем в один текст для промпта
     */
    private suspend fun buildContextFromOllama(
        userText: String,
    ): String {
        // 1. embedding для запроса
        val queryEmbedding = ollama.embed(userText)

        // 2. топ-K чанков
        val chunks: List<Chunk> =
            ollama.readTopKChunksFromJson(queryEmbedding.embedding)

        if (chunks.isEmpty()) {
            return "Контекст не найден (по запросу не нашлось подходящих чанков)."
        }

        // 3. преобразуем чанки в удобочитаемый текст
        return chunks.joinToString("\n\n") { chunk ->
            buildString {
                if (chunk.fileName.isNotBlank()) {
                    appendLine("Файл: ${chunk.fileName}")
                }
                appendLine(chunk.text)
            }
        }
    }
}
