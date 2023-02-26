package cn.arsenals.tunearsenals_mode

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import cn.arsenals.library.calculator.GetUpTime
import cn.arsenals.model.TimingTaskInfo
import cn.arsenals.store.TimingTaskStorage

class TimingTaskManager(private var context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val taskListConfig = context.getSharedPreferences("tunearsenals_task_list", Context.MODE_PRIVATE)

    private fun getPendingIntent(timingTaskInfo: TimingTaskInfo): PendingIntent {
        val taskId = timingTaskInfo.taskId
        val taskIntent = Intent(context, TuneArsenalsTaskIntentService::class.java)
        taskIntent.putExtra("taskId", taskId)
        taskIntent.action = taskId
        taskIntent.action = taskId
        val pendingIntent = PendingIntent.getService(context, 0, taskIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        return pendingIntent
    }

    fun setTaskAndSave(timingTaskInfo: TimingTaskInfo) {
        TimingTaskStorage(context).save(timingTaskInfo)

        val taskId = timingTaskInfo.taskId

        setTask(timingTaskInfo)

        taskListConfig.edit().putBoolean(taskId, timingTaskInfo.enabled).apply()
    }

    fun setTask(timingTaskInfo: TimingTaskInfo) {
        // 如果任务启用了，立即添加到队列
        if (timingTaskInfo.enabled && (timingTaskInfo.expireDate < 1 || timingTaskInfo.expireDate > System.currentTimeMillis())) {
            val delay = GetUpTime(timingTaskInfo.triggerTimeMinutes).minutes.toLong() * 60 * 1000 // 下次执行

            val pendingIntent = getPendingIntent(timingTaskInfo)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pendingIntent)
            }
        } else {
            timingTaskInfo.enabled = false
            cancelTask(timingTaskInfo)
        }
    }

    fun updateAlarmManager() {
        val tasks = listTask()
        tasks.forEach {
            setTask(it)
        }
    }

    fun listTask(): ArrayList<TimingTaskInfo> {
        val taskList = ArrayList<TimingTaskInfo>()
        val storage = TimingTaskStorage(context)
        taskListConfig.all.keys.forEach {
            storage.load(it)?.run {
                taskList.add(this)
            }
        }
        return taskList
    }

    fun cancelTask(timingTaskInfo: TimingTaskInfo) {
        val pendingIntent = getPendingIntent(timingTaskInfo)
        cancelTask(pendingIntent)
    }

    fun removeTask(timingTaskInfo: TimingTaskInfo) {
        cancelTask(timingTaskInfo)
        taskListConfig.edit().remove(timingTaskInfo.taskId).apply()
        val storage = TimingTaskStorage(context)
        storage.remove(timingTaskInfo.taskId)
    }

    /**
     */
    fun cancelTask(pendingIntent: PendingIntent): TimingTaskManager {
        alarmManager.cancel(pendingIntent)
        return this
    }
}