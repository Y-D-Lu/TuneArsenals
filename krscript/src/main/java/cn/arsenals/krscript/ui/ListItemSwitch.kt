package cn.arsenals.krscript.ui

import android.content.Context
import android.widget.Switch
import cn.arsenals.krscript.R
import cn.arsenals.krscript.executor.ScriptEnvironmen
import cn.arsenals.krscript.model.SwitchNode

class ListItemSwitch(private val context: Context,
                     private val config: SwitchNode) : ListItemClickable(context, R.layout.kr_switch_list_item, config) {
    protected var switchView = layout.findViewById<Switch?>(R.id.kr_switch)

    var checked: Boolean
        get() {
            return if (switchView != null) switchView!!.isChecked else false
        }
        set(value) {
            switchView?.isChecked = value
        }

    override fun updateViewByShell() {
        super.updateViewByShell()

        if (config.getState.isNotEmpty()) {
            val shellResult = ScriptEnvironmen.executeResultRoot(context, config.getState, config)
            config.checked = shellResult == "1" || shellResult.toLowerCase() == "true"
        }
        checked = config.checked
    }

    init {
        checked = config.checked
    }
}
