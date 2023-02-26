package cn.arsenals.tunearsenals.activities

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import cn.arsenals.common.ui.AdapterAppChooser
import cn.arsenals.common.ui.DialogAppChooser
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.common.ui.ProgressBarDialog
import cn.arsenals.library.calculator.GetUpTime
import cn.arsenals.model.AppInfo
import cn.arsenals.model.TimingTaskInfo
import cn.arsenals.model.TriggerInfo
import cn.arsenals.tunearsenals_mode.ModeSwitcher
import cn.arsenals.tunearsenals_mode.TuneArsenalsStandbyMode
import cn.arsenals.tunearsenals_mode.TimingTaskManager
import cn.arsenals.tunearsenals_mode.TriggerManager
import cn.arsenals.store.SpfConfig
import cn.arsenals.ui.TuneArsenalsTaskItem
import cn.arsenals.ui.TuneArsenalsTriggerItem
import cn.arsenals.ui.TabIconHelper
import cn.arsenals.utils.AppListHelper
import cn.arsenals.tunearsenals.R
import kotlinx.android.synthetic.main.activity_system_tunearsenals.*

class ActivitySystemTuneArsenals : ActivityBase() {
    private lateinit var processBarDialog: ProgressBarDialog
    private lateinit var globalSPF: SharedPreferences
    private lateinit var chargeConfig: SharedPreferences
    internal val myHandler: Handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_tunearsenals)

        setBackArrow()
        onViewCreated()
    }

    private lateinit var modeSwitcher: ModeSwitcher

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_system_tunearsenals)

        updateCustomList()
    }

    private fun updateCustomList() {
        nextTask = null
        system_tunearsenals_task_list.removeAllViews()
        TimingTaskManager(context).listTask().forEach {
            addCustomTaskItemView(it)
            checkNextTask(it)
        }
        updateNextTaskInfo()

        system_tunearsenals_trigger_list.removeAllViews()
        TriggerManager(context).list().forEach {
            it?.run {
                addCustomTriggerView(it)
            }
        }
    }

    private var nextTask: TimingTaskInfo? = null // 下一个要执行的任务
    private fun checkNextTask(it: TimingTaskInfo) {
        if (it.enabled && (it.expireDate < 1 || it.expireDate > System.currentTimeMillis())) {
            if (nextTask == null || GetUpTime(it.triggerTimeMinutes).minutes < GetUpTime(nextTask!!.triggerTimeMinutes).minutes) {
                nextTask = it
            }
        }
    }

    private fun updateNextTaskInfo() {
        system_tunearsenals_next_content.removeAllViews()
        if (nextTask != null) {
            system_tunearsenals_next_content.addView(buildCustomTaskItemView(nextTask!!))
        }
    }

    private fun onViewCreated() {
        modeSwitcher = ModeSwitcher()
        processBarDialog = ProgressBarDialog(this)
        globalSPF = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        chargeConfig = getSharedPreferences(SpfConfig.CHARGE_SPF, Context.MODE_PRIVATE)

        val tabIconHelper = TabIconHelper(configlist_tabhost, this)
        configlist_tabhost.setup()

        tabIconHelper.newTabSpec("系统场景", getDrawable(R.drawable.tab_security)!!, R.id.blacklist_tab3)
        tabIconHelper.newTabSpec("设置", getDrawable(R.drawable.tab_settings)!!, R.id.configlist_tab5)
        configlist_tabhost.currentTab = 0
        configlist_tabhost.setOnTabChangedListener { tabId ->
            tabIconHelper.updateHighlight()
        }

        if (chargeConfig.getBoolean(SpfConfig.CHARGE_SPF_BP, false)) {
            system_tunearsenals_bp.visibility = View.VISIBLE
            val limit = chargeConfig.getInt(SpfConfig.CHARGE_SPF_BP_LEVEL, SpfConfig.CHARGE_SPF_BP_LEVEL_DEFAULT)
            system_tunearsenals_bp_lt.text = (limit - 20).toString() + "%"
            system_tunearsenals_bp_gt.text = limit.toString() + "%"
        }

        system_tunearsenals_add_task.setOnClickListener {
            val intent = Intent(this, ActivityTimingTask::class.java)
            startActivity(intent)
        }

        system_tunearsenals_add_trigger.setOnClickListener {
            val intent = Intent(this, ActivityTrigger::class.java)
            startActivity(intent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            system_tunearsenals_standby_apps.visibility = View.VISIBLE
            system_tunearsenals_standby_apps.setOnClickListener {
                standbyAppConfig()
            }
        } else {
            system_tunearsenals_standby_apps.visibility = View.GONE
        }

        system_tunearsenals_command.setOnClickListener {
            val intent = Intent(this, ActivityCustomCommand::class.java)
            startActivity(intent)
        }
    }

    // 设置待机模式的应用
    private fun standbyAppConfig() {
        processBarDialog.showDialog()
        Thread {
            val configFile = context.getSharedPreferences(TuneArsenalsStandbyMode.configSpfName, Context.MODE_PRIVATE)
            val whiteList = context.resources.getStringArray(R.array.tunearsenals_standby_white_list)
            val options = ArrayList(AppListHelper(context).getAll().filter {
                !whiteList.contains(it.packageName)
            }.sortedBy {
                it.appType
            }.map {
                it.apply {
                    selected = configFile.getBoolean(packageName.toString(), it.appType == AppInfo.AppType.USER && !it.updated)
                }
            })

            myHandler.post {
                processBarDialog.hideDialog()

                DialogAppChooser(themeMode.isDarkMode, ArrayList(options), true, object : DialogAppChooser.Callback {
                    override fun onConfirm(apps: List<AdapterAppChooser.AppInfo>) {
                        val items = apps.map { it.packageName }
                        options.forEach {
                            it.selected = items.contains(it.packageName)
                        }
                        saveStandbyAppConfig(options)
                    }
                }).show(supportFragmentManager, "standby_apps")
            }
        }.start()
    }

    // 保存休眠应用配置
    private fun saveStandbyAppConfig(apps: List<AppInfo>) {
        val configFile = getSharedPreferences(TuneArsenalsStandbyMode.configSpfName, Context.MODE_PRIVATE).edit()
        configFile.clear()

        apps.forEach {
            if (it.selected && it.appType == AppInfo.AppType.SYSTEM) {
                configFile.putBoolean(it.packageName.toString(), true)
            } else if ((!it.selected) && it.appType == AppInfo.AppType.USER) {
                configFile.putBoolean(it.packageName.toString(), false)
            }
        }

        configFile.apply()
    }

    private fun buildCustomTaskItemView(timingTaskInfo: TimingTaskInfo): TuneArsenalsTaskItem {
        val tunearsenalsTaskItem = TuneArsenalsTaskItem(context, timingTaskInfo)
        tunearsenalsTaskItem.setLayoutParams(LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        tunearsenalsTaskItem.isClickable = true
        return tunearsenalsTaskItem
    }

    private fun addCustomTaskItemView(timingTaskInfo: TimingTaskInfo) {
        val tunearsenalsTaskItem = buildCustomTaskItemView(timingTaskInfo)

        system_tunearsenals_task_list.addView(tunearsenalsTaskItem)
        tunearsenalsTaskItem.setOnClickListener {
            val intent = Intent(this, ActivityTimingTask::class.java)
            intent.putExtra("taskId", timingTaskInfo.taskId)
            startActivity(intent)
        }
        tunearsenalsTaskItem.setOnLongClickListener {
            DialogHelper.confirm(this, "删除该任务？", "", {
                TimingTaskManager(context).removeTask(timingTaskInfo)
                updateCustomList()
            })
            true
        }
    }

    private fun addCustomTriggerView(triggerInfo: TriggerInfo) {
        val itemView = TuneArsenalsTriggerItem(context, triggerInfo)
        itemView.setLayoutParams(LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        itemView.isClickable = true

        system_tunearsenals_trigger_list.addView(itemView)

        itemView.setOnClickListener {
            val intent = Intent(this, ActivityTrigger::class.java)
            intent.putExtra("id", triggerInfo.id)
            startActivity(intent)
        }
        itemView.setOnLongClickListener {
            DialogHelper.confirm(this, "删除该触发器？", "", {
                TriggerManager(context).removeTrigger(triggerInfo)
                updateCustomList()
            })
            true
        }
    }

    override fun onDestroy() {
        processBarDialog.hideDialog()
        super.onDestroy()
    }
}
