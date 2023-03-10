package cn.arsenals.shell_utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import cn.arsenals.common.shell.KeepShellPublic
import cn.arsenals.common.shell.RootFile
import cn.arsenals.common.ui.ProgressBarDialog
import cn.arsenals.utils.CommonCmds

/**
 * Created by Hello on 2017/11/01.
 */

class BackupRestoreUtils(var context: Activity) {
    val dialog: ProgressBarDialog = ProgressBarDialog(context)
    internal var myHandler: Handler = Handler(Looper.getMainLooper())

    companion object {
        private var bootPartPath = "/dev/block/bootdevice/by-name/boot"
        private var recPartPath = "/dev/block/bootdevice/by-name/recovery"
        private var dtboPartPath = "/dev/block/bootdevice/by-name/dtbo"
        private var persistPartPath = "/dev/block/bootdevice/by-name/persist"

        fun isSupport(): Boolean {
            if (RootFile.itemExists(bootPartPath) || RootFile.itemExists(recPartPath)) {
                return true
            }

            var r = false
            val boots = KeepShellPublic.doCmdSync("ls /dev/block/platform/*/by-name/BOOT").split("\n")
            if (boots.isNotEmpty() && boots.first().startsWith("/dev/block/platform") && boots.first().endsWith("/by-name/BOOT")) {
                bootPartPath = boots.first()
                r = true
            }
            val recs = KeepShellPublic.doCmdSync("ls /dev/block/platform/*/by-name/RECOVERY").split("\n")
            if (recs.isNotEmpty() && recs.first().startsWith("/dev/block/platform") && recs.first().endsWith("/by-name/RECOVERY")) {
                recPartPath = recs.first()
                r = true
            }
            val dtbos = KeepShellPublic.doCmdSync("ls /dev/block/platform/*/by-name/DTBO").split("\n")
            if (dtbos.isNotEmpty() && dtbos.first().startsWith("/dev/block/platform") && dtbos.first().endsWith("/by-name/DTBO")) {
                dtboPartPath = dtbos.first()
                r = true
            }
            val persists = KeepShellPublic.doCmdSync("ls /dev/block/platform/*/by-name/PERSIST").split("\n")
            if (persists.isNotEmpty() && persists.first().startsWith("/dev/block/platform") && persists.first().endsWith("/by-name/PERSIST")) {
                persistPartPath = persists.first()
                r = true
            }
            return r
        }
    }

    //???????????????
    fun showProgressBar() {
        myHandler.post {
            dialog.showDialog("??????????????????...")
        }
    }

    //???????????????
    fun hideProgressBar() {
        myHandler.post {
            dialog.hideDialog()
        }
    }

    //??????????????????
    fun showMsg(msg: String, longMsg: Boolean) {
        myHandler.post {
            Toast.makeText(context, msg, if (longMsg) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
        }
    }

    //??????Boot
    fun flashBoot(path: String) {
        FlashImgThread(path, bootPartPath).start()
    }

    fun flashRecovery(path: String) {
        FlashImgThread(path, recPartPath).start()
    }

    fun flashDTBO(path: String) {
        FlashImgThread(path, dtboPartPath).start()
    }

    fun flashPersist(path: String) {
        FlashImgThread(path, persistPartPath).start()
    }

    internal inner class FlashImgThread(var inputPath: String, var outputPath: String) : Thread() {
        override fun run() {
            if (!isSupport() || !RootFile.itemExists(outputPath)) {
                showMsg("???????????????????????????", true)
                return
            }
            showMsg("????????????\n$inputPath\n?????????????????????", true)
            showProgressBar()
            if (KeepShellPublic.doCmdSync("dd if=\"$inputPath\" of=$outputPath") != "error") {
                showMsg("???????????????", true)
            } else {
                showMsg("??????????????????", true)
            }
            hideProgressBar()
        }
    }


    fun saveBoot() {
        SaveImgThread(bootPartPath, "boot").start()
    }

    fun saveRecovery() {
        SaveImgThread(recPartPath, "recovery").start()
    }

    fun saveDTBO() {
        SaveImgThread(dtboPartPath, "dtbo").start()
    }

    fun savePersist() {
        SaveImgThread(persistPartPath, "persist").start()
    }

    internal inner class SaveImgThread(var inputPath: String, var outputName: String) : Thread() {
        override fun run() {
            val outPath = "${CommonCmds.SDCardDir}/$outputName.img"

            if (!isSupport() || !RootFile.itemExists(inputPath)) {
                showMsg("???????????????????????????", true)
                return
            }
            showProgressBar()
            if (KeepShellPublic.doCmdSync("dd if=$inputPath of=$outPath\n") != "error") {
                showMsg("???????????????????????????????????????$outPath ???", true)
            } else {
                showMsg("???????????????????????????", true)
            }
            hideProgressBar()
        }
    }
}
