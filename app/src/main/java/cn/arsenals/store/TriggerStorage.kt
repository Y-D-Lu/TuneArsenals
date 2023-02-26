package cn.arsenals.store

import android.content.Context
import cn.arsenals.common.shared.ObjectStorage
import cn.arsenals.model.TriggerInfo

class TriggerStorage(private val context: Context) : ObjectStorage<TriggerInfo>(context) {

    fun save(obj: TriggerInfo): Boolean {
        return super.save(obj, obj.id)
    }

}
