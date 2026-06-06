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
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam
import com.alibaba.dashscope.common.MultiModalMessage
import com.alibaba.dashscope.common.Role
import com.alibaba.dashscope.utils.Constants
import android.content.SharedPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.core.content.edit

class ApiKeyManager(context: Context) {
    // 创建一个名为 "app_config" 的本地存储文件
    private val prefs: SharedPreferences = context.getSharedPreferences("app_config", Context.MODE_PRIVATE)
    companion object {
        private const val KEY_API_KEY = "dashscope_api_key"
    }
    /**
     * 保存 API Key 到本地
     */
    fun saveApiKey(apiKey: String) {
        prefs.edit { putString(KEY_API_KEY, apiKey) }
    }
    /**
     * 从本地读取 API Key，如果找不到则返回空字符串 ""
     */
    fun getApiKey(): String {
        return prefs.getString(KEY_API_KEY, "") ?: ""
    }
    /**
     * 判断本地是否已经配置过 API Key
     */
    fun hasApiKey(): Boolean {
        return getApiKey().isNotEmpty()
    }
}

class MainActivity : ComponentActivity() {
    @SuppressLint("DefaultLocale")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // 1. 获取 Context (如果之前没有定义，请加上)
                val context = LocalContext.current
                val apiKeyManager = remember { ApiKeyManager(context) }
                var showKeyDialog by remember { mutableStateOf(false) }

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

                        // 中间的麦克风调用按钮
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

