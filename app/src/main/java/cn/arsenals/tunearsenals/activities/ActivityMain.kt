package cn.arsenals.tunearsenals.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import cn.arsenals.TuneArsenals
import cn.arsenals.common.shared.MagiskExtend
import cn.arsenals.common.shell.KeepShellPublic
import cn.arsenals.common.shell.KernelProrp
import cn.arsenals.common.shell.RootFile
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.permissions.CheckRootStatus
import cn.arsenals.store.SpfConfig
import cn.arsenals.tunearsenals.R
import cn.arsenals.tunearsenals.dialogs.DialogMonitor
import cn.arsenals.tunearsenals.dialogs.DialogPower
import cn.arsenals.tunearsenals.fragments.FragmentHome
import cn.arsenals.tunearsenals.fragments.FragmentNav
import cn.arsenals.tunearsenals.fragments.FragmentNotRoot
import cn.arsenals.ui.TabIconHelper2
import cn.arsenals.utils.ElectricityUnit
import cn.arsenals.utils.Update
import kotlinx.android.synthetic.main.activity_main.*

class ActivityMain : ActivityBase() {
    private lateinit var globalSPF: SharedPreferences

    private class ThermalCheckThread(private var context: Activity) : Thread() {
        private fun deleteThermalCopyWarn(onYes: Runnable) {
            TuneArsenals.post {
                if (!context.isFinishing) {
                    val view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_thermal, null)
                    val dialog = DialogHelper.customDialog(context, view)
                    view.findViewById<View>(R.id.btn_no).setOnClickListener {
                        dialog.dismiss()
                    }
                    view.findViewById<View>(R.id.btn_yes).setOnClickListener {
                        dialog.dismiss()
                        onYes.run()
                    }
                    dialog.setCancelable(false)
                }
            }
        }

        override fun run() {
            sleep(500)
            if (
                    MagiskExtend.magiskSupported() &&
                    KernelProrp.getProp("${MagiskExtend.MAGISK_PATH}system/vendor/etc/thermal.current.ini") != ""
            ) {
                when {
                    RootFile.list("/data/thermal/config").size > 0 -> {
                        deleteThermalCopyWarn {
                            KeepShellPublic.doCmdSync(
                                    "chattr -R -i /data/thermal 2> /dev/null\n" +
                                            "rm -rf /data/thermal 2> /dev/null\n" +
                                            "sync;svc power reboot || reboot;"
                            )
                        }
                    }
                    RootFile.list("/data/vendor/thermal/config").size > 0 -> {
                        if (
                                RootFile.fileEquals(
                                        "/data/vendor/thermal/config/thermal-normal.conf",
                                        MagiskExtend.getMagiskReplaceFilePath("/system/vendor/etc/thermal-normal.conf")
                                )
                        ) {
                            // TuneArsenals.toast("?????????????????????????????????", Toast.LENGTH_SHORT)
                            return
                        } else {
                            deleteThermalCopyWarn {
                                KeepShellPublic.doCmdSync(
                                        "chattr -R -i /data/vendor/thermal 2> /dev/null\n" +
                                                "rm -rf /data/vendor/thermal 2> /dev/null\n" +
                                                "sync;svc power reboot || reboot;"
                                )
                            }
                        }
                    }
                    else -> return
                }
            }
        }
    }

    @SuppressLint("ResourceAsColor")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ActivityStartSplash.finished) {
            val intent = Intent(this.applicationContext, ActivityStartSplash::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            // intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            startActivity(intent)
            finish()
            return
        }

        /*
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()   // or .detectAll() for all detectable problems
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .detectAll()
                .build());
        */

        globalSPF = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        if (!globalSPF.contains(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT)) {
            globalSPF.edit().putInt(SpfConfig.GLOBAL_SPF_CURRENT_NOW_UNIT, ElectricityUnit().getDefaultElectricityUnit(this)).apply()
        }

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val tabIconHelper2 = TabIconHelper2(tab_list, tab_content, this, supportFragmentManager, R.layout.list_item_tab2)
        tabIconHelper2.newTabSpec(getString(R.string.app_nav), getDrawable(R.drawable.app_more)!!, FragmentNav.createPage(themeMode))
        tabIconHelper2.newTabSpec(getString(R.string.app_home), getDrawable(R.drawable.app_home)!!, (if (CheckRootStatus.lastCheckResult) {
            FragmentHome()
        } else {
            FragmentNotRoot()
        }))
        tab_content.adapter = tabIconHelper2.adapter
        tab_list.getTabAt(1)?.select() // ?????????????????????

        if (CheckRootStatus.lastCheckResult) {
            try {
                if (MagiskExtend.magiskSupported() &&
                        !(MagiskExtend.moduleInstalled() || globalSPF.getBoolean("magisk_dot_show", false))
                ) {
                    DialogHelper.confirm(this,
                            getString(R.string.magisk_install_title),
                            getString(R.string.magisk_install_desc),
                            {
                                MagiskExtend.magiskModuleInstall(this)
                            })
                    // ???????????? globalSPF.edit().putBoolean("magisk_dot_show", true).apply()
                }
            } catch (ex: Exception) {
                DialogHelper.alert(
                        this,
                        getString(R.string.sorry),
                        "??????????????????\n" + ex.message
                ) {
                    recreate()
                }
            }
            ThermalCheckThread(this).start()
        }

    }

    override fun onResume() {
        super.onResume()

        // ???????????????????????????????????? 24 ??????
        if (globalSPF.getLong(SpfConfig.GLOBAL_SPF_LAST_UPDATE, 0) + (3600 * 24 * 1000) < System.currentTimeMillis()) {
            Update().checkUpdate(this)
            globalSPF.edit().putLong(SpfConfig.GLOBAL_SPF_LAST_UPDATE, System.currentTimeMillis()).apply()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    }

    //???????????????
    override fun onBackPressed() {
        try {
            when {
                supportFragmentManager.backStackEntryCount > 0 -> {
                    supportFragmentManager.popBackStack()
                }
                else -> {
                    excludeFromRecent()
                    super.onBackPressed()
                    this.finishActivity(0)
                }
            }
        } catch (ex: Exception) {
            ex.stackTrace
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    //???????????????
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> startActivity(Intent(this.applicationContext, ActivityOtherSettings::class.java))
            R.id.action_power -> DialogPower(this).showPowerMenu()
            R.id.action_graph -> {
                if (!CheckRootStatus.lastCheckResult) {
                    Toast.makeText(this, "????????????ROOT??????????????????????????????", Toast.LENGTH_SHORT).show()
                    return false
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        DialogMonitor(this).show()
                    } else {
                        //??????????????????????????????
                        //val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                        //startActivity(intent);
                        val intent = Intent()
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        intent.data = Uri.fromParts("package", this.packageName, null)
                        Toast.makeText(applicationContext, getString(R.string.permission_float), Toast.LENGTH_LONG).show()
                    }
                } else {
                    DialogMonitor(this).show()
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    public override fun onPause() {
        super.onPause()
        if (!CheckRootStatus.lastCheckResult) {
            finish()
        }
    }

    override fun onDestroy() {
        val fragmentManager = supportFragmentManager
        fragmentManager.fragments.clear()
        super.onDestroy()
    }
}
