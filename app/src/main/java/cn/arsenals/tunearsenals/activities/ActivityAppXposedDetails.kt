package cn.arsenals.tunearsenals.activities

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.store.SpfConfig
import cn.arsenals.store.XposedExtension
import cn.arsenals.ui.IntInputFilter
import cn.arsenals.vaddin.IAppConfigAidlInterface
import cn.arsenals.tunearsenals.R
import cn.arsenals.xposed.XposedCheck
import kotlinx.android.synthetic.main.activity_app_xposed_details.*
import org.json.JSONObject

class ActivityAppXposedDetails : ActivityBase() {
    var app = ""
    lateinit var tunearsenalsConfigInfo: XposedExtension.AppConfig
    lateinit var originConfig: XposedExtension.AppConfig
    private var dynamicCpu: Boolean = false
    private var _result = RESULT_CANCELED
    private var vAddinsInstalled = false
    private var aidlConn: IAppConfigAidlInterface? = null
    private lateinit var spfGlobal: SharedPreferences

    fun getAddinVersion(): Int {
        var code = 0
        try {
            val manager = getPackageManager()
            val info = manager.getPackageInfo("cn.arsenals.vaddin", 0)
            code = info.versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }

        return code
    }

    fun getAddinMinimumVersion(): Int {
        return resources.getInteger(R.integer.addin_minimum_version)
    }

    private var conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aidlConn = IAppConfigAidlInterface.Stub.asInterface(service)
            updateXposedConfigFromAddin()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            aidlConn = null
        }
    }

    private fun installVAddin() {
        DialogHelper.warning(context, getString(R.string.tunearsenals_addin_miss), getString(R.string.tunearsenals_addin_miss_desc), {
            try {
                val uri = Uri.parse("http://tunearsenals.omarea.com/")
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "启动在线页面失败！", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateXposedConfigFromAddin() {
        if (aidlConn != null) {
            try {
                if (getAddinMinimumVersion() > getAddinVersion()) {
                    if (aidlConn != null) {
                        unbindService(conn)
                        aidlConn = null
                    }
                    installVAddin()
                } else {
                    val configJson = aidlConn!!.getStringValue(app, "{}")
                    val config = JSONObject(configJson)
                    for (key in config.keys()) {
                        when (key) {
                            "dpi" -> {
                                tunearsenalsConfigInfo.dpi = config.getInt(key)
                                originConfig.dpi = tunearsenalsConfigInfo.dpi
                            }
                            "excludeRecent" -> {
                                tunearsenalsConfigInfo.excludeRecent = config.getBoolean(key)
                                originConfig.excludeRecent = tunearsenalsConfigInfo.excludeRecent
                            }
                            "smoothScroll" -> {
                                tunearsenalsConfigInfo.smoothScroll = config.getBoolean(key)
                                originConfig.smoothScroll = tunearsenalsConfigInfo.smoothScroll
                            }
                            "webDebug" -> {
                                tunearsenalsConfigInfo.webDebug = config.getBoolean(key)
                                originConfig.webDebug = tunearsenalsConfigInfo.webDebug
                            }
                        }
                    }
                    app_details_scrollopt.isChecked = tunearsenalsConfigInfo.smoothScroll
                    app_details_excludetask.isChecked = tunearsenalsConfigInfo.excludeRecent
                    app_details_web_debug.isChecked = tunearsenalsConfigInfo.webDebug
                    if (tunearsenalsConfigInfo.dpi >= 96) {
                        app_details_dpi.text = tunearsenalsConfigInfo.dpi.toString()
                    } else {
                        app_details_dpi.text = "默认"
                    }
                }
            } catch (ex: Exception) {
                Toast.makeText(applicationContext, getString(R.string.tunearsenals_addin_sync_fail), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindService() {
        tryUnBindAddin()
        try {
            val intent = Intent();
            //绑定服务端的service
            intent.setAction("cn.arsenals.vaddin.ConfigUpdateService");
            //新版本（5.0后）必须显式intent启动 绑定服务
            intent.setComponent(ComponentName("cn.arsenals.vaddin", "cn.arsenals.vaddin.ConfigUpdateService"));
            //绑定的时候服务端自动创建
            if (bindService(intent, conn, Context.BIND_AUTO_CREATE)) {
            } else {
                throw Exception("")
            }
        } catch (ex: Exception) {
            Toast.makeText(applicationContext, "连接到“TuneArsenals-高级设定”插件失败，请不要阻止插件自启动！", Toast.LENGTH_LONG).show()
        }
    }

    private fun tryUnBindAddin() {
        try {
            if (aidlConn != null) {
                unbindService(conn)
                aidlConn = null
            }
        } catch (ex: Exception) {

        }
    }

    /**
     * 检查Xposed状态
     */
    private fun checkXposedState() {
        var allowXposedConfig = XposedCheck.xposedIsRunning()
        app_details_vaddins_notactive.visibility = if (allowXposedConfig) View.GONE else View.VISIBLE
        try {
            vAddinsInstalled = packageManager.getPackageInfo("cn.arsenals.vaddin", 0) != null
            allowXposedConfig = allowXposedConfig && vAddinsInstalled
        } catch (ex: Exception) {
            vAddinsInstalled = false
        }
        app_details_vaddins_notinstall.setOnClickListener {
            installVAddin()
        }
        if (vAddinsInstalled && getAddinVersion() < getAddinMinimumVersion()) {
            installVAddin()
        } else if (vAddinsInstalled) {
            // 已安装（获取配置）
            app_details_vaddins_notinstall.visibility = View.GONE
            if (aidlConn == null) {
                bindService()
            } else {
                updateXposedConfigFromAddin()
            }
        } else {
            // 未安装（显示未安装）
            app_details_vaddins_notinstall.visibility = View.VISIBLE
        }
        app_details_vaddins_notactive.visibility = if (XposedCheck.xposedIsRunning()) View.GONE else View.VISIBLE
        app_details_dpi.isEnabled = allowXposedConfig
        app_details_excludetask.isEnabled = allowXposedConfig
        app_details_scrollopt.isEnabled = allowXposedConfig
        app_details_web_debug.isEnabled = allowXposedConfig
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_xposed_details)

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

        dynamicCpu = spfGlobal.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DEFAULT)

        app_details_icon.setOnClickListener {
            try {
                saveConfig()
                startActivity(getPackageManager().getLaunchIntentForPackage(app))
            } catch (ex: Exception) {
                Toast.makeText(applicationContext, getString(R.string.start_app_fail), Toast.LENGTH_SHORT).show()
            }
        }

        tunearsenalsConfigInfo = XposedExtension.AppConfig(app)
        originConfig = XposedExtension.AppConfig(app)
        if (tunearsenalsConfigInfo.dpi >= 96) {
            app_details_dpi.text = tunearsenalsConfigInfo.dpi.toString()
        }
        app_details_excludetask.setOnClickListener {
            tunearsenalsConfigInfo.excludeRecent = (it as Switch).isChecked
        }
        app_details_scrollopt.setOnClickListener {
            tunearsenalsConfigInfo.smoothScroll = (it as Switch).isChecked
        }
        app_details_web_debug.setOnClickListener {
            tunearsenalsConfigInfo.webDebug = (it as Switch).isChecked
        }

        if (XposedCheck.xposedIsRunning()) {
            if (tunearsenalsConfigInfo.dpi >= 96) {
                app_details_dpi.text = tunearsenalsConfigInfo.dpi.toString()
            } else {
                app_details_dpi.text = "默认"
            }
            app_details_dpi.setOnClickListener {
                var dialog: DialogHelper.DialogWrap? = null
                val view = layoutInflater.inflate(R.layout.dialog_dpi_input, null)
                val inputDpi = view.findViewById<EditText>(R.id.input_dpi).apply {
                    setFilters(arrayOf(IntInputFilter()));
                    if (tunearsenalsConfigInfo.dpi >= 96) {
                        setText(tunearsenalsConfigInfo.dpi.toString())
                    }
                }
                dialog = DialogHelper.confirm(this, "请输入DPI", "", view, DialogHelper.DialogButton(getString(R.string.btn_confirm), {
                    val dpiText = inputDpi.text.toString()
                    if (dpiText.isEmpty()) {
                        tunearsenalsConfigInfo.dpi = 0
                    } else {
                        try {
                            val dpi = dpiText.toInt()
                            if (dpi < 96 && dpi != 0) {
                                Toast.makeText(applicationContext, "DPI的值必须大于96", Toast.LENGTH_SHORT).show()
                            } else {
                                tunearsenalsConfigInfo.dpi = dpi
                                if (dpi == 0) {
                                    app_details_dpi.text = "默认"
                                } else
                                    app_details_dpi.text = dpi.toString()
                                dialog?.dismiss()
                            }
                        } catch (ex: Exception) {
                        }
                    }
                }, false), DialogHelper.DialogButton(getString(R.string.btn_cancel)))
            }
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
        checkXposedState()

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
        try {
            if (
                    tunearsenalsConfigInfo.dpi != originConfig.dpi ||
                    tunearsenalsConfigInfo.excludeRecent != originConfig.excludeRecent ||
                    tunearsenalsConfigInfo.smoothScroll != originConfig.smoothScroll ||
                    tunearsenalsConfigInfo.webDebug != originConfig.webDebug
            ) {
                setResult(RESULT_OK, this.intent)
            } else {
                setResult(_result, this.intent)
            }
            if (aidlConn != null) {
                try {
                    val config = JSONObject().apply {
                        put("dpi", tunearsenalsConfigInfo.dpi)
                        put("excludeRecent", tunearsenalsConfigInfo.excludeRecent)
                        put("smoothScroll", tunearsenalsConfigInfo.smoothScroll)
                        put("webDebug", tunearsenalsConfigInfo.webDebug)
                    }.toString(0)

                    aidlConn!!.run {
                        setStringValue(tunearsenalsConfigInfo.packageName, config)
                    }
                } catch (ex: java.lang.Exception) {
                }
            }
        } catch (ex: Exception) {
        }
    }

    override fun finish() {
        super.finish()
        tryUnBindAddin()
    }

    override fun onPause() {
        super.onPause()
    }
}
