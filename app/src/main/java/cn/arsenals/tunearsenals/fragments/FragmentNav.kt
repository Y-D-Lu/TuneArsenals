package cn.arsenals.tunearsenals.fragments

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import cn.arsenals.TuneArsenals
import cn.arsenals.common.ui.ThemeMode
import cn.arsenals.kr.KrScriptConfig
import cn.arsenals.library.shell.BatteryUtils
import cn.arsenals.permissions.CheckRootStatus
import cn.arsenals.shell_utils.BackupRestoreUtils
import cn.arsenals.tunearsenals.R
import cn.arsenals.tunearsenals.activities.*
import cn.arsenals.utils.AccessibleServiceHelper
import com.projectkr.shell.OpenPageHelper
import kotlinx.android.synthetic.main.fragment_nav.*

class FragmentNav : Fragment(), View.OnClickListener {
    private lateinit var themeMode: ThemeMode

    companion object {
        fun createPage(themeMode: ThemeMode): Fragment {
            val fragment = FragmentNav()
            fragment.themeMode = themeMode
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_nav, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val nav = view.findViewById<LinearLayout>(R.id.nav)
        for (index in 1..nav.childCount) {
            val ele = nav.getChildAt(index)
            if (ele is GridLayout) {
                for (index2 in 0 until ele.childCount) {
                    bindClickEvent(ele.getChildAt(index2))
                }
            }
        }

        // 激活辅助服务按钮
        nav_tunearsenals_service_not_active.setOnClickListener {
            startService()
        }
    }

    private fun startService() {
        AccessibleServiceHelper().stopTuneArsenalsModeService(activity!!.applicationContext)

        /* 使用ROOT权限激活辅助服务会导致某些授权拿不到，导致事件触发不完整 */
        /*

        val dialog = ProgressBarDialog(context!!)
        dialog.showDialog("尝试使用ROOT权限开启服务...")
        Thread(Runnable {
            if (!AccessibleServiceHelper().startTuneArsenalsModeService(context!!)) {
                try {
                    myHandler.post {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        // intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    myHandler.post {
                        dialog.hideDialog()
                    }
                }
            } else {
                myHandler.post {
                    dialog.hideDialog()
                    btn_config_service_not_active.visibility = if (AccessibleServiceHelper().serviceRunning(context!!)) View.GONE else View.VISIBLE
                }
            }
        }).start()
        */
        TuneArsenals.toast("请在系统设置里激活[TuneArsenals - 场景模式]选项", Toast.LENGTH_SHORT)
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // intent.addFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME)
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    private fun bindClickEvent(view: View) {
        view.setOnClickListener(this)
        if (!CheckRootStatus.lastCheckResult && "root".equals(view.tag)) {
            view.isEnabled = false
        }
    }

    override fun onResume() {
        super.onResume()
        if (isDetached) {
            return
        }

        // 辅助服务激活状态
        val serviceState = AccessibleServiceHelper().serviceRunning(context!!)
        nav_tunearsenals_service_not_active.visibility = if (serviceState) View.GONE else View.VISIBLE

        activity!!.title = getString(R.string.app_name)
    }

    private fun tryOpenApp(packageName: String) {
        val pm = context!!.packageManager
        if (packageName.equals("cn.arsenals.gesture")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.component = ComponentName("cn.arsenals.gesture", "cn.arsenals.gesture.SettingsActivity")
                startActivity(intent)
                return
            } catch (ex: java.lang.Exception) {
            }
        } else if (packageName.equals("cn.arsenals.filter")) {
            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.component = ComponentName("cn.arsenals.filter", "cn.arsenals.filter.SettingsActivity")
                startActivity(intent)
                return
            } catch (ex: java.lang.Exception) {
            }
        }

        try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                return
            }
        } catch (ex: java.lang.Exception) {
        }

        openUrl("https://www.coolapk.com/apk/" + packageName)
        /*
            Uri uri = Uri.parse("market://details?id=" + appPkg);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (marketPkg != null) {// 如果没给市场的包名，则系统会弹出市场的列表让你进行选择。
                intent.setPackage(marketPkg);
            }
            try {
                context.startActivity(intent);
                return true;
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        */
    }

    override fun onClick(v: View?) {
        v?.run {
            if (!CheckRootStatus.lastCheckResult && "root".equals(tag)) {
                Toast.makeText(context, "没有获得ROOT权限，不能使用本功能", Toast.LENGTH_SHORT).show()
                return
            }

            when (id) {
                R.id.nav_freeze -> {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setClassName("cn.arsenals.tunearsenals", "cn.arsenals.tunearsenals.activities.ActivityFreezeApps2")
                    startActivity(intent)
                    return
                }
                R.id.nav_applictions -> {
                    val intent = Intent(context, ActivityApplistions::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_swap -> {
                    val intent = Intent(context, ActivitySwap::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_battery -> {
                    val batteryUtils = BatteryUtils()
                    if (batteryUtils.qcSettingSupport() || batteryUtils.bpSettingSupport()) {
                        val intent = Intent(context, ActivityBattery::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(context, "此功能不支持你的手机", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                R.id.nav_charge -> {
                    val intent = Intent(context, ActivityCharge::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_img -> {
                    if (BackupRestoreUtils.isSupport()) {
                        val intent = Intent(context, ActivityImg::class.java)
                        startActivity(intent)
                    } else {
                        Toast.makeText(context, "此功能不支持你的手机", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                R.id.nav_battery_stats -> {
                    val intent = Intent(context, ActivityBatteryStats::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_core_control -> {
                    val intent = Intent(context, ActivityCpuControl::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_miui_thermal -> {
                    val intent = Intent(context, ActivityMiuiThermal::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_app_tunearsenals -> {
                    val intent = Intent(context, ActivityAppConfig2::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_cpu_modes -> {
                    val intent = Intent(context, ActivityCpuModes::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_system_tunearsenals -> {
                    val intent = Intent(context, ActivitySystemTuneArsenals::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_auto_click -> {
                    val intent = Intent(context, ActivityAutoClick::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_app_magisk -> {
                    val intent = Intent(context, ActivityMagisk::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_processes -> {
                    val intent = Intent(context, ActivityProcess::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return
                }
                R.id.nav_fps_chart -> {
                    val serviceState = AccessibleServiceHelper().serviceRunning(context!!)
                    if (serviceState) {
                        val intent = Intent(context, ActivityFpsChart::class.java)
                        startActivity(intent)
                    } else {
                        TuneArsenals.toast("请在系统设置里激活[TuneArsenals - 场景模式]辅助服务", Toast.LENGTH_SHORT)
                    }
                    return
                }
                R.id.nav_additional -> {
                    val intent = Intent(context, ActivityAddin::class.java)
                    startActivity(intent)
                    return
                }
                R.id.nav_additional_all -> {
                    val krScriptConfig = KrScriptConfig().init(context!!)
                    val activity = activity!!
                    krScriptConfig.pageListConfig?.run {
                        OpenPageHelper(activity).openPage(this.apply {
                            title = getString(R.string.menu_additional)
                        })
                    }
                    return
                }
                else -> {
                    Log.w("FragmentNav", "onClick unhandled id $id")
                }
            }
        }
    }

    private fun openUrl(link: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (ex: Exception) {
        }
    }
}
