package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.ui.theme.MyApplicationTheme

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

                        // 上方的大文本展示框（只显示内容，不可编辑）
                        Text(
                            text = displayText.ifEmpty { "点击按钮将显示在此处" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(8.dp),
                            // 可以根据需要调整样式
                        )
                        
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
                                    displayText = if (displayText.isEmpty()) {
                                        inputText
                                    } else {
                                        "$displayText\n$inputText"
                                    }
                                }
                            }
                        ) {
                            Text(text = "添加到上方")
                        }

                    }
                }
            }
        }
    }
}
