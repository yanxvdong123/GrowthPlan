package com.example.growthplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.growthplan.data.AppDatabase
import com.example.growthplan.data.GrowthRepository
import com.example.growthplan.data.Plan
import com.example.growthplan.data.PlanType
import com.example.growthplan.data.Task
import com.example.growthplan.viewmodel.GrowthViewModel
import com.example.growthplan.viewmodel.GrowthViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = AppDatabase.getDatabase(this)
        val repository = GrowthRepository(database.planDao(), database.taskDao())
        val viewModelFactory = GrowthViewModelFactory(repository)

        setContent {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = Color(0xFF0D47A1),
                    secondary = Color(0xFF1976D2),
                    tertiary = Color(0xFFBBDEFB)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GrowthPlanApp(viewModelFactory)
                }
            }
        }
    }
}

@Composable
fun GrowthPlanApp(viewModelFactory: GrowthViewModelFactory) {
    val navController = rememberNavController()
    val viewModel: GrowthViewModel = viewModel(factory = viewModelFactory)

    NavHost(navController = navController, startDestination = "home") {
        composable("home") { 
            HomeScreen(navController, viewModel) 
        }
        composable(
            route = "plan_detail/{parentId}/{parentName}/{level}",
            arguments = listOf(
                navArgument("parentId") { type = NavType.LongType },
                navArgument("parentName") { type = NavType.StringType },
                navArgument("level") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val parentId = backStackEntry.arguments?.getLong("parentId") ?: 0L
            val parentName = backStackEntry.arguments?.getString("parentName") ?: "Plan"
            val level = backStackEntry.arguments?.getInt("level") ?: 0
            
            PlanHierarchyScreen(
                navController = navController, 
                viewModel = viewModel, 
                parentId = parentId,
                parentName = parentName,
                currentLevelIndex = level
            )
        }
        composable("stats") {
            StatsScreen(navController, viewModel)
        }
        composable("admin") {
            AdminScreen(navController, viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, viewModel: GrowthViewModel) {
    val fiveYearPlans by viewModel.fiveYearPlans.collectAsState()
    val todayTasks by viewModel.todayTasks.collectAsState()
    var showTaskDialog by remember { mutableStateOf(false) }
    var showAdminAuth by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("5-Year Growth Plan") },
                actions = {
                    IconButton(onClick = { navController.navigate("stats") }) {
                        Icon(Icons.Default.BarChart, contentDescription = "Stats")
                    }
                    IconButton(onClick = { showAdminAuth = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Admin")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTaskDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Task")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("My 5-Year Vision", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (fiveYearPlans.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text("No 5-Year plans yet. Go to Admin to set up.", modifier = Modifier.padding(16.dp))
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(fiveYearPlans) { plan ->
                        PlanCard(plan) {
                            navController.navigate("plan_detail/${plan.id}/${plan.title}/1")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text("Today's Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(todayTasks) { task ->
                    TaskItem(task, onToggle = { viewModel.toggleTask(task) })
                }
                if (todayTasks.isEmpty()) {
                    item { Text("No tasks for today!", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) }
                }
            }
        }
    }

    if (showTaskDialog) {
        AddTaskDialog(onDismiss = { showTaskDialog = false }, onConfirm = { viewModel.addTask(it) })
    }
    
    if (showAdminAuth) {
        AdminAuthDialog(
            onDismiss = { showAdminAuth = false },
            onSuccess = { 
                showAdminAuth = false
                navController.navigate("admin") 
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanHierarchyScreen(
    navController: NavController, 
    viewModel: GrowthViewModel, 
    parentId: Long,
    parentName: String,
    currentLevelIndex: Int
) {
    val childPlans by viewModel.getChildPlans(parentId).collectAsState()
    val currentType = PlanType.entries.getOrNull(currentLevelIndex) ?: PlanType.WEEK
    val nextLevelIndex = currentLevelIndex + 1
    val canDrillDown = currentLevelIndex < 4

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(parentName) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Sub-Goals: ${currentType.label}", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            
            if (childPlans.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No plans at this level.", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(childPlans) { plan ->
                        PlanCard(plan) {
                            if (canDrillDown) {
                                navController.navigate("plan_detail/${plan.id}/${plan.title}/$nextLevelIndex")
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(navController: NavController, viewModel: GrowthViewModel) {
    val fiveYearStats by viewModel.getStatsForType(PlanType.FIVE_YEAR).collectAsState(initial = Pair(0,0))
    val yearStats by viewModel.getStatsForType(PlanType.YEAR).collectAsState(initial = Pair(0,0))
    val taskStats by viewModel.taskStats.collectAsState(initial = Pair(0,0))

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Progress Statistics") }, 
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back") } }
            ) 
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            StatItem("5-Year Vision", fiveYearStats.first, fiveYearStats.second)
            StatItem("Annual Goals", yearStats.first, yearStats.second)
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            StatItem("All Time Tasks", taskStats.first, taskStats.second)
        }
    }
}

@Composable
fun StatItem(label: String, completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total.toFloat() else 0f
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("$completed / $total")
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress, 
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(navController: NavController, viewModel: GrowthViewModel) {
    val allPlans by viewModel.allPlans.collectAsState()
    var showAddPlanDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Admin Backdoor") }, 
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.Close, "Close") } }
            ) 
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddPlanDialog = true }) {
                Icon(Icons.Default.Add, "Add Plan")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(allPlans) { plan ->
                ListItem(
                    headlineContent = { Text(plan.title) },
                    supportingContent = { Text("${plan.type.name} ParentID: ${plan.parentId ?: "None"}") },
                    trailingContent = {
                        IconButton(onClick = { viewModel.deletePlan(plan) }) {
                            Icon(Icons.Default.Delete, "Delete", tint = Color.Red)
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }

    if (showAddPlanDialog) {
        AddPlanDialog(
            allPlans = allPlans,
            onDismiss = { showAddPlanDialog = false },
            onConfirm = { title, type, parentId -> 
                viewModel.addPlan(Plan(title = title, type = type, parentId = parentId))
            }
        )
    }
}

@Composable
fun PlanCard(plan: Plan, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(plan.title, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        Text(
            text = task.title,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null
        )
    }
}

@Composable
fun AddTaskDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Daily Task") },
        text = { TextField(value = text, onValueChange = { text = it }) },
        confirmButton = { Button(onClick = { if(text.isNotBlank()) onConfirm(text); onDismiss() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdminAuthDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Access") },
        text = { 
            Column {
                TextField(value = password, onValueChange = { password = it; error = false }, label = { Text("Password") }, isError = error)
                if(error) Text("Wrong (hint: 1234)", color = Color.Red, fontSize = 12.sp)
            }
        },
        confirmButton = { Button(onClick = { if(password == "1234") onSuccess() else error = true }) { Text("Enter") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlanDialog(allPlans: List<Plan>, onDismiss: () -> Unit, onConfirm: (String, PlanType, Long?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PlanType.FIVE_YEAR) }
    var selectedParentId by remember { mutableStateOf<Long?>(null) }
    var typeExpanded by remember { mutableStateOf(false) }
    var parentExpanded by remember { mutableStateOf(false) }
    val potentialParents = allPlans.filter { it.type.level == selectedType.level - 1 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Plan") },
        text = {
            Column {
                TextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
                    OutlinedTextField(value = selectedType.label, onValueChange = {}, readOnly = true, label = { Text("Type") }, modifier = Modifier.menuAnchor())
                    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        PlanType.entries.forEach { type ->
                            DropdownMenuItem(text = { Text(type.label) }, onClick = { selectedType = type; typeExpanded = false; selectedParentId = null })
                        }
                    }
                }
                if (selectedType != PlanType.FIVE_YEAR) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ExposedDropdownMenuBox(expanded = parentExpanded, onExpandedChange = { parentExpanded = it }) {
                        val pName = potentialParents.find { it.id == selectedParentId }?.title ?: "Select Parent"
                        OutlinedTextField(value = pName, onValueChange = {}, readOnly = true, label = { Text("Parent") }, modifier = Modifier.menuAnchor())
                        ExposedDropdownMenu(expanded = parentExpanded, onDismissRequest = { parentExpanded = false }) {
                            potentialParents.forEach { parent ->
                                DropdownMenuItem(text = { Text(parent.title) }, onClick = { selectedParentId = parent.id; parentExpanded = false })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { 
            Button(enabled = title.isNotBlank() && (selectedType == PlanType.FIVE_YEAR || selectedParentId != null),
                onClick = { onConfirm(title, selectedType, selectedParentId); onDismiss() }) { Text("Create") } 
        }
    )
}