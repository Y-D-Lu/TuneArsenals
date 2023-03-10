package cn.arsenals

import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import cn.arsenals.common.shared.FileWrite
import cn.arsenals.common.shell.ShellExecutor
import cn.arsenals.data.EventBus
import cn.arsenals.data.customer.ChargeCurve
import cn.arsenals.data.customer.ScreenOffCleanup
import cn.arsenals.data.publisher.BatteryState
import cn.arsenals.data.publisher.ScreenState
import cn.arsenals.permissions.Busybox
import cn.arsenals.permissions.CheckRootStatus
import cn.arsenals.store.SpfConfig
import cn.arsenals.tunearsenals.R
import cn.arsenals.tunearsenals_mode.TimingTaskManager
import cn.arsenals.tunearsenals_mode.TriggerIEventMonitor
import cn.arsenals.utils.CrashHandler

class TuneArsenals : Application() {
    companion object {
        private val handler = Handler(Looper.getMainLooper())
        lateinit var context: Application
        lateinit var thisPackageName: String
        private var nightMode = false
        private var config: SharedPreferences? = null
        val globalConfig:SharedPreferences
            get () {
                if (config == null) {
                    config = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
                }
                return config!!
            }

        val isNightMode: Boolean
            get() {
                return nightMode
            }

        fun getBoolean(key: String, defaultValue: Boolean): Boolean {
            return globalConfig.getBoolean(key, defaultValue)
        }

        fun setBoolean(key: String, value: Boolean) {
            globalConfig.edit().putBoolean(key, value).apply()
        }

        fun getString(key: String, defaultValue: String): String? {
            return globalConfig.getString(key, defaultValue)
        }

        fun toast(message: String, time: Int) {
            handler.post {
                Toast.makeText(context, message, time).show()
            }
        }

        fun toast(message: String) {
            handler.post {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }

        fun toast(message: Int, time: Int) {
            handler.post {
                Toast.makeText(context, message, time).show()
            }
        }

        fun post(runnable: Runnable) {
            handler.post(runnable)
        }

        fun postDelayed(runnable: Runnable, delayMillis: Long) {
            handler.postDelayed(runnable, delayMillis)
        }
    }

    // ??????????????????
    private lateinit var screenState: ScreenState

    private var lastThemeId = R.style.AppTheme
    private fun setAppTheme(theme: Int) {
        if (lastThemeId != theme) {
            setTheme(theme)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        /*
        try {
            val theme = (if ((newConfig.uiMode and Configuration.UI_MODE_NIGHT_YES) != 0) {
                R.style.AppThemeNight
            } else {
                R.style.AppTheme
            })
            setAppTheme(theme)
        } catch (ex: Exception) {
        }
        */
        nightMode = ((newConfig.uiMode and Configuration.UI_MODE_NIGHT_YES) != 0)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = this
        CrashHandler().init(this)

        /*
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES) {
            setAppTheme(R.style.AppThemeNight)
        }
        */
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES) {
            nightMode = true
        }
        thisPackageName = this.packageName

        // ??????busybox
        if (!Busybox.systemBusyboxInstalled()) {
            ShellExecutor.setExtraEnvPath(
                FileWrite.getPrivateFilePath(this, getString(R.string.toolkit_install_path))
            )
        }

        // ??????????????????
        screenState = ScreenState(this)
        screenState.autoRegister()

        // ??????????????????
        BatteryState(context).registerReceiver()

        // ????????????
        TimingTaskManager(this).updateAlarmManager()

        // ????????????
        EventBus.subscribe(TriggerIEventMonitor(this))

        // ????????????
        EventBus.subscribe(ChargeCurve(this))

        // ???????????????????????????
        EventBus.subscribe(ScreenOffCleanup(context))

        // ????????????????????????????????????root???????????????root????????????
        if (getBoolean("root", false)) {
            CheckRootStatus.checkRootAsync()
        }
    }
}
