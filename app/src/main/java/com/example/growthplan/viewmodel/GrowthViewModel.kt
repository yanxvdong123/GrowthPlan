package com.example.growthplan.viewmodel

import androidx.lifecycle.*
import com.example.growthplan.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GrowthViewModel(private val repository: GrowthRepository) : ViewModel() {
    val fiveYearPlans = repository.getPlansByType(PlanType.FIVE_YEAR).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allPlans = repository.getAllPlans().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todayTasks = repository.getTasksForToday().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    fun getChildPlans(parentId: Long) = repository.getChildPlans(parentId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun addPlan(plan: Plan) = viewModelScope.launch { repository.addPlan(plan) }
    fun deletePlan(plan: Plan) = viewModelScope.launch { repository.deletePlan(plan) }
    fun addTask(title: String) = viewModelScope.launch { repository.addTask(Task(title = title)) }
    fun toggleTask(task: Task) = viewModelScope.launch { repository.updateTask(task.copy(isCompleted = !task.isCompleted)) }
    fun getStatsForType(type: PlanType) = repository.getPlanStats(type)
    val taskStats = repository.getTaskStats()
}

class GrowthViewModelFactory(private val repository: GrowthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GrowthViewModel(repository) as T
    }
}