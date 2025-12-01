package org.ufku.ii20task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.ufku.ii20task.mcp.Client

@Composable
@Preview
fun App() {
    MaterialTheme {
        val client = remember { Client() }
        val ollama = remember { Ollama() }
        val zai = remember { Zai(ollama) }

        // список сообщений (история)
        val messages = remember { mutableStateListOf<ChatMessageUi>() }

        // текст в поле ввода
        var input by remember { mutableStateOf("") }

        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        // начальное подключение и первый запрос, как у тебя
        LaunchedEffect(Unit) {
            client.connect()
            messages += ChatMessageUi("ждем...", isUser = false)
        }

        // автоскролл к последнему сообщению
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.lastIndex)
            }
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // История сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState
            ) {
                items(messages) { msg ->
                    val bg = if (msg.isUser)
                        MaterialTheme.colorScheme.tertiaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer

                    val alignment =
                        if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = if (msg.isUser)
                            Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = bg,
                            shape = MaterialTheme.shapes.medium,
                            tonalElevation = 2.dp,
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Text(
                                text = msg.text,
                                modifier = Modifier.padding(8.dp),
                                textAlign = if (msg.isUser)
                                    TextAlign.End else TextAlign.Start
                            )
                        }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 4.dp))

            // Поле ввода + кнопка "Отправить"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = {
                        Text("Сообщение или команда")
                    },
                    singleLine = true
                )

                Button(
                    onClick = {
                        val trimmed = input.trim()
                        if (trimmed.isEmpty()) return@Button

                        // сразу добавляем пользовательское сообщение в историю
                        messages += ChatMessageUi(trimmed, isUser = true)

                        val action = parseInput(trimmed)

                        // асинхронно дергаем Zai / MCP
                        scope.launch {
                            val replyText = when (action) {
                                is InputAction.Message -> {
                                    zai.invokeRequest(action.text)
                                }

                                is InputAction.Command -> {
                                    handleCommand(action, client, zai)
                                }
                            }

                            messages += ChatMessageUi(replyText, isUser = false)
                        }

                        input = ""
                    }
                ) {
                    Text("Отпр.")
                }
            }
        }
    }
}

/** Что ввёл пользователь: обычный текст или команду с аргументами */
private sealed class InputAction {
    data class Message(val text: String) : InputAction()
    data class Command(val name: String, val args: List<String>) : InputAction()
}

/** Парсер ввода: строки с '\' считаются командами */
private fun parseInput(raw: String): InputAction {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("\\")) {
        return InputAction.Message(trimmed)
    }

    // убираем "\" и режем по пробелам
    val cmd = trimmed
        .drop(1)                      // убрали '\'


    val name = cmd.lowercase()
    return InputAction.Command(name, listOf())
}

/** Обработчик команд — сюда вешаешь всю "магию" */
private suspend fun handleCommand(
    cmd: InputAction.Command,
    client: Client,
    zai: Zai
): String {
    return when (cmd.name) {
        // пример команды: \branch owner repo
        "branch" -> {
            client.getBranches().joinToString("\n\n")
        }

        // можно добавлять свои команды: \help, \status и т.п.
        "help" -> {
            zai.setHelpMode(true)
            """
            Доступные команды:
            \branch         — получить ветки репозитория
            \clear          — выходим из help
            
            Сейчас можно спросить что либо про проект
            """.trimIndent()
        }

        "clear" -> {
            zai.setHelpMode(false)
            "вышли из сумрака(help)"
        }

        else -> "Неизвестная команда \\${cmd.name} с аргументами: ${cmd.args.joinToString(" ")}"
    }
}

/** Модель сообщения в чате */
data class ChatMessageUi(
    val text: String,
    val isUser: Boolean
)