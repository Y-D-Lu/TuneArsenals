package cn.arsenals.store

import android.content.Context
import cn.arsenals.common.shared.ObjectStorage
import cn.arsenals.model.TimingTaskInfo

class TimingTaskStorage(private val context: Context) : ObjectStorage<TimingTaskInfo>(context) {

    fun save(obj: TimingTaskInfo): Boolean {
        return super.save(obj, obj.taskId)
    }

}
