package com.example.growthplan.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

enum class PlanType(val level: Int, val label: String) {
    FIVE_YEAR(0, "5-Year Vision"),
    YEAR(1, "Annual Goal"),
    QUARTER(2, "Quarterly Target"),
    MONTH(3, "Monthly Milestone"),
    WEEK(4, "Weekly Action")
}

class Converters {
    @TypeConverter fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
    @TypeConverter fun dateToTimestamp(date: Date?): Long? = date?.time
    @TypeConverter fun fromPlanType(value: String): PlanType = PlanType.valueOf(value)
    @TypeConverter fun planTypeToString(type: PlanType): String = type.name
}

@Entity(tableName = "plans")
data class Plan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val type: PlanType,
    val parentId: Long? = null,
    val isCompleted: Boolean = false,
    val aiCoachingTip: String? = null, // 新增：存储AI的点评
    val createdAt: Date = Date()
)

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val isCompleted: Boolean = false,
    val aiFeedback: String? = null, // 新增：完成任务后的AI奖励语
    val date: Date = Date()
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM plans WHERE type = :type ORDER BY createdAt DESC")
    fun getPlansByType(type: PlanType): Flow<List<Plan>>
    @Query("SELECT * FROM plans WHERE parentId = :parentId ORDER BY createdAt DESC")
    fun getChildPlans(parentId: Long): Flow<List<Plan>>
    @Query("SELECT * FROM plans ORDER BY type ASC, createdAt DESC")
    fun getAllPlans(): Flow<List<Plan>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: Plan)
    @Delete suspend fun deletePlan(plan: Plan)
    @Update suspend fun updatePlan(plan: Plan)
    @Query("SELECT count(*) FROM plans WHERE type = :type AND isCompleted = 1")
    fun getCompletedCountByType(type: PlanType): Flow<Int>
    @Query("SELECT count(*) FROM plans WHERE type = :type")
    fun getTotalCountByType(type: PlanType): Flow<Int>
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date >= :startOfDay AND date < :endOfDay ORDER BY isCompleted ASC")
    fun getTasksForDate(startOfDay: Long, endOfDay: Long): Flow<List<Task>>
    @Query("SELECT * FROM tasks ORDER BY date DESC")
    fun getAllTasks(): Flow<List<Task>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)
    @Delete suspend fun deleteTask(task: Task)
    @Update suspend fun updateTask(task: Task)
    @Query("SELECT count(*) FROM tasks WHERE isCompleted = 1")
    fun getCompletedTaskCount(): Flow<Int>
    @Query("SELECT count(*) FROM tasks")
    fun getTotalTaskCount(): Flow<Int>
}

@Database(entities = [Plan::class, Task::class], version = 2, exportSchema = false) // 版本升到2
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun taskDao(): TaskDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "growth_plan_db")
                    .fallbackToDestructiveMigration() // 简单处理：数据库升级直接清空
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class GrowthRepository(private val planDao: PlanDao, private val taskDao: TaskDao) {
    fun getPlansByType(type: PlanType) = planDao.getPlansByType(type)
    fun getChildPlans(parentId: Long) = planDao.getChildPlans(parentId)
    fun getAllPlans() = planDao.getAllPlans()
    suspend fun addPlan(plan: Plan) = planDao.insertPlan(plan)
    suspend fun updatePlan(plan: Plan) = planDao.updatePlan(plan)
    suspend fun deletePlan(plan: Plan) = planDao.deletePlan(plan)
    fun getPlanStats(type: PlanType) = kotlinx.coroutines.flow.combine(planDao.getCompletedCountByType(type), planDao.getTotalCountByType(type)) { c, t -> Pair(c, t) }
    fun getTasksForToday(): Flow<List<Task>> {
        val now = java.util.Calendar.getInstance()
        now.set(java.util.Calendar.HOUR_OF_DAY, 0)
        val start = now.timeInMillis
        now.add(java.util.Calendar.DAY_OF_YEAR, 1)
        return taskDao.getTasksForDate(start, now.timeInMillis)
    }
    fun getTaskStats() = kotlinx.coroutines.flow.combine(taskDao.getCompletedTaskCount(), taskDao.getTotalTaskCount()) { c, t -> Pair(c, t) }
    suspend fun addTask(task: Task) = taskDao.insertTask(task)
    suspend fun updateTask(task: Task) = taskDao.updateTask(task)
    suspend fun deleteTask(task: Task) = taskDao.deleteTask(task)
}