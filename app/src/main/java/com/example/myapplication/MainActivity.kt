package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        // 状态变量1：记录上方大文本框中显示的内容
                        var displayText by remember { mutableStateOf("") }
                        // 状态变量2：记录当前输入框中的文本
                        var inputText by remember { mutableStateOf("") }

                        // 上方的大文本展示框
                        // 使用 Box 包裹 Text，形成滑动窗口
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()       // 宽度占满
                                .weight(1f)           // 高度占据剩余所有空间
                                .padding(8.dp)        // 整体留白
                                .verticalScroll(rememberScrollState()), // 2. 开启垂直滚动
                            contentAlignment = Alignment.TopStart // 3. 让文字默认从左上角开始显示
                        ) {
                            // 实际的文本组件
                            Text(
                                text = displayText.ifEmpty { "点击按钮将显示在此处" },
                                modifier = Modifier.padding(16.dp) // 文本内部留一点距离，防止文字贴着屏幕边缘
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        // 中间的文本输入框
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = { Text("请输入文本") },
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 按钮：将输入框的内容追加到上方大文本框
                        Button(
                            onClick = {
                                if (inputText.isNotEmpty()) {
                                    displayText += "${inputText}\n"

                                    // 2. 开启一个协程处理网络请求 (防止界面卡顿)
                                    lifecycleScope.launch {
                                        try {
                                            // 调用我们下面编写的发往千问模型的函数
                                            val qwenReply = askQwenModel(inputText)
                                            // 当模型回复回来后，将结果追加到屏幕上方
                                            displayText += "[AI 回复]: ${qwenReply}\n"
                                        } catch (e: Exception) {
                                            displayText += "出错了: ${e.message}\n"
                                        }
                                    }
                                }
                                inputText=""
                            }
                        )
                        {
                            Text(text = "添加到上方")
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    /**
     * 这是一个用于发起网络请求的辅助函数
     * api码："sk-3789f764667445d0948ed70bee3da170"
     * 问题：你是什么模型
     */
    private suspend fun askQwenModel(prompt: String): String = withContext(Dispatchers.IO) {
        // 1. 替换为你自己在阿里云百炼申请的 API Key
        val apiKey = "sk-3789f764667445d0948ed70bee3da170"
        val urlString = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

        var connection: HttpURLConnection? = null

        try {
            // 2. 构造 JSON 请求体 (使用 Android 自带的 org.json)
            val jsonBody = JSONObject().apply {
                put("model", "qwen-turbo") // 可更换为 "qwen-max" 等其他模型

                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messagesArray)
            }

            // 3. 初始化网络连接
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "POST"
                connectTimeout = 30000 // 30秒连接超时
                readTimeout = 30000    // 30秒读取超时
                doOutput = true        // 允许发送请求体
                doInput = true         // 允许接收响应体

                // 设置请求头
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            // 4. 写入请求数据
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            // 5. 获取响应状态码并读取结果
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 请求成功，读取返回的 JSON 字符串
                val responseString = BufferedReader(InputStreamReader(connection.inputStream, "UTF-8")).use { reader ->
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    response.toString()
                }

                // 6. 解析模型返回的 JSON
                val jsonResponse = JSONObject(responseString)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val messageObj = firstChoice.optJSONObject("message")
                    return@withContext messageObj?.optString("content") ?: "未解析到文本内容"
                } else {
                    return@withContext "模型未返回有效 choices"
                }
            } else {
                // 请求失败，读取错误流信息
                val errorString = BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }
                throw Exception("HTTP 错误码: $responseCode, 详情: $errorString")
            }

        } catch (e: Exception) {
            // 将异常继续向上抛出，触发你外层的 try-catch，从而把错误显示在 displayText 上
            throw Exception("网络请求失败: ${e.message}", e)
        } finally {
            // 无论成功失败，关闭连接释放资源
            connection?.disconnect()
        }
    }

}