                                    // 录音结束后，在协程中调用 ASR
                                    lifecycleScope.launch {
                                        try {
                                            displayText += "[系统] 正在识别语音...\n"
                                            // 从本地存储管理器中，动态捞出记住的密钥
                                            val currentKey = apiKeyManager.getApiKey()
                                            val recognizedText = transcribeAudioWithSdk(tempFile, currentKey)

                                            if (recognizedText.isNotEmpty()) {
                                                displayText += "[语音] $recognizedText\n"
                                            }
                                        } catch (e: Exception) {
                                            displayText += "[错误] 语音识别失败: ${e.message}\n"
                                        }
                                    }

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

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // 按钮：将输入框的内容追加到上方大文本框
                        Button(
                            onClick = {
                                showKeyDialog=true
                            }
                        )
                        {
                            Text(text = "管理我的 API Key")
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        EditApiKeyDialog(
                            showDialog = showKeyDialog,
                            onDismissRequest = { showKeyDialog = false }, // 当用户在弹窗里点“取消”或点外面时，关闭弹窗
                            apiKeyManager = apiKeyManager,
                            onKeySaved = { updatedKey ->
                                // 当用户在弹窗里点了保存，这里会收到最新存储的真实 key 字符串
                                // 如果你想在主界面感知到变化，可以在这里更新你的其他界面状态
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun EditApiKeyDialog(
        showDialog: Boolean,
        onDismissRequest: () -> Unit,
        apiKeyManager: ApiKeyManager,
        onKeySaved: (String) -> Unit
    ) {
        if (!showDialog) return // 如果开关是 false，直接不渲染任何 UI

        val context = LocalContext.current

        // 1. 记住输入框里的文本，初始值直接去本地捞现有的 Key
        var inputKey by remember { mutableStateOf(apiKeyManager.getApiKey()) }

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(text = "配置 / 修改 API Key") },
            text = {
                Column {
                    Text(
                        text = "目前保存的 Key：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant

                    )
                    // 如果本地有旧的 Key，脱敏显示一下，没有就显示“无”
                    val currentLocalKey = apiKeyManager.getApiKey()
                    Text(
                        text = if (currentLocalKey.isEmpty()) "（当前未设置）" else "${currentLocalKey.take(4)}****${currentLocalKey.takeLast(4)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. 供用户编辑的文本输入框
                    OutlinedTextField(
                        value = inputKey,
                        onValueChange = { inputKey = it },
                        label = { Text("在此输入或粘贴新的 sk-...") },
                        placeholder = { Text("sk-...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmedKey = inputKey.trim()
                        if (trimmedKey.isNotEmpty()) {
                            // 3. 点击保存后更新本地持久化（推荐的 KTX 内部写好了）
                            apiKeyManager.saveApiKey(trimmedKey)
                            onKeySaved(trimmedKey) // 通知外部
                            onDismissRequest() // 关闭弹窗
                            Toast.makeText(context, "更新成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Key 不能为空哦", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("保存更新")
                }
            },
            dismissButton = {
                Row {
                    // 如果原本就有 Key，允许用户一键清除
                    if (apiKeyManager.hasApiKey()) {
                        TextButton(
                            onClick = {
                                apiKeyManager.saveApiKey("") // 传入空代表清除
                                onKeySaved("")
                                onDismissRequest()
                                Toast.makeText(context, "已清除配置", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("清除配置", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text("取消")
                    }
                }
            }
        )
    }

    /**
     * 将录音文件发送给千问 ASR 模型进行语音转文字
     */
    private suspend fun transcribeAudioWithSdk(audioFile: File, currentKey: String): String = withContext(Dispatchers.IO) {
        // 1. 初始化基础 API 路径（北京地域固定要求）
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1"

        try {
            // 2. 将本地 File 转化为百炼 SDK 规范的本地 File URL
            val localFilePath = "file://${audioFile.absolutePath}"

            // 3. 核心修改：明确指定音频格式为 m4a（兼容你之前定义的临时文件）
            // 如果你未来换回 mp3，这里改为 "audio/mp3" 即可
            val audioContent = mapOf(
                "audio" to localFilePath,
                "mimetype" to "audio/m4a"
            )

            val userMessage = MultiModalMessage.builder()
                .role(Role.USER.value)
                .content(listOf(audioContent))
                .build()

            // 4. 配置语音识别的可选参数
            val asrOptions = hashMapOf<String, Any>(
                "enable_itn" to true // 💡 建议改为 true：自动将“一二三”转为“123”，更符合阅读习惯
            )

            // 5. 组装请求参数
            val param = MultiModalConversationParam.builder()
                .apiKey(currentKey)
                .model("qwen3-asr-flash") // 使用闪速语音大模型
                .message(userMessage)
                .parameter("asr_options", asrOptions)
                .build()

            // 6. 发起同步调用（在 Dispatchers.IO 中执行，保证不卡死 Android 界面）
            val conv = MultiModalConversation()
            val result = conv.call(param)

            // ======= 🛠️ 把第 7 步和第 8 步修改为以下安全解析方式 =======

            // 1. 安全获取 choices 列表
            val choices = result.output?.choices
            if (!choices.isNullOrEmpty()) {
                // 2. 拿到第一条回复
                val firstChoice = choices[0]
                val message = firstChoice.message

                // 3. qwen3-asr-flash 的文本可能存在两个地方，我们做个双保险读取：
                // 先尝试读取多模态的 content 列表，如果为空，直接读取普通的 content 字符串
                val contents = message.content as? List<*>
                var textResult = ""

                if (!contents.isNullOrEmpty()) {
                    // 如果是标准的多模态 List 结构
                    val firstContent = contents[0] as? Map<*, *>
                    textResult = firstContent?.get("text")?.toString() ?: ""
                }

                if (textResult.isEmpty()) {
                    // 如果上面没取到，直接尝试拿普通的文本字段
                    textResult = message.content?.toString() ?: ""
                }

                return@withContext textResult.trim().ifEmpty { "语音识别成功，但未检测到说话内容" }
            }

            return@withContext "大模型未返回有效数据"

        } catch (e: Exception) {
            // 向上抛出异常，让外层的 try-catch 捕获并显示在界面上
            throw Exception("语音转文字失败: ${e.message}", e)
        }
    }

}
