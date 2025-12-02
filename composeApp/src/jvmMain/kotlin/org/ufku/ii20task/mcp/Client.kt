package org.ufku.ii20task.mcp



import io.github.cdimascio.dotenv.dotenv
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.*
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.security.InvalidParameterException
import kotlin.time.Duration.Companion.seconds


class Client {
    private val mcp: Client = Client(clientInfo = Implementation(name = "mcp-github", version = "1.0.0"))
    private val githubToken = dotenv().get("GITHUB_PAT")


    val json = Json {
        ignoreUnknownKeys = true
    }
    val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                },
            )
        }
        install(SSE)
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.HEADERS
        }
        install(HttpTimeout) {
            // не ограничивать длительность запроса (полезно для stream=true)
            requestTimeoutMillis = 100_000L
            // дать время на TCP/TLS рукопожатие
            connectTimeoutMillis = 30_000
        }
        defaultRequest {
            headers {
                append("Accept", "application/json")
                append(HttpHeaders.Authorization, "Bearer $githubToken")
            }
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun connect() {
        val transport = StreamableHttpClientTransport(
            client = http,
            url = "https://api.githubcopilot.com/mcp/x/all",
            reconnectionTime = 5.seconds, // опционально, авто-reconnect SSE
        ) {

            header(HttpHeaders.Authorization, "Bearer $githubToken")

            header("X-MCP-Readonly", "true")
        }
        mcp.connect(transport)
    }

    suspend fun callList(): List<String> {
        val list = withContext(Dispatchers.IO) {
            mcp.listTools()
        }
        return list.tools.map { "${it.name} - ${it.description} - ${it.inputSchema}" }
    }

    suspend fun getBranches(): List<String> {
        val callResult = withContext(Dispatchers.IO) {
            mcp.callTool(
                name = "list_branches",
                arguments = mapOf(
                    "owner" to "takhir-nafikov",
                    "repo" to "ii12task",
                )
            )
        }

        val callWrap = callResult?.content?.map { (it as? io.modelcontextprotocol.kotlin.sdk.TextContent)?.text ?: "" }

        return callWrap ?: emptyList()
    }

    suspend fun getPullRequestUrl(): String {
        val callResult = withContext(Dispatchers.IO) {
            mcp.callTool(
                name = "pull_request_read",
                arguments = mapOf(
                    "owner" to "takhir-nafikov",
                    "repo" to "ii20task",
                    "pullNumber" to 1,
                    "method" to "get"
                )
            )
        }

        val text = callResult?.content?.map { (it as? io.modelcontextprotocol.kotlin.sdk.TextContent)?.text ?: "" }?.getOrNull(0) ?: throw InvalidParameterException("не нашли ничего")
        val pr = json.decodeFromString<PullRequestDiffOnly>(text)
        return pr.diffUrl
    }

    suspend fun loadDiffText(
        diffUrl: String
    ): String {
        val response: HttpResponse = withContext(Dispatchers.IO) {
            http.get(diffUrl) {
                headers.append("Accept", "text/plain")
//            headers.append("User-Agent", "KtorDiffClient/1.0")
            }
        }

        // если хочешь, можешь добавить проверку кода ответа
        if (!response.status.isSuccess()) {
            error("Failed to download diff: HTTP ${response.status.value}")
        }

        return response.bodyAsText()
    }
}