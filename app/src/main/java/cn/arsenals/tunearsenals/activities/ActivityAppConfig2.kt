package cn.arsenals.tunearsenals.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import cn.arsenals.TuneArsenals
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.common.ui.OverScrollListView
import cn.arsenals.common.ui.ProgressBarDialog
import cn.arsenals.data.EventBus
import cn.arsenals.data.EventType
import cn.arsenals.model.AppInfo
import cn.arsenals.store.SpfConfig
import cn.arsenals.store.TuneArsenalsConfigStore
import cn.arsenals.tunearsenals.R
import cn.arsenals.tunearsenals.dialogs.DialogAppOrientation
import cn.arsenals.tunearsenals.dialogs.DialogAppPowerConfig
import cn.arsenals.tunearsenals_mode.ModeSwitcher
import cn.arsenals.ui.TuneArsenalsModeAdapter
import cn.arsenals.utils.AppListHelper
import kotlinx.android.synthetic.main.activity_app_config2.*
import java.util.*


class ActivityAppConfig2 : ActivityBase() {
    private lateinit var processBarDialog: ProgressBarDialog
    private lateinit var spfPowercfg: SharedPreferences
    private lateinit var globalSPF: SharedPreferences
    private lateinit var applistHelper: AppListHelper
    private var installedList: ArrayList<AppInfo>? = null
    private var displayList: ArrayList<AppInfo>? = null
    private lateinit var tunearsenalsConfigStore: TuneArsenalsConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_config2)

        setBackArrow()
        globalSPF = context.getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)

        this.onViewCreated()
    }

    private lateinit var modeSwitcher: ModeSwitcher

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.tunearsenals_apps, menu)
        return true
    }

    //???????????????
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_reset -> {
                DialogHelper.confirm(this, "???????????????", "?????????????????????????????????????????????????????????????????????????????????????????????(cgroup)?????????????????????????????????????????????", {
                    tunearsenalsConfigStore.resetAll()
                    spfPowercfg.all.clear()
                    initDefaultConfig()
                    recreate()
                })
            }
        }
        return true
    }

    private fun onViewCreated() {
        modeSwitcher = ModeSwitcher()
        processBarDialog = ProgressBarDialog(this)
        applistHelper = AppListHelper(this, false)
        spfPowercfg = getSharedPreferences(SpfConfig.POWER_CONFIG_SPF, Context.MODE_PRIVATE)
        globalSPF = getSharedPreferences(SpfConfig.GLOBAL_SPF, Context.MODE_PRIVATE)
        tunearsenalsConfigStore = TuneArsenalsConfigStore(this.context)

        if (spfPowercfg.all.isEmpty()) {
            initDefaultConfig()
        }


        tunearsenals_app_list.setOnItemClickListener { parent, view2, position, _ ->
            try {
                val item = (parent.adapter.getItem(position) as AppInfo)
                val intent = Intent(this.context, ActivityAppDetails::class.java)
                intent.putExtra("app", item.packageName)
                startActivityForResult(intent, REQUEST_APP_CONFIG)
                lastClickRow = view2
            } catch (ex: Exception) {
            }
        }

        // ??????????????????
        val dynamicControl = globalSPF.getBoolean(SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL, SpfConfig.GLOBAL_SPF_DYNAMIC_CONTROL_DEFAULT)

        if (dynamicControl) {
            tunearsenals_app_list.setOnItemLongClickListener { parent, view, position, id ->
                val item = (parent.adapter.getItem(position) as AppInfo)
                val app = item.packageName.toString()
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

                                setAppRowDesc(item)
                                (parent.adapter as TuneArsenalsModeAdapter).updateRow(position, view)
                                notifyService(app, "" + mode)
                            }
                        }).show()
                true
            }
        } else {
            tunearsenals_app_list.setOnItemLongClickListener { _, _, _, _ ->
                DialogHelper.helpInfo(this, "", "????????????????????????????????? [????????????] ??????????????? [????????????] ??????")
                true
            }
        }

        config_search_box.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                loadList()
                return@setOnEditorActionListener true
            }
            false
        }

        configlist_modes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadList()
            }
        }
        configlist_type.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadList()
            }
        }

        loadList()
    }

    private val REQUEST_APP_CONFIG = 0
    private var lastClickRow: View? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_APP_CONFIG && data != null && displayList != null) {
            try {
                if (resultCode == AppCompatActivity.RESULT_OK) {
                    val adapter = (tunearsenals_app_list.adapter as TuneArsenalsModeAdapter)
                    var index = -1
                    val packageName = data.extras!!.getString("app")
                    for (i in 0 until displayList!!.size) {
                        if (displayList!![i].packageName == packageName) {
                            index = i
                        }
                    }
                    if (index < 0) {
                        return
                    }
                    val item = adapter.getItem(index)
                    setAppRowDesc(item)
                    (tunearsenals_app_list.adapter as TuneArsenalsModeAdapter?)?.run {
                        updateRow(index, lastClickRow!!)
                    }
                    //loadList(false)
                }
            } catch (ex: Exception) {
                Log.e("update-list", "" + ex.message)
            }
        }
    }

    // ??????????????????????????????
    private fun notifyService(app: String, mode: String) {
        EventBus.publish(EventType.SCENE_APP_CONFIG, HashMap<String, Any>().apply {
            put("app", app)
            put("mode", mode)
        })
    }

    private fun initDefaultConfig() {
        for (item in resources.getStringArray(R.array.powercfg_igoned)) {
            spfPowercfg.edit().putString(item, ModeSwitcher.IGONED).apply()
        }
        for (item in resources.getStringArray(R.array.powercfg_fast)) {
            spfPowercfg.edit().putString(item, ModeSwitcher.FAST).apply()
        }
        for (item in resources.getStringArray(R.array.powercfg_game)) {
            spfPowercfg.edit().putString(item, ModeSwitcher.PERFORMANCE).apply()
        }
        for (item in context.resources.getStringArray(R.array.powercfg_powersave)) {
            spfPowercfg.edit().putString(item, ModeSwitcher.POWERSAVE).apply()
        }
    }

    private fun sortAppList(list: ArrayList<AppInfo>): ArrayList<AppInfo> {
        list.sortWith { l, r ->
            try {
                val les = l.stateTags.toString()
                val res = r.stateTags.toString()
                when {
                    les < res -> -1
                    les > res -> 1
                    else -> {
                        val lp = l.packageName.toString()
                        val rp = r.packageName.toString()
                        when {
                            lp < rp -> -1
                            lp > rp -> 1
                            else -> 0
                        }
                    }
                }
            } catch (ex: Exception) {
                0
            }
        }
        return list
    }

    private fun setListData(dl: ArrayList<AppInfo>?, lv: OverScrollListView) {
        TuneArsenals.post {
            lv.adapter = TuneArsenalsModeAdapter(
                    this,
                    dl!!,
                    globalSPF.getString(SpfConfig.GLOBAL_SPF_POWERCFG_FIRST_MODE, ModeSwitcher.DEFAULT)!!
            )
            processBarDialog.hideDialog()
        }
    }

    private var onLoading = false

    @SuppressLint("ApplySharedPref")
    private fun loadList(foreceReload: Boolean = false) {
        if (onLoading) {
            return
        }
        processBarDialog.showDialog()

        Thread(Runnable {
            onLoading = true
            if (foreceReload || installedList == null || installedList!!.size == 0) {
                installedList = ArrayList()/*????????????????????????*/
                installedList = applistHelper.getAll()
            }
            if (config_search_box == null) {
                TuneArsenals.post {
                    processBarDialog.hideDialog()
                }
                return@Runnable
            }
            val keyword = config_search_box.text.toString().toLowerCase(Locale.getDefault())
            val search = keyword.isNotEmpty()
            var filterMode = ""
            var filterAppType = ""
            when (configlist_type.selectedItemPosition) {
                0 -> filterAppType = "/data"
                1 -> filterAppType = "/system"
                2 -> filterAppType = "*"
            }
            when (configlist_modes.selectedItemPosition) {
                0 -> filterMode = "*"
                1 -> filterMode = ModeSwitcher.POWERSAVE
                2 -> filterMode = ModeSwitcher.BALANCE
                3 -> filterMode = ModeSwitcher.PERFORMANCE
                4 -> filterMode = ModeSwitcher.FAST
                5 -> filterMode = ""
                6 -> filterMode = ModeSwitcher.IGONED
            }
            displayList = ArrayList()
            for (i in installedList!!.indices) {
                val item = installedList!![i]
                setAppRowDesc(item)
                val packageName = item.packageName.toString()
                if (search && !(packageName.toLowerCase(Locale.getDefault()).contains(keyword) || item.appName.toString().toLowerCase(Locale.getDefault()).contains(keyword))) {
                    continue
                } else {
                    if (filterMode == "*" || filterMode == spfPowercfg.getString(packageName, "")) {
                        if (filterAppType == "*" || item.path.startsWith(filterAppType)) {
                            displayList!!.add(item)
                        }
                    }
                }
            }
            sortAppList(displayList!!)
            TuneArsenals.post {
                processBarDialog.hideDialog()
                setListData(displayList, tunearsenals_app_list)
            }
            onLoading = false
        }).start()
    }

    private fun setAppRowDesc(item: AppInfo) {
        item.selected = false
        val packageName = item.packageName.toString()
        item.stateTags = spfPowercfg.getString(packageName, "")
        val configInfo = tunearsenalsConfigStore.getAppConfig(packageName)
        item.tunearsenalsConfigInfo = configInfo
        val desc = StringBuilder()
        if (configInfo.aloneLight) {
            desc.append("???????????? ")
        }
        if (configInfo.disNotice) {
            desc.append("????????????  ")
        }
        if (configInfo.disButton) {
            desc.append("????????????  ")
        }
        if (configInfo.freeze) {
            desc.append("????????????  ")
        }
        if (configInfo.gpsOn) {
            desc.append("??????GPS  ")
        }
        if (configInfo.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            DialogAppOrientation.Transform(this).getName(configInfo.screenOrientation).run {
                if (isNotEmpty()) {
                    desc.append(this)
                    desc.append("  ")
                }
            }
        }
        item.desc = desc.toString()
    }

    override fun onDestroy() {
        processBarDialog.hideDialog()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_app_tunearsenals)
    }
}
