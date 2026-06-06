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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
//import androidx.compose.material.icons.automirrored.filled.VolumeUp
//import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.media.MediaRecorder
import android.os.Build
import java.io.File


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // 1. 获取 Context (如果之前没有定义，请加上)
                val context = LocalContext.current

                // 2. 定义权限申请的回调处理
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                )
                { isGranted ->
                    if (isGranted) {
                        // 用户同意了权限
                        Toast.makeText(context, "麦克风权限已获取", Toast.LENGTH_SHORT).show()
                        // TODO: 这里可以调用开始录音的逻辑
                    } else {
                        // 用户拒绝了权限
                        Toast.makeText(context, "需要麦克风权限才能使用此功能", Toast.LENGTH_SHORT).show()
                    }
                }
                // 1. 定义录音相关的状态变量和临时文件
                var isRecording by remember { mutableStateOf(false) }
                var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
                // 使用应用的缓存目录存放临时录音文件
                val tempFile = remember { File(context.cacheDir, "voice_input_temp.m4a") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        var displayText by remember { mutableStateOf("") }
                        var inputText by remember { mutableStateOf("") }

                        // 上方的大文本展示框，使用 Box 包裹 Text，形成滑动窗口
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()       // 宽度占满
                                .weight(1f)           // 高度占据剩余所有空间
                                .padding(8.dp)        // 整体留白
                                .verticalScroll(rememberScrollState()), // 2. 开启垂直滚动
                            contentAlignment = Alignment.TopStart // 3. 让文字默认从左上角开始显示
                        )
                        {
                            // 实际的文本组件
                            Text(
                                text = displayText.ifEmpty { "点击按钮将显示在此处" },
                                modifier = Modifier.padding(16.dp) // 文本内部留一点距离，防止文字贴着屏幕边缘
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        // 中间的文本输入框，使用row包裹
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically // 垂直居中
                        )
                        {
                            // 左侧的输入框
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                label = { Text("请输入文本") },
                                modifier = Modifier
                                    .weight(1f) // 关键：设置权重为 1，让输入框尽可能变宽，把按钮挤到右边
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // 右侧的按钮（此时不实现功能）
                            IconButton(onClick = {
                                // 检查权限
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    if (!isRecording) {
                                        // --- 开始录音逻辑 ---
                                        try {
                                            // 兼容不同 Android 版本的构造函数
                                            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                MediaRecorder(context)
                                            } else {
                                                @Suppress("DEPRECATION")
                                                MediaRecorder()
                                            }

                                            // 配置录音参数
                                            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                                            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            newRecorder.setOutputFile(tempFile.absolutePath)

                                            newRecorder.prepare()
                                            newRecorder.start()

                                            recorder = newRecorder
                                            isRecording = true
                                            Toast.makeText(context, "开始录音...", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "录音出错: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // --- 停止录音逻辑 ---
                                        recorder?.apply {
                                            try {
                                                stop()
                                                release()
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        recorder = null
                                        isRecording = false
                                        Toast.makeText(context, "录音已保存: ${tempFile.absolutePath}", Toast.LENGTH_LONG).show()

                                    }
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "发送到千问" // 辅助描述
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 按钮：将输入框的内容追加到上方大文本框
                        Button(
                            onClick = {
                                if (inputText.isNotEmpty()) {
                                    displayText += "[用户]${inputText}\n"

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
     * 问题：what is the weather today
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
                    // 1. 插入系统级约束（System Role）
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", """
                            你是一位亲切、有耐心的英语口语陪练老师。
                            当前场景：情景口语自由练习（如：咖啡厅点餐、机场值机、日常闲聊等）。
                            
                            请严格遵守以下约束：
                            1. 你的回复必须简短、地道、口语化，字数控制在2-3句话以内，绝对不要回复长篇大论。
                            2. 每次回复的结尾，请抛出一个自然、相关的小问题，引导我继续说下去。
                            3. 如果我的表达有明显的严重语法错误，请在回复的最开头用括号简短纠正一下（例如：[纠正: 应为 "I went to..." 而不是 "I go to yesterday"]），然后再用英文继续对话。如果没有错误则不用纠正。
                            4. 全程请用英文与我对话（除非纠正语法时可以用中文说明）。
                        """.trimIndent())
                    })

                    // 2. 插入用户的实际输入
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt) // 这里的 prompt 就是用户在手机上输入的聊天内容
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
