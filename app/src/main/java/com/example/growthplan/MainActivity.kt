package com.example.growthplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.example.growthplan.data.*
import com.example.growthplan.viewmodel.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(this)
        val repo = GrowthRepository(db.planDao(), db.taskDao())
        val factory = GrowthViewModelFactory(repo)

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val vm: GrowthViewModel = viewModel(factory = factory)
                NavHost(navController, startDestination = "home") {
                    composable("home") { HomeScreen(navController, vm) }
                    composable("admin") { AdminScreen(navController, vm) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav, vm: GrowthViewModel) {
    val plans by vm.fiveYearPlans.collectAsState()
    val tasks by vm.todayTasks.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("5-Year Plan") }, actions = {
            IconButton(onClick = { nav.navigate("admin") }) { Icon(Icons.Default.Settings, null) }
        })},
        floatingActionButton = { FloatingActionButton(onClick = { vm.addTask("New Task") }) { Icon(Icons.Default.Add, null) } }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp)) {
            Text("Vision", style = MaterialTheme.typography.headlineMedium)
            LazyColumn(Modifier.height(200.dp)) { items(plans) { Text(it.title, Modifier.padding(8.dp)) } }
            Divider()
            Text("Today's Tasks", style = MaterialTheme.typography.headlineMedium)
            LazyColumn { items(tasks) { task ->
                Row(Modifier.fillMaxWidth()) {
                    Checkbox(task.isCompleted, { vm.toggleTask(task) })
                    Text(task.title, Modifier.padding(12.dp))
                }
            } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(nav, vm: GrowthViewModel) {
    val allPlans by vm.allPlans.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Admin") }) }) { p ->
        LazyColumn(Modifier.padding(p)) {
            items(allPlans) { plan ->
                ListItem(headlineContent = { Text(plan.title) }, trailingContent = {
                    IconButton(onClick = { vm.deletePlan(plan) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                })
            }
            item { Button(onClick = { vm.addPlan(Plan(title = "New 5-Year Goal", type = PlanType.FIVE_YEAR)) }) { Text("Add 5-Year Goal") } }
        }
    }
}