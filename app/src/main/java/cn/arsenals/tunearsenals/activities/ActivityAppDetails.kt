package cn.arsenals.tunearsenals.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Checkable
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.data.EventBus
import cn.arsenals.data.EventType
import cn.arsenals.library.permissions.NotificationListener
import cn.arsenals.library.shell.CGroupMemoryUtlis
import cn.arsenals.model.TuneArsenalsConfigInfo
import cn.arsenals.permissions.WriteSettings
import cn.arsenals.store.SpfConfig
import cn.arsenals.store.TuneArsenalsConfigStore
import cn.arsenals.tunearsenals.R
import cn.arsenals.tunearsenals.dialogs.DialogAppBoostPolicy
import cn.arsenals.tunearsenals.dialogs.DialogAppCGroupMem
import cn.arsenals.tunearsenals.dialogs.DialogAppOrientation
import cn.arsenals.tunearsenals.dialogs.DialogAppPowerConfig
import cn.arsenals.tunearsenals_mode.ImmersivePolicyControl
import cn.arsenals.tunearsenals_mode.ModeSwitcher
import cn.arsenals.tunearsenals_mode.TuneArsenalsMode
import cn.arsenals.utils.AccessibleServiceHelper
import kotlinx.android.synthetic.main.activity_app_details.*

class ActivityAppDetails : ActivityBase() {
    var app = ""
    lateinit var immersivePolicyControl: ImmersivePolicyControl
    lateinit var tunearsenalsConfigInfo: TuneArsenalsConfigInfo
    private var dynamicCpu: Boolean = false
    private var _result = RESULT_CANCELED
    private lateinit var tunearsenalsBlackList: SharedPreferences
    private lateinit var spfGlobal: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_details)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        // setTitle(R.string.app_name)

        // 显示返回按钮
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { _ ->
            // finish()
            saveConfigAndFinish()
        }

        spfGlobal = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        val intent = this.intent
        if (intent == null) {
            setResult(_result, this.intent)
            finish()
            return
        }
        val extras = this.intent.extras
        if (extras == null || !extras.containsKey("app")) {
            setResult(_result, this.intent)
            finish()
            return
        }

        app = extras.getString("app")!!

        if (app == "android" || app == "com.android.systemui" || app == "com.android.webview" || app == "mokee.platform" || app == "com.miui.rom") {
            app_details_perf.visibility = View.GONE
            app_details_auto.visibility = View.GONE
            app_details_assist.visibility = View.GONE
            app_details_freeze.isEnabled = false
            tunearsenals_mode_config.visibility = View.GONE
            tunearsenals_mode_allow.visibility = View.GONE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            app_details_assist.visibility = View.GONE
        }

        // 场景模式白名单开关
        tunearsenalsBlackList = getSharedPreferences(SpfConfig.SCENE_BLACK_LIST, Context.MODE_PRIVATE)
        tunearsenals_mode_allow.setOnClickListener {
            val checked = (it as Checkable).isChecked
            tunearsenals_mode_config.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) {
                tunearsenalsBlackList.edit().remove(app).apply()
            } else {
                tunearsenalsBlackList.edit().putBoolean(app, true).apply()
            }
        }

        immersivePolicyControl = ImmersivePolicyControl(contentResolver)

        dynamicCpu = spfGlobal.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DEFAULT)

        app_details_dynamic.setOnClickListener {
            if (!dynamicCpu) {
                DialogHelper.helpInfo(this, "", "请先回到功能列表，进入 [性能配置] 功能，开启 [动态响应] 功能")
                return@setOnClickListener
            }

            val spfPowercfg = getSharedPreferences(SpfConfig.POWER_CONFIG_SPF, Context.MODE_PRIVATE)

            DialogAppPowerConfig(this,
                    spfPowercfg.getString(app, ""),
                    object : DialogAppPowerConfig.IResultCallback {
                        override fun onChange(mode: String?) {
                            spfPowercfg.edit().run {
                                if (mode.isNullOrEmpty()) {
                                    remove(app)
                                } else {
                                    putString(app, mode)
                                }
                            }.apply()

                            (it as TextView).text = ModeSwitcher.getModName("" + mode)
                            _result = RESULT_OK
                            notifyService(app, "" + mode)
                        }
                    }).show()
        }

        app_details_cgroup_mem.setOnClickListener {
            val utlis = CGroupMemoryUtlis(this)
            if (!utlis.isSupported) {
                DialogHelper.helpInfo(this, "", "抱歉，您的内核不支持该功能特性~")
                return@setOnClickListener
            }
            DialogAppCGroupMem(this, tunearsenalsConfigInfo.fgCGroupMem, object : DialogAppCGroupMem.IResultCallback {
                override fun onChange(group: String?, name: String?) {
                    tunearsenalsConfigInfo.fgCGroupMem = group
                    (it as TextView).text = name
                    _result = RESULT_OK
                }
            }).show()
        }


        app_details_cgroup_mem2.setOnClickListener {
            val utlis = CGroupMemoryUtlis(this)
            if (!utlis.isSupported) {
                DialogHelper.helpInfo(this, "", "抱歉，您的内核不支持该功能特性~")
                return@setOnClickListener
            }

            DialogAppCGroupMem(this, tunearsenalsConfigInfo.bgCGroupMem, object : DialogAppCGroupMem.IResultCallback {
                override fun onChange(group: String?, name: String?) {
                    tunearsenalsConfigInfo.bgCGroupMem = group
                    (it as TextView).text = name
                    _result = RESULT_OK
                }
            }).show()
        }

        app_details_boost_mem.setOnClickListener {
            DialogAppBoostPolicy(this, tunearsenalsConfigInfo.dynamicBoostMem, object : DialogAppBoostPolicy.IResultCallback {
                override fun onChange(enabled: Boolean) {
                    tunearsenalsConfigInfo.dynamicBoostMem = enabled
                    (it as TextView).text = if (enabled) "已启用" else "未启用"
                    _result = RESULT_OK
                }
            }).show()
        }

        app_details_hidenav.setOnClickListener {
            if (!WriteSettings().getPermission(this)) {
                WriteSettings().setPermission(this)
                Toast.makeText(applicationContext, getString(R.string.tunearsenals_need_write_sys_settings), Toast.LENGTH_SHORT).show()
                (it as Switch).isChecked = !it.isChecked
                return@setOnClickListener
            }
            val isSelected = (it as Switch).isChecked
            if (isSelected && app_details_hidestatus.isChecked) {
                immersivePolicyControl.hideAll(app)
            } else if (isSelected) {
                immersivePolicyControl.hideNavBar(app)
            } else {
                immersivePolicyControl.showNavBar(app)
            }
        }
        app_details_hidestatus.setOnClickListener {
            if (!WriteSettings().getPermission(this)) {
                WriteSettings().setPermission(this)
                Toast.makeText(applicationContext, getString(R.string.tunearsenals_need_write_sys_settings), Toast.LENGTH_SHORT).show()
                (it as Switch).isChecked = !it.isChecked
                return@setOnClickListener
            }
            val isSelected = (it as Switch).isChecked
            if (isSelected && app_details_hidenav.isChecked) {
                immersivePolicyControl.hideAll(app)
            } else if (isSelected) {
                immersivePolicyControl.hideStatusBar(app)
            } else {
                immersivePolicyControl.showStatusBar(app)
            }
        }

        app_details_icon.setOnClickListener {
            try {
                saveConfig()
                startActivity(packageManager.getLaunchIntentForPackage(app))
            } catch (ex: Exception) {
                Toast.makeText(applicationContext, getString(R.string.start_app_fail), Toast.LENGTH_SHORT).show()
            }
        }

        tunearsenalsConfigInfo = TuneArsenalsConfigStore(this).getAppConfig(app)

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
            app_details_hidenotice.isEnabled = false
        } else {
            app_details_hidenotice.setOnClickListener {
                if (!NotificationListener().getPermission(this)) {
                    NotificationListener().setPermission(this)
                    Toast.makeText(applicationContext, getString(R.string.tunearsenals_need_notic_listing), Toast.LENGTH_SHORT).show()
                    (it as Switch).isChecked = !it.isChecked
                    return@setOnClickListener
                }
                tunearsenalsConfigInfo.disNotice = (it as Switch).isChecked
            }
        }
        tunearsenals_orientation.setOnClickListener {
            DialogAppOrientation(this, tunearsenalsConfigInfo.screenOrientation, object : DialogAppOrientation.IResultCallback {
                override fun onChange(value: Int, name: String?) {
                    tunearsenalsConfigInfo.screenOrientation = value
                    (it as TextView).text = "" + name
                }
            }).show()
        }
        app_details_aloowlight.setOnClickListener {
            if (!WriteSettings().getPermission(this)) {
                WriteSettings().setPermission(this)
                Toast.makeText(applicationContext, getString(R.string.tunearsenals_need_write_sys_settings), Toast.LENGTH_SHORT).show()
                (it as Switch).isChecked = false
                return@setOnClickListener
            }
            tunearsenalsConfigInfo.aloneLight = (it as Switch).isChecked
        }
        app_details_gps.setOnClickListener {
            tunearsenalsConfigInfo.gpsOn = (it as Switch).isChecked
        }

        app_details_freeze.setOnClickListener {
            tunearsenalsConfigInfo.freeze = (it as Switch).isChecked
            if (!tunearsenalsConfigInfo.freeze) {
                TuneArsenalsMode.unfreezeApp(tunearsenalsConfigInfo.packageName)
            }
        }

        app_monitor.setOnClickListener {
            tunearsenalsConfigInfo.showMonitor = (it as Switch).isChecked
        }
    }

    // 通知辅助服务配置变化
    private fun notifyService(app: String, mode: String? = null) {
        if (AccessibleServiceHelper().serviceRunning(this)) {
            EventBus.publish(EventType.SCENE_APP_CONFIG, HashMap<String, Any>().apply {
                put("app", app)
                if (mode != null) {
                    put("mode", mode)
                }
            })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.save, menu)
        return true
    }

    //右上角菜单
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_save -> {
                saveConfigAndFinish()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()
        val powercfg = getSharedPreferences(SpfConfig.POWER_CONFIG_SPF, Context.MODE_PRIVATE)

        var packageInfo: PackageInfo? = null
        try {
            packageInfo = packageManager.getPackageInfo(app, 0)
        } catch (ex: Exception) {
            Toast.makeText(applicationContext, "所选的应用已被卸载！", Toast.LENGTH_SHORT).show()
        }
        if (packageInfo == null) {
            finish()
            return
        }
        val applicationInfo = packageInfo.applicationInfo
        app_details_name.text = applicationInfo.loadLabel(packageManager)
        app_details_packagename.text = packageInfo.packageName
        app_details_icon.setImageDrawable(applicationInfo.loadIcon(packageManager))

        val firstMode = spfGlobal.getString(SpfConfig.GLOBAL_SPF_POWERCFG_FIRST_MODE, "")
        app_details_dynamic.text = ModeSwitcher.getModName(powercfg.getString(app, firstMode)!!)

        app_details_cgroup_mem.text = DialogAppCGroupMem.Transform(this).getName(tunearsenalsConfigInfo.fgCGroupMem)
        app_details_cgroup_mem2.text = DialogAppCGroupMem.Transform(this).getName(tunearsenalsConfigInfo.bgCGroupMem)
        app_details_boost_mem.text = if (tunearsenalsConfigInfo.dynamicBoostMem) "已启用" else "未启用"

        if (immersivePolicyControl.isFullScreen(app)) {
            app_details_hidenav.isChecked = true
            app_details_hidestatus.isChecked = true
        } else {
            app_details_hidenav.isChecked = immersivePolicyControl.isHideNavbarOnly(app)
            app_details_hidestatus.isChecked = immersivePolicyControl.isHideStatusOnly(app)
        }

        app_details_hidenotice.isChecked = tunearsenalsConfigInfo.disNotice
        app_details_aloowlight.isChecked = tunearsenalsConfigInfo.aloneLight
        app_details_gps.isChecked = tunearsenalsConfigInfo.gpsOn
        app_details_freeze.isChecked = tunearsenalsConfigInfo.freeze
        app_monitor.isChecked = tunearsenalsConfigInfo.showMonitor

        tunearsenals_mode_allow.isChecked = !tunearsenalsBlackList.contains(app)
        tunearsenals_mode_config.visibility = if (tunearsenals_mode_config.visibility == View.VISIBLE && tunearsenals_mode_allow.isChecked) View.VISIBLE else View.GONE

        val screenOrientation = tunearsenalsConfigInfo.screenOrientation
        tunearsenals_orientation.text = DialogAppOrientation.Transform(this).getName(screenOrientation)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            saveConfigAndFinish()
        }
        return false
    }

    private fun saveConfigAndFinish() {
        saveConfig()
        this.finish()
    }

    private fun saveConfig() {
        val originConfig = TuneArsenalsConfigStore(this).getAppConfig(tunearsenalsConfigInfo.packageName)

        if (
                tunearsenalsConfigInfo.screenOrientation != originConfig.screenOrientation ||
                tunearsenalsConfigInfo.aloneLight != originConfig.aloneLight ||
                tunearsenalsConfigInfo.disNotice != originConfig.disNotice ||
                tunearsenalsConfigInfo.disButton != originConfig.disButton ||
                tunearsenalsConfigInfo.gpsOn != originConfig.gpsOn ||
                tunearsenalsConfigInfo.freeze != originConfig.freeze ||
                tunearsenalsConfigInfo.fgCGroupMem != originConfig.fgCGroupMem ||
                tunearsenalsConfigInfo.bgCGroupMem != originConfig.bgCGroupMem ||
                tunearsenalsConfigInfo.dynamicBoostMem != originConfig.dynamicBoostMem ||
                tunearsenalsConfigInfo.showMonitor != originConfig.showMonitor
        ) {
            setResult(RESULT_OK, this.intent)
        } else {
            setResult(_result, this.intent)
        }
        if (!TuneArsenalsConfigStore(this).setAppConfig(tunearsenalsConfigInfo)) {
            Toast.makeText(applicationContext, getString(R.string.config_save_fail), Toast.LENGTH_LONG).show()
        } else {
            if (tunearsenalsConfigInfo.fgCGroupMem != originConfig.fgCGroupMem ||
                    tunearsenalsConfigInfo.bgCGroupMem != originConfig.bgCGroupMem ||
                    tunearsenalsConfigInfo.dynamicBoostMem != originConfig.dynamicBoostMem) {
                notifyService(app)
            }

            if (tunearsenalsConfigInfo.freeze != originConfig.freeze) {
                if (tunearsenalsConfigInfo.freeze) {
                    TuneArsenalsMode.getCurrentInstance()?.setFreezeAppLeaveTime(tunearsenalsConfigInfo.packageName)
                }
            }
        }
    }

}
