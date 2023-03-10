package cn.arsenals.tunearsenals.activities

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.CompoundButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import cn.arsenals.TuneArsenals
import cn.arsenals.common.model.SelectItem
import cn.arsenals.common.shared.MagiskExtend
import cn.arsenals.common.shell.KeepShellPublic
import cn.arsenals.common.shell.KernelProrp
import cn.arsenals.common.shell.RootFile
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.common.ui.DialogItemChooserMini
import cn.arsenals.common.ui.ProgressBarDialog
import cn.arsenals.library.basic.RadioGroupSimulator
import cn.arsenals.library.shell.LMKUtils
import cn.arsenals.library.shell.PropsUtils
import cn.arsenals.library.shell.SwapModuleUtils
import cn.arsenals.library.shell.SwapUtils
import cn.arsenals.store.SpfConfig
import cn.arsenals.tunearsenals.R
import cn.arsenals.ui.AdapterSwaplist
import kotlinx.android.synthetic.main.activity_swap.*
import java.util.*


class ActivitySwap : ActivityBase() {
    private lateinit var processBarDialog: ProgressBarDialog
    private val myHandler = Handler(Looper.getMainLooper())
    private lateinit var swapConfig: SharedPreferences
    private var totalMem = 2048
    private val swapUtils = SwapUtils(TuneArsenals.context)
    private val swapModuleUtils = SwapModuleUtils()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_swap)
        setBackArrow()

        swapConfig = getSharedPreferences(SpfConfig.SWAP_SPF, Context.MODE_PRIVATE)

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)

        totalMem = (info.totalMem / 1024 / 1024f).toInt()

        // ??????????????? ??????Magisk???????????????
        swapModuleUtils.loadModuleConfig(swapConfig)

        setView()
    }

    private fun swapOffAwait(): Timer {
        val timer = Timer()
        val totalUsed = swapUtils.swapUsedSize
        val startTime = System.currentTimeMillis()
        timer.schedule(object : TimerTask() {
            override fun run() {
                val currentUsed = swapUtils.swapUsedSize
                val avgSpeed = (totalUsed - currentUsed).toFloat() / (System.currentTimeMillis() - startTime) * 1000
                val tipStr = StringBuilder()
                tipStr.append(String.format("??????Swapfile ${currentUsed}/${totalUsed}MB (%.1fMB/s)\n", avgSpeed))
                if (avgSpeed > 0) {
                    tipStr.append("???????????? " + (currentUsed / avgSpeed).toInt() + "???")
                } else {
                    tipStr.append("???????????????~")
                }
                myHandler.post {
                    processBarDialog.showDialog(tipStr.toString())
                }
            }
        }, 0, 1000)
        return timer
    }

    private fun zramOffAwait(): Timer {
        val timer = Timer()
        val totalUsed = swapUtils.zramUsedSize
        val startTime = System.currentTimeMillis()
        timer.schedule(object : TimerTask() {
            override fun run() {
                val currentUsed = swapUtils.zramUsedSize
                val avgSpeed = (totalUsed - currentUsed).toFloat() / (System.currentTimeMillis() - startTime) * 1000
                val tipStr = StringBuilder()
                tipStr.append(String.format("??????ZRAM ${currentUsed}/${totalUsed}MB (%.1fMB/s)\n", avgSpeed))
                if (avgSpeed > 0) {
                    tipStr.append("???????????? " + (currentUsed / avgSpeed).toInt() + "???")
                } else {
                    tipStr.append("???????????????~")
                }
                myHandler.post {
                    processBarDialog.showDialog(tipStr.toString())
                }
            }
        }, 0, 1000)
        return timer
    }

    private fun setView() {
        val context = this
        processBarDialog = ProgressBarDialog(context)

        if (swapModuleUtils.magiskModuleInstalled) {
            swap_module_installed.visibility = View.VISIBLE
            swap_module_uninstalled.visibility = View.GONE
        } else {
            swap_module_installed.visibility = View.GONE
            swap_module_uninstalled.visibility = View.VISIBLE
        }

        if (MagiskExtend.magiskSupported()) {
            val currentVersion = swapModuleUtils.getModuleVersion()
            if (currentVersion < getString(R.string.swap_module_target_version).toInt()) {
                swap_module_downloadable.visibility = View.VISIBLE
                swap_module_downloadable.setOnClickListener {
                    swapModuleUpdateDialog()
                }
            } else {
                swap_module_downloadable.visibility = View.GONE
            }
        }

        // ??????swap
        btn_swap_close.setOnClickListener {
            val usedSize = swapUtils.swapUsedSize
            if (usedSize > 500) {
                DialogHelper.confirm(this,
                        "?????????????????????",
                        "Swap???????????????(${usedSize}MB)????????????????????????????????????\n?????????????????????????????????????????????????????????????????????????????????????????????", {
                    swapConfig.edit().putBoolean(SpfConfig.SWAP_SPF_SWAP, false).apply()
                    swapModuleUtils.saveModuleConfig(swapConfig)
                    KeepShellPublic.doCmdSync("sync\nsleep 2\nsvc power reboot || reboot")
                })
            } else {
                swapOffDialog()
            }
        }

        // ??????lmk??????
        swap_auto_lmk.setOnClickListener {
            val checked = (it as CompoundButton).isChecked
            swapConfig.edit().putBoolean(SpfConfig.SWAP_SPF_AUTO_LMK, checked).apply()
            if (checked) {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(info)
                val utils = LMKUtils()
                utils.autoSetLMK(info.totalMem)
                swap_lmk_current.text = utils.getCurrent()
            } else {
                Toast.makeText(context, "???????????????????????????????????????LMK?????????", Toast.LENGTH_SHORT).show()
            }
        }

        // ????????????zram
        if (!swapUtils.zramSupport) {
            swap_config_zram.visibility = View.GONE
            zram_stat.visibility = View.GONE
        }

        // swap??????
        btn_swap_create.setOnClickListener {
            swapCreateDialog()
        }

        // ??????zram????????????
        btn_zram_resize.setOnClickListener {
            zramResizeDialog()
        }

        swappiness_adj.setOnClickListener {
            swappinessAdjDialog()
        }
    }

    // ?????????????????????
    private fun swapModuleUpdateDialog () {
        val view = layoutInflater.inflate(R.layout.dialog_swap_module, null)
        val dialog = DialogHelper.customDialog(this, view)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            try {
                val intent = Intent()
                intent.action = Intent.ACTION_VIEW
                intent.data = Uri.parse(getString(R.string.swap_module_download_url))
                context.startActivity(intent)
            } catch (ex: java.lang.Exception) {
                Toast.makeText(context, "?????????????????????", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun swapOffDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_swap_delete, null)
        val deleteFile = view.findViewById<CompoundButton>(R.id.swap_delete_file)
        val dialog = DialogHelper.customDialog(this, view)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
            val delete = deleteFile.isChecked

            processBarDialog.showDialog(getString(R.string.swap_on_close))
            val run = Runnable {
                val timer = swapOffAwait()

                if (delete) {
                    swapUtils.swapDelete()
                } else {
                    swapUtils.swapOff()
                }

                timer.cancel()
                myHandler.post {
                    swapConfig.edit().putBoolean(SpfConfig.SWAP_SPF_SWAP, false).apply()
                    processBarDialog.hideDialog()
                    getSwaps()
                }
            }
            Thread(run).start()
        }
    }

    private var timer: Timer? = null
    private fun startTimer() {
        stopTimer()

        timer = Timer()
        timer!!.schedule(object : TimerTask() {
            override fun run() {
                getSwaps()
            }
        }, 0, 5000)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    private fun swappinessAdjDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_swappines, null)

        val swappinessSeekBar = view.findViewById<SeekBar>(R.id.seekbar_swap_swappiness)
        val swappinessText = view.findViewById<TextView>(R.id.txt_zramstus_swappiness)
        val extraFreeSeekBar = view.findViewById<SeekBar>(R.id.seekbar_extra_free_kbytes)
        val extraFreeText = view.findViewById<TextView>(R.id.text_extra_free_kbytes)
        val watermarkScaleSeekBar = view.findViewById<SeekBar>(R.id.seekbar_watermark_scale_factor)
        val watermarkScaleText = view.findViewById<TextView>(R.id.text_watermark_scale_factor)


        swappinessSeekBar.progress = swapConfig.getInt(SpfConfig.SWAP_SPF_SWAPPINESS, 65)
        swappinessText.text = swappinessSeekBar.progress.toString()

        val extraFreeKbytes = KernelProrp.getProp("/proc/sys/vm/extra_free_kbytes")
        try {
            val bytes = extraFreeKbytes.toInt()
            extraFreeSeekBar.progress = swapConfig.getInt(SpfConfig.SWAP_SPF_EXTRA_FREE_KBYTES, bytes)
        } catch (ex: Exception) {
            extraFreeSeekBar.progress = swapConfig.getInt(SpfConfig.SWAP_SPF_EXTRA_FREE_KBYTES, 29615)
        }

        extraFreeText.text = extraFreeSeekBar.progress.toString() + "(" + (extraFreeSeekBar.progress / 1024) + "MB)"

        watermarkScaleSeekBar.progress = swapConfig.getInt(SpfConfig.SWAP_SPF_WATERMARK_SCALE, 100)
        watermarkScaleText.text = watermarkScaleSeekBar.progress.run {
            "$this(${this / 100F}%)"
        }

        swappinessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                swappinessText.text = p1.toString()
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        // extra_free_kbytes??????
        extraFreeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                extraFreeText.text = p1.toString() + "(" + (p1 / 1024) + "MB)"
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        watermarkScaleSeekBar.isEnabled = RootFile.fileExists("/proc/sys/vm/watermark_scale_factor")
        watermarkScaleSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                watermarkScaleText.text = p1.run {
                    "$p1(${p1 / 100F}%)"
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
            }
        })

        val dialog = DialogHelper.customDialog(this, view)
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            val swappiness = swappinessSeekBar.progress
            val extraFree = extraFreeSeekBar.progress
            val watermarkScale = watermarkScaleSeekBar.progress


            val config = swapConfig.edit()
                    .putInt(SpfConfig.SWAP_SPF_SWAPPINESS, swappiness)
                    .putInt(SpfConfig.SWAP_SPF_EXTRA_FREE_KBYTES, extraFree)

            KeepShellPublic.doCmdSync("echo $swappiness > /proc/sys/vm/swappiness")
            KeepShellPublic.doCmdSync("echo $extraFree > /proc/sys/vm/extra_free_kbytes")
            if (watermarkScaleSeekBar.isEnabled) {
                KeepShellPublic.doCmdSync("echo $watermarkScale > /proc/sys/vm/watermark_scale_factor")

                config.putInt(SpfConfig.SWAP_SPF_WATERMARK_SCALE, watermarkScale)
            }
            config.apply()

            myHandler.post {
                getSwaps()
            }
        }
    }

    private fun zramResizeDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_zram_resize, null)
        val zramSizeBar = view.findViewById<SeekBar>(R.id.zram_size)
        val zramAutoStart = view.findViewById<CompoundButton>(R.id.zram_auto_start)
        val compactAlgorithm = view.findViewById<TextView>(R.id.zram_compact_algorithm)
        val zramSizeText = view.findViewById<TextView>(R.id.zram_size_text)

        zramAutoStart.isChecked = swapConfig.getBoolean(SpfConfig.SWAP_SPF_ZRAM, false)
        val compAlgorithmOptions = swapUtils.compAlgorithmOptions
        var currentAlgorithm = swapConfig.getString(SpfConfig.SWAP_SPF_ALGORITHM, swapUtils.compAlgorithm)
        if (!compAlgorithmOptions.contains(currentAlgorithm!!)) {
            currentAlgorithm = swapUtils.compAlgorithm
            swapConfig.edit().putString(SpfConfig.SWAP_SPF_ALGORITHM, currentAlgorithm).apply()
        }
        compactAlgorithm.text = currentAlgorithm
        compactAlgorithm.setOnClickListener {
            DialogItemChooserMini
                    .singleChooser(context, compAlgorithmOptions, compAlgorithmOptions.indexOf(currentAlgorithm))
                    .setTitle(R.string.swap_zram_comp_options)
                    .setCallback(object : DialogItemChooserMini.Callback {
                        override fun onConfirm(selected: List<SelectItem>, status: BooleanArray) {
                            val algorithm = selected.firstOrNull()?.value
                            algorithm?.run {
                                (it as TextView).text = algorithm
                            }
                        }
                    })
                    .show()
        }

        zramSizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                zramSizeText.text = (progress * 128).toString() + "MB"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        zramSizeBar.max = totalMem / 128
        var zramSize = swapConfig.getInt(SpfConfig.SWAP_SPF_ZRAM_SIZE, 0)
        if (zramSize > totalMem)
            zramSize = totalMem
        zramSizeBar.progress = zramSize / 128

        val dialog = DialogHelper.customDialog(this, view)
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            val sizeVal = zramSizeBar.progress * 128
            val autoStart = zramAutoStart.isChecked
            val algorithm = "" + compactAlgorithm.text

            processBarDialog.showDialog(getString(R.string.zram_resizing))
            swapConfig.edit()
                    .putInt(SpfConfig.SWAP_SPF_ZRAM_SIZE, sizeVal)
                    .putBoolean(SpfConfig.SWAP_SPF_ZRAM, autoStart)
                    .putString(SpfConfig.SWAP_SPF_ALGORITHM, algorithm)
                    .apply()

            val run = Thread {
                if (swapUtils.zramEnabled && algorithm != swapUtils.compAlgorithm || sizeVal != swapUtils.zramCurrentSizeMB) {
                    val timer = zramOffAwait()
                    swapUtils.zramOff()
                    timer.cancel()
                }
                myHandler.post {
                    processBarDialog.showDialog(getString(R.string.zram_resizing))
                }
                swapUtils.resizeZram(sizeVal, algorithm)

                getSwaps()
                myHandler.post {
                    processBarDialog.hideDialog()
                }
            }
            Thread(run).start()
        }
    }

    private fun swapCreateDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_swap_create, null)
        val swapSize = view.findViewById<SeekBar>(R.id.swap_size)
        val swapSizeText = view.findViewById<TextView>(R.id.swap_size_text)

        val dialog = DialogHelper.customDialog(this, view)

        swapSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                swapSizeText.text = (progress * 128).toString() + "MB"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })

        var swapCurrentSize = 0
        if (swapUtils.swapExists) {
            swapCurrentSize = swapUtils.swapFileSize
        } else {
            swapCurrentSize = swapConfig.getInt(SpfConfig.SWAP_SPF_SWAP_SWAPSIZE, 0)
        }

        swapSize.progress = swapCurrentSize / 128
        swapSizeText.text = (swapSize.progress * 128).toString() + "MB"

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            val size = swapSize.progress * 128
            if (size < 1) {
                TuneArsenals.toast("????????????SWAP?????????")
                return@setOnClickListener
            } else if (size == swapUtils.swapFileSize) {
                // ?????????????????????????????????????????????????????????

                // ????????????
                swapConfig.edit().putInt(SpfConfig.SWAP_SPF_SWAP_SWAPSIZE, size).apply()

                swapActiveDialog()
            } else {
                val run = Runnable {
                    val startTime = System.currentTimeMillis()
                    myHandler.post {
                        processBarDialog.showDialog(getString(R.string.file_creating))
                    }
                    swapUtils.mkswap(size)

                    // ????????????
                    swapConfig.edit().putInt(SpfConfig.SWAP_SPF_SWAP_SWAPSIZE, size).apply()

                    getSwaps()
                    val time = System.currentTimeMillis() - startTime
                    myHandler.post {
                        processBarDialog.hideDialog()
                        val speed = (size * 1000.0 / time).toInt()
                        Toast.makeText(
                                context,
                                "Swapfile?????????????????????${time / 1000}s????????????????????????${speed}MB/s",
                                Toast.LENGTH_LONG
                        ).show()
                        swapActiveDialog()
                    }
                }
                Thread(run).start()
            }
        }
    }

    private fun swapActiveDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_swap_active, null)
        val dialog = DialogHelper.customDialog(this, view)

        val priorityHight = view.findViewById<CompoundButton>(R.id.swap_priority_high)
        val priorityMiddle = view.findViewById<CompoundButton>(R.id.swap_priority_middle)
        val priorityLow = view.findViewById<CompoundButton>(R.id.swap_priority_low)
        val autoStart = view.findViewById<CompoundButton>(R.id.swap_auto_start)
        val mountLoop = view.findViewById<CompoundButton>(R.id.swap_mount_loop)

        // ??????????????????
        val radioGroupSimulator = RadioGroupSimulator(priorityHight, priorityMiddle, priorityLow)
        when (swapConfig.getInt(SpfConfig.SWAP_SPF_SWAP_PRIORITY, -2)) {
            5 -> priorityHight.isChecked = true
            0 -> priorityMiddle.isChecked = true
            else -> priorityLow.isChecked = true
        }
        mountLoop.isChecked = swapConfig.getBoolean(SpfConfig.SWAP_SPF_SWAP_USE_LOOP, false)
        autoStart.isChecked = swapConfig.getBoolean(SpfConfig.SWAP_SPF_SWAP, false)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            val priority: Int
            when (radioGroupSimulator.checked) {
                priorityHight -> {
                    priority = 5
                }
                priorityMiddle -> {
                    priority = 0
                }
                priorityLow -> {
                    priority = -2
                }
                else -> {
                    return@setOnClickListener
                }
            }
            // ????????????
            swapConfig.edit()
                    .putBoolean(SpfConfig.SWAP_SPF_SWAP_USE_LOOP, mountLoop.isChecked)
                    .putInt(SpfConfig.SWAP_SPF_SWAP_PRIORITY, priority)
                    .putBoolean(SpfConfig.SWAP_SPF_SWAP, autoStart.isChecked)
                    .apply()

            processBarDialog.showDialog("??????...")
            Thread {
                val swapPriority = swapConfig.getInt(SpfConfig.SWAP_SPF_SWAP_PRIORITY, -2)
                if (swapPriority == 0) {
                    val zramPriority = swapUtils.zramPriority
                    if (zramPriority != null && zramPriority < 0) {
                        val timer = zramOffAwait()
                        swapUtils.zramOff()
                        timer.cancel()
                    }
                }
                val result = swapUtils.swapOn(
                        swapPriority,
                        swapConfig.getBoolean(SpfConfig.SWAP_SPF_SWAP_USE_LOOP, false)
                )
                if (result.isNotEmpty()) {
                    TuneArsenals.toast(result, Toast.LENGTH_LONG)
                }

                getSwaps()

                myHandler.post(showSwapOpened)
                processBarDialog.hideDialog()
            }.start()
        }
    }

    private var swapsTHRowCache: LinkedHashMap<String, String>? = null
    private val swapsTHRow: LinkedHashMap<String, String>
        get() {
            if (swapsTHRowCache == null) {
                swapsTHRowCache = LinkedHashMap<String, String>().apply {
                    put("path", getString(R.string.path))
                    put("type", getString(R.string.type))
                    put("size", getString(R.string.size))
                    put("used", getString(R.string.used))
                    put("priority", getString(R.string.order)) // put("priority", getString(R.string.priority))
                }
            }
            return swapsTHRowCache!!
        }

    internal val getSwaps = {
        val zramEnabled = swapUtils.zramEnabled
        val swappiness = KernelProrp.getProp("/proc/sys/vm/swappiness")
        val watermarkScale = KernelProrp.getProp("/proc/sys/vm/watermark_scale_factor")
        val memInfo = KernelProrp.getProp("/proc/meminfo")
        val swapFileExists = swapUtils.swapExists
        val currentSwap = swapUtils.tunearsenalsSwaps
        val rows = swapUtils.procSwaps
        val extraFreeKbytes = KernelProrp.getProp("/proc/sys/vm/extra_free_kbytes")
        // ????????????
        val compAlgorithm = swapUtils.compAlgorithm
        // zram??????
        val zramStatus = getZRamStatus(compAlgorithm)
        val swapFileSize = swapUtils.swapFileSize

        var loopName: String? = PropsUtils.getProp("tunearsenals.swap.loop").split("/").lastOrNull()
        if (loopName != null && !loopName.contains("loop")) {
            loopName = null
        }

        val list = ArrayList<HashMap<String, String>>()
        list.add(swapsTHRow)

        var swapSize = 0f
        var swapFree = 0f
        // ?????????ZRAM???????????????????????????????????????????????????????????????????????????????????????????????????????????????0?????????
        val zramSize = if (zramEnabled) swapUtils.zramCurrentSizeMB else 0
        var zramFree = 0f
        for (i in 1 until rows.size) {
            val tr = LinkedHashMap<String, String>()
            val params = rows[i].split(" ").toMutableList()
            val path = params[0]
            tr["path"] = path
            tr["type"] = params[1].replace("file", "??????").replace("partition", "??????")

            val size = swapUsedSizeParseMB(params[2])
            // tr.put("size", if (size.length > 3) (size.substring(0, size.length - 3) + "m") else "0")
            tr["size"] = size

            val used = swapUsedSizeParseMB(params[3])
            // tr.put("used", if (used.length > 3) (used.substring(0, used.length - 3) + "m") else "0")
            tr["used"] = used

            tr["priority"] = params[4]
            list.add(tr)

            if (path.startsWith("/swapfile") || path.equals("/data/swapfile") || (loopName != null && path.contains(loopName))) {
                try {
                    swapSize = size.toFloat()
                    swapFree = size.toFloat() - used.toFloat()
                } catch (ex: java.lang.Exception) {
                }
            } else if (path.startsWith("/block/zram0") || path.startsWith("/dev/block/zram0")) {
                try {
                    // zramSize = size.toFloat()
                    zramFree = size.toFloat() - used.toFloat()
                } catch (ex: java.lang.Exception) {
                }
            }
        }
        val swaps = AdapterSwaplist(this, list)

        myHandler.post {
            try {
                txt_swap_size_display.text = swapFileSize.toString() + "MB"
                swap_usage.setData(swapSize, swapFree)
                zram_usage.setData(zramSize.toFloat(), zramFree)
                if (swapSize > 0) {
                    swap_usage_ratio.text = (100 - (swapFree * 100 / swapSize).toInt()).toString() + "%"
                } else {
                    swap_usage_ratio.text = "0%"
                }
                if (zramSize > 0 && zramFree > 0) {
                    zram_usage_ratio.text = (100 - (zramFree * 100 / zramSize).toInt()).toString() + "%"
                } else {
                    zram_usage_ratio.text = "0%"
                }

                swap_swappiness_display.text = swappiness
                watermark_scale_factor_display.text = watermarkScale

                list_swaps.adapter = swaps

                txt_mem.text = memInfo

                if (currentSwap.isNotEmpty()) {
                    btn_swap_close.visibility = View.VISIBLE
                    btn_swap_create.visibility = View.GONE
                    swap_state.text = getString(R.string.swap_state_using)
                } else {
                    btn_swap_close.visibility = View.GONE
                    btn_swap_create.visibility = View.VISIBLE
                    if (swapFileExists) {
                        swap_state.text = getString(R.string.swap_state_created)
                    } else {
                        swap_state.text = getString(R.string.swap_state_undefined)
                    }
                }

                if (zramEnabled) {
                    zram_state.text = getString(R.string.swap_state_using)
                } else {
                    zram_state.text = getString(R.string.swap_state_created)
                }

                swap_auto_lmk.isChecked = swapConfig.getBoolean(SpfConfig.SWAP_SPF_AUTO_LMK, false)
                val lmkUtils = LMKUtils()
                if (lmkUtils.supported() && !swapModuleUtils.magiskModuleInstalled) {
                    swap_lmk_current.text = lmkUtils.getCurrent()
                    swap_auto_lmk_wrap.visibility = View.VISIBLE
                } else {
                    swap_auto_lmk_wrap.visibility = View.GONE
                }

                extra_free_kbytes_display.text = extraFreeKbytes

                zram0_stat.text = zramStatus
                txt_swap_io.text = vmStat

                txt_zram_size_display.text = "${zramSize}MB"

                zram_compact_algorithm.text = compAlgorithm

                if (currentSwap.isNotEmpty()) {
                    txt_swap_auto_start.text = if (swapConfig.getBoolean(SpfConfig.SWAP_SPF_SWAP, false)) "???????????????????????????" else "???????????????"
                } else {
                    txt_swap_auto_start.text = "--"
                }
                txt_zram_auto_start.text = if (swapConfig.getBoolean(SpfConfig.SWAP_SPF_ZRAM, false)) "???????????????????????????" else "???????????????????????????"
            } catch (ex: java.lang.Exception) {
            }
        }
    }

    private val vmStat: String
        get() {
            val vmstat = KernelProrp.getProp("/proc/vmstat")
            vmstat.run {
                val text = StringBuilder()
                try {
                    var prop = ""
                    var value = ""
                    for (row in split("\n")) {
                        if (row.startsWith("pswpin")) {
                            prop = "???SWAP?????????"
                        } else if (row.startsWith("pswpout")) {
                            prop = "?????????SWAP???"
                        } else {
                            continue
                        }
                        value = row.split(" ")[1]
                        text.append(prop)
                        val mb = (value.toLong() * 4 / 1024)
                        if (mb > 10240) {
                            text.append(String.format("%.2f", (mb / 1024f)))
                            text.append("GB\n")
                        } else {
                            text.append(mb)
                            text.append("MB\n")
                        }
                    }
                } catch (ex: Exception) {
                }

                return text.toString().trim()
            }
        }

    private fun getZRamStatus(compAlgorithm: String): String {
        return if (RootFile.fileExists("/proc/zraminfo")) {
            KernelProrp.getProp("/proc/zraminfo")
        } else {
            // ???????????????
            // val max_comp_streams = KernelProrp.getProp("/sys/block/zram0/max_comp_streams")
            // ?????????????????????????????????????????????
            var origDataSize = KernelProrp.getProp("/sys/block/zram0/orig_data_size")
            // ??????????????????????????????????????????
            var comprDataSize = KernelProrp.getProp("/sys/block/zram0/compr_data_size")
            if (origDataSize.isBlank() || comprDataSize.isBlank()) {
                val mmStat = KernelProrp.getProp("/sys/block/zram0/mm_stat").split("[ ]+".toRegex())
                if (mmStat.size > 1) {
                    origDataSize = mmStat[0]
                    comprDataSize = mmStat[1]
                }
            }

            // ??????????????????????????????
            val memUsedTotal = KernelProrp.getProp("/sys/block/zram0/mem_used_total")

            val zramWriteBackStat = if (swapUtils.zramWriteBackSupport) swapUtils.writeBackStat else null

            val generalStats = if (memUsedTotal.length > 0) {
                // ?????????????????????????????????
                val memLimit = KernelProrp.getProp("/sys/block/zram0/mem_limit")
                // ????????????????????????
                val memUsedMax = KernelProrp.getProp("/sys/block/zram0/mem_used_max")
                // ??????????????????????????????????????????????????? ???????????????
                // val same_pages = KernelProrp.getProp("/sys/block/zram0/same_pages")
                // ???????????????????????????
                // val pages_compacted = KernelProrp.getProp("/sys/block/zram0/pages_compacted")
                // ??????????????????
                // val huge_pages = KernelProrp.getProp("/sys/block/zram0/huge_pages")

                String.format(
                        getString(R.string.swap_zram_stat_format),
                        compAlgorithm,
                        zramInfoValueParseMB(origDataSize),
                        zramInfoValueParseMB(comprDataSize),
                        zramInfoValueParseMB(memUsedTotal),
                        zramInfoValueParseMB(memUsedMax),
                        if (memLimit == "0") "" else memLimit,
                        zramCompressionRatio(origDataSize, comprDataSize))
            } else {
                String.format(
                        getString(R.string.swap_zram_stat_format2),
                        compAlgorithm,
                        zramInfoValueParseMB(origDataSize),
                        zramInfoValueParseMB(comprDataSize),
                        zramCompressionRatio(origDataSize, comprDataSize))
            }
            if (zramWriteBackStat != null) {
                return generalStats + "\n\n" + String.format(
                        getString(R.string.swap_zram_writback_stat),
                        zramWriteBackStat.backingDev,
                        zramWriteBackStat.backed / 1024,
                        zramWriteBackStat.backReads / 1024,
                        zramWriteBackStat.backWrites / 1024
                )
            } else {
                return generalStats
            }
        }
    }

    private fun zramInfoValueParseMB(sizeStr: String): String {
        return try {
            (sizeStr.toLong() / 1024 / 1024).toString() + "MB"
        } catch (ex: java.lang.Exception) {
            sizeStr
        }
    }

    private fun zramCompressionRatio(origDataSize: String, comprDataSize: String): String {
        return try {
            (comprDataSize.toLong() * 1000 / origDataSize.toLong() / 10.0).toString() + "%"
        } catch (ex: java.lang.Exception) {
            "$comprDataSize/$origDataSize"
        }
    }

    private fun swapUsedSizeParseMB(sizeStr: String): String {
        return try {
            (sizeStr.toLong() / 1024).toString()
        } catch (ex: java.lang.Exception) {
            sizeStr
        }
    }

    override fun onResume() {
        super.onResume()
        title = getString(R.string.menu_swap)
        startTimer()
    }

    private var showSwapOpened = {
        Toast.makeText(context, getString(R.string.executed), Toast.LENGTH_LONG).show()
        processBarDialog.hideDialog()
    }

    class OnSeekBarChangeListener(
            private var onValueChange: Runnable?,
            private var omCompleted: Runnable?,
            private var spf: SharedPreferences,
            private var spfProp: String,
            private var ratio: Int = 1) : SeekBar.OnSeekBarChangeListener {
        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            omCompleted?.run()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
        }

        @SuppressLint("ApplySharedPref")
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            val value = progress * ratio
            if (spf.getInt(spfProp, Int.MIN_VALUE) == value) {
                return
            }
            spf.edit().putInt(spfProp, value).commit()
            onValueChange?.run()
        }
    }

    // ???????????????????????????
    override fun onPause() {
        stopTimer()
        swapModuleUtils.saveModuleConfig(swapConfig)
        super.onPause()
    }

    override fun onDestroy() {
        processBarDialog.hideDialog()
        super.onDestroy()
    }
}
