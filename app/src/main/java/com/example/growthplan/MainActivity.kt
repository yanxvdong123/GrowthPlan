package com.example.growthplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import com.example.growthplan.data.*
import com.example.growthplan.viewmodel.*
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.launch

// 深色系配色方案
val DarkPrimary = Color(0xFF1A1A2E)
val DarkSecondary = Color(0xFF16213E)
val AccentColor = Color(0xFFE94560)
val TextWhite = Color(0xFFE2E2E2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = AppDatabase.getDatabase(this)
        val repository = GrowthRepository(database.planDao(), database.taskDao())
        val viewModelFactory = GrowthViewModelFactory(repository)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = AccentColor,
                    background = DarkPrimary,
                    surface = DarkSecondary,
                    onBackground = TextWhite,
                    onSurface = TextWhite
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GrowthPlanApp(viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun GrowthPlanApp(factory: GrowthViewModelFactory) {
    val navController = rememberNavController()
    val viewModel: GrowthViewModel = viewModel(factory = factory)

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, viewModel) }
        composable("admin") { AdminScreen(navController, viewModel) }
        composable("stats") { StatsScreen(navController, viewModel) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: GrowthViewModel) {
    val fiveYearPlans by viewModel.fiveYearPlans.collectAsState()
    val todayTasks by viewModel.todayTasks.collectAsState()
    val scope = rememberCoroutineScope()
    var showAiDialog by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf("输入你的计划，AI 教练帮你拆解并量化指标...") }
    var isAnalyzing by remember { mutableStateOf(false) }

    // 初始化 Gemini
    val generativeModel = remember {
        GenerativeModel(modelName = "gemini-1.5-flash", apiKey = "AIzaSyDj3EOVECWv2E3PRjOZ6v0GMCmZrIzeqmM")
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("GROWTH HUB", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                actions = {
                    IconButton(onClick = { navController.navigate("stats") }) { Icon(Icons.Default.AutoGraph, null) }
                    IconButton(onClick = { navController.navigate("admin") }) { Icon(Icons.Default.Settings, null) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAiDialog = true },
                containerColor = AccentColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, "AI Coach")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // 愿景概览卡片
            DashboardCard(title = "5-Year Vision", count = fiveYearPlans.size)
            
            Spacer(modifier = Modifier.height(24.dp))
            Text("TODAY'S MISSIONS", fontWeight = FontWeight.Bold, color = AccentColor)
            Spacer(modifier = Modifier.height(8.dp))

            if (todayTasks.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No missions. Click AI Coach to start.", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(todayTasks) { task ->
                        MissionItem(task) { viewModel.toggleTask(task) }
                    }
                }
            }
        }
    }

    // AI 教练对话框
    if (showAiDialog) {
        var userInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            containerColor = DarkSecondary,
            title = { Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = AccentColor)
                Spacer(Modifier.width(8.dp))
                Text("AI 成长教练", color = TextWhite)
            }},
            text = {
                Column {
                    if (isAnalyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(aiResponse, color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text("例如：我下个月想瘦3斤") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        isAnalyzing = true
                        aiResponse = "正在深度思考..."
                        val result = generativeModel.generateContent("你是一个严厉的成长教练。用户说：$userInput。请分析这个目标，给出1句毒舌点评，并将其拆解为3个可量化的日任务指标。").text
                        aiResponse = result ?: "大脑短路了..."
                        isAnalyzing = false
                    }
                }) { Text("分析拆解") }
            },
            dismissButton = { TextButton(onClick = { showAiDialog = false }) { Text("关闭") } }
        )
    }
}

@Composable
fun DashboardCard(title: String, count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(AccentColor, Color(0xFF950740))))
            .padding(20.dp)
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
            Text("$count ACTIVE", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun MissionItem(task: Task, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSecondary)
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (task.isCompleted) AccentColor else Color.Gray
        Icon(
            if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color
        )
        Spacer(Modifier.width(12.dp))
        Text(
            task.title,
            color = if (task.isCompleted) Color.Gray else TextWhite,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
        )
    }
}

// ... 辅助界面 (Stats, Admin 等逻辑保持极简) ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController, viewModel: GrowthViewModel) {
    Scaffold(topBar = { TopAppBar(title = {Text("DATA CENTER")}) }) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text("你的成长曲线正在上升...", color = AccentColor)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(navController: NavController, viewModel: GrowthViewModel) {
    val allPlans by viewModel.allPlans.collectAsState()
    Scaffold(topBar = { TopAppBar(title = {Text("DATABASE ADMIN")}) }) { p ->
        LazyColumn(Modifier.padding(p)) {
            items(allPlans) { plan ->
                ListItem(headlineContent = { Text(plan.title) }, trailingContent = {
                    IconButton(onClick = { viewModel.deletePlan(plan) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                })
            }
        }
    }
}