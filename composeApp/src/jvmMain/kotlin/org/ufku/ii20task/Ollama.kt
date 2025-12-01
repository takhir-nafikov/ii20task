package org.ufku.ii20task

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.sqrt


class Ollama(): AutoCloseable {
    val httpClient = HttpClient(CIO) {
        defaultRequest {
            headers {
                append("Accept", "application/json")
            }
            contentType(ContentType.Application.Json)
        }
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    encodeDefaults = true
                },
            )
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun embed(chunkText: String): Chunk = withContext(Dispatchers.IO) {
        val response: OllamaEmbeddingsResponse = httpClient.post("http://localhost:11434/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbeddingsRequest(prompt = chunkText))
        }.body()
        val em = response.embedding.map { it.toFloat() }.toFloatArray()
        Chunk(chunkText, em)
    }

    suspend fun embed(fileChunk: FileChunk): Chunk = withContext(Dispatchers.IO) {
        val response: OllamaEmbeddingsResponse = httpClient.post("http://localhost:11434/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(OllamaEmbeddingsRequest(prompt = fileChunk.chunk))
        }.body()
        val em = response.embedding.map { it.toFloat() }.toFloatArray()
        Chunk(fileChunk.chunk, em, fileChunk.fileName)
    }

    override fun close() {
        httpClient.close()
    }

    suspend fun writeEmbeddingToJsonFile(
        chunk: Chunk
    ) = withContext(Dispatchers.IO) {
        val file = File("F:\\Repos\\ii20task\\docs\\embed.json")
        // Если файл не существует — создаём его с пустым JSON-массивом
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.writeText("[]")
        }

        // Загружаем уже существующие данные
        val existingList = json.decodeFromString<MutableList<Chunk>>(file.readText())

        val ne = normalizeTo01(chunk.embedding.toList()).toFloatArray()

        // Добавляем новое значение
        existingList.add(chunk.copy(embedding = ne))

        // Сохраняем обратно
        file.writeText(json.encodeToString(existingList))
    }

    data class FileChunk(
        val fileName: String,
        val chunk: String
    )

    suspend fun readChunksFromFolder(): List<FileChunk> = coroutineScope {
        val dir = File("F:\\Repos\\ii20task\\docs")

        require(dir.exists() && dir.isDirectory) { "Directory does not exist: docs" }

        // Находим все .md файлы
        val mdFiles = dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("md", ignoreCase = true) }
            ?: emptyList()

        val chunkSize = 250
        val overlap = 50

        // Асинхронно читаем КАЖДЫЙ файл и режем его на чанки отдельно
        val deferredChunksPerFile = mdFiles.map { file ->
            async(Dispatchers.IO) {
                val text = file.readText()

                val chunks = mutableListOf<FileChunk>()
                var start = 0

                while (start < text.length) {
                    val end = (start + chunkSize).coerceAtMost(text.length)

                    val core = text.substring(start, end)

                    val prefixStart = (start - overlap).coerceAtLeast(0)
                    val prefix = text.substring(prefixStart, start)

                    val suffixEnd = (end + overlap).coerceAtMost(text.length)
                    val suffix = text.substring(end, suffixEnd)

                    val chunkWithContext = prefix + core + suffix

                    chunks.add(
                        FileChunk(
                            fileName = file.name,
                            chunk = chunkWithContext
                        )
                    )

                    start += chunkSize
                }

                chunks
            }
        }

        // Собираем чанки со всех файлов в один список
        deferredChunksPerFile.awaitAll().flatten()
    }

    fun normalizeTo01(embedding: List<Float>): List<Float> {
        if (embedding.isEmpty()) {
            return emptyList()
        }

        val dimension = embedding.size
        return embedding.map { it / dimension }
    }

    suspend fun readTopKChunksFromJson(
        queryEmbedding: FloatArray,
        needSort: Boolean = true,
        k: Int = 10
    ): List<Chunk> = withContext(Dispatchers.IO) {
        val file = File("F:\\Repos\\ii20task\\docs\\embed.json")
        if (!file.exists()) {
            return@withContext emptyList()
        }

        // Читаем все сохранённые чанки
        val chunks = json.decodeFromString<List<Chunk>>(file.readText())

        if (chunks.isEmpty()) {
            return@withContext emptyList()
        }

        // Нормализуем embedding запроса так же, как и при записи
        val normalizedQuery = normalizeTo01(queryEmbedding.toList())

        // Считаем сходство с каждым чанкoм
        val scored = chunks.map { chunk ->
            val score = calculateSimilarity(normalizedQuery, chunk.embedding.toList())
            chunk to score
        }

        val result = if (needSort) {
            // сортируем по убыванию сходства
            scored.sortedByDescending { it.second }
        } else {
            // НЕ сортируем, оставляем исходный порядок
            scored
        }

        // Берём top-k после выбора стратегии сортировки
        result
            .take(k)
            .map { it.first }
    }


    suspend fun readFromFile(): String = coroutineScope {
        val dir = File("F:\\Repos\\ii20task\\docs")

        require(dir.exists() && dir.isDirectory) { "Directory does not exist: markdown_files" }

        val mdFiles = mutableListOf<File>()

        // Обычный цикл для поиска .md файлов
        for (file in dir.listFiles() ?: emptyArray()) {
            if (file.isFile && file.extension.lowercase() == "md") {
                mdFiles.add(file)
            }
        }

        // Асинхронное чтение каждого файла
        val deferredContents = mdFiles.map { file ->
            async(Dispatchers.IO) {
                file.readText()
            }
        }

        // Ждём все чтения и объединяем строки
        deferredContents.awaitAll().joinToString("\n")
    }

    fun calculateSimilarity(
        vector1: List<Float>,
        vector2: List<Float>
    ): Float {
        require(vector1.size == vector2.size) {
            "Vectors must have the same size. Got ${vector1.size} and ${vector2.size}"
        }

        if (vector1.isEmpty()) {
            return 0f
        }

        // Скалярное произведение (dot product)
        val dotProduct = vector1.zip(vector2).sumOf { (a, b) ->
            a.toDouble() * b.toDouble()
        }

        // Длины векторов (L2 норма)
        val magnitude1 = sqrt(vector1.sumOf { it.toDouble() * it.toDouble() })
        val magnitude2 = sqrt(vector2.sumOf { it.toDouble() * it.toDouble() })

        // Если один из векторов нулевой, возвращаем 0
        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0f
        }

        // Косинусное сходство
        return (dotProduct / (magnitude1 * magnitude2)).toFloat()
    }
}


@Serializable
data class OllamaEmbeddingsRequest(
    @SerialName("model")
    val model: String = "nomic-embed-text",
    @SerialName("prompt")
    val prompt: String
)

@Serializable
data class OllamaEmbeddingsResponse(
    val embedding: List<Double>
)

@Serializable
data class Chunk(
    val text: String,         // сам текст чанка
    val embedding: FloatArray, // вектор-embedding
    val fileName: String = ""
)