package cn.arsenals.tunearsenals.dialogs

import android.app.Activity
import android.content.Context
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import cn.arsenals.common.shared.FileWrite
import cn.arsenals.common.shared.MagiskExtend
import cn.arsenals.common.shell.AsynSuShellUnit
import cn.arsenals.common.shell.KeepShell
import cn.arsenals.common.shell.KeepShellPublic
import cn.arsenals.common.ui.DialogHelper
import cn.arsenals.model.AppInfo
import cn.arsenals.tunearsenals.R
import cn.arsenals.utils.CommonCmds
import java.io.File
import java.util.*

/**
 * Created by helloklf on 2017/12/04.
 */

open class DialogAppOptions(protected var context: Activity, protected var apps: ArrayList<AppInfo>, protected var handler: Handler) {
    private var allowPigz = false
    private var backupPath = CommonCmds.AbsBackUpDir
    private var userdataPath = ""

    init {
        userdataPath = context.filesDir.absolutePath
        userdataPath = userdataPath.substring(0, userdataPath.indexOf(context.packageName) - 1)
    }

    fun selectUserAppOptions() {
        val dialogView = context.layoutInflater.inflate(R.layout.dialog_app_options_user, null)

        val dialog = DialogHelper.customDialog(context, dialogView)
        dialogView.findViewById<View>(R.id.app_options_single_only).visibility = View.GONE
        dialogView.findViewById<View>(R.id.app_options_clear).setOnClickListener {
            dialog.dismiss()
            clearAll()
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            dialogView.findViewById<View>(R.id.app_options_backup_apk).visibility = View.GONE
        } else {
            dialogView.findViewById<View>(R.id.app_options_backup_apk).setOnClickListener {
                dialog.dismiss()
                backupAll()
            }
        }
        dialogView.findViewById<View>(R.id.app_options_uninstall).setOnClickListener {
            dialog.dismiss()
            uninstallAll()
        }
        /*
        dialogView.findViewById<View>(R.id.app_options_as_system).setOnClickListener {
            dialog.dismiss()
            moveToSystem()
        }
        */
        dialogView.findViewById<View>(R.id.app_options_dex2oat).setOnClickListener {
            dialog.dismiss()
            buildAll()
        }
        dialogView.findViewById<TextView>(R.id.app_options_title).text = "???????????????"

        dialogView.findViewById<View>(R.id.app_options_app_freeze).setOnClickListener {
            dialog.dismiss()
            modifyStateAll()
        }
    }

    fun selectSystemAppOptions() {
        val dialogView = context.layoutInflater.inflate(R.layout.dialog_app_options_system, null)

        val dialog = DialogHelper.customDialog(context, dialogView)
        dialogView.findViewById<View>(R.id.app_options_single_only).visibility = View.GONE
        dialogView.findViewById<View>(R.id.app_options_clear).setOnClickListener {
            dialog.dismiss()
            clearAll()
        }
        dialogView.findViewById<View>(R.id.app_options_uninstall_user).setOnClickListener {
            dialog.dismiss()
            uninstallAllSystem(false) // TODO:xxx
        }
        dialogView.findViewById<View>(R.id.app_options_dex2oat).setOnClickListener {
            dialog.dismiss()
            buildAll()
        }

        dialogView.findViewById<View>(R.id.app_options_delete).setOnClickListener {
            dialog.dismiss()
            deleteAll()
        }
        dialogView.findViewById<View>(R.id.app_options_uninstall).visibility = View.GONE

        dialogView.findViewById<TextView>(R.id.app_options_title).text = "???????????????"

        dialogView.findViewById<View>(R.id.app_options_app_freeze).setOnClickListener {
            dialog.dismiss()
            modifyStateAll()
        }
    }

    fun selectBackupOptions() {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_restore, null)
        val dialog = DialogHelper.customDialog(context, view)
        view.findViewById<View>(R.id.app_install).run {
            setOnClickListener {
                dialog.dismiss()
                restoreAll(apk = true, data = false)
            }
        }

        val dataExists = (apps.find {
            backupDataExists(it.packageName)
        }) != null

        view.findViewById<View>(R.id.app_restore_full).run {
            visibility = if (dataExists) View.VISIBLE else View.GONE
            setOnClickListener {
                dialog.dismiss()
                restoreAll(apk = true, data = true)
            }
        }
        view.findViewById<View>(R.id.app_restore_data).run {
            visibility = if (dataExists) View.VISIBLE else View.GONE
            setOnClickListener {
                dialog.dismiss()
                restoreAll(apk = false, data = true)
            }
        }
        view.findViewById<View>(R.id.app_delete_backup).setOnClickListener {
            dialog.dismiss()
            deleteBackupAll()
        }

    }

    private fun checkRestoreData(): Boolean {
        val r = KeepShellPublic.doCmdSync("cd $userdataPath/${context.packageName}\necho `toybox ls -ld|cut -f3 -d ' '`\n echo `ls -ld|cut -f3 -d ' '`\n")
        return r != "error" && r.trim().isNotEmpty()
    }

    protected fun isMagisk(): Boolean {
        val keepShell = KeepShell(false)
        val result = keepShell.doCmdSync("su -v").toUpperCase(Locale.getDefault()).contains("MAGISKSU")
        keepShell.tryExit()
        return result
    }

    protected fun isTmpfs(dir: String): Boolean {
        val keepShell = KeepShell(false)
        val result = keepShell.doCmdSync("df | grep tmpfs | grep \"$dir\"").toUpperCase(Locale.getDefault()).trim().isNotEmpty()
        keepShell.tryExit()
        return result
    }

    protected fun execShell(sb: StringBuilder) {
        val layoutInflater = LayoutInflater.from(context)
        val dialog = layoutInflater.inflate(R.layout.dialog_loading, null)
        val textView = (dialog.findViewById(R.id.dialog_text) as TextView)
        textView.text = "??????????????????"
        val alert = DialogHelper.customDialog(context, dialog, false)
        AsynSuShellUnit(ProgressHandler(dialog, alert, handler)).exec(sb.toString()).waitFor()
    }

    open class ProgressHandler(dialog: View, protected var alert: DialogHelper.DialogWrap, protected var handler: Handler) : Handler(Looper.getMainLooper()) {
        private var textView: TextView = (dialog.findViewById(R.id.dialog_text) as TextView)
        var progressBar: ProgressBar = (dialog.findViewById(R.id.dialog_app_details_progress) as ProgressBar)
        private var error = java.lang.StringBuilder()

        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.obj != null) {
                if (msg.what == 0) {
                    textView.text = "??????????????????..."
                } else if (msg.what == 5) {
                    error.append(msg.obj)
                    error.append("\n")
                } else if (msg.what == 10) {
                    if (msg.obj == true) {
                        textView.text = "???????????????"
                    } else {
                        textView.text = "???????????????"
                    }
                    handler.postDelayed({
                        alert.dismiss()
                        alert.hide()
                    }, 2000)
                } else {
                    val obj = msg.obj.toString()
                    if (obj.contains("[operation completed]")) {
                        progressBar.progress = 100
                        textView.text = "???????????????"
                        handler.postDelayed({
                            try {
                                alert.dismiss()
                                alert.hide()
                            } catch (ex: Exception) {
                            }
                            if (error.isNotBlank()) {
                                val context: Context = alert.context
                                DialogHelper.alert(context, "?????????????????????", error.toString())
                            }
                        }, 1200)
                        handler.handleMessage(handler.obtainMessage(2))
                    } else if (Regex("^\\[.*]\$").matches(obj)) {
                        progressBar.progress = msg.what
                        val txt = obj
                                .replace("[copy ", "[?????? ")
                                .replace("[uninstall ", "[?????? ")
                                .replace("[install ", "[?????? ")
                                .replace("[restore ", "[?????? ")
                                .replace("[backup ", "[?????? ")
                                .replace("[unhide ", "[?????? ")
                                .replace("[hide ", "[?????? ")
                                .replace("[delete ", "[?????? ")
                                .replace("[disable ", "[?????? ")
                                .replace("[enable ", "[?????? ")
                                .replace("[trim caches ", "[???????????? ")
                                .replace("[clear ", "[???????????? ")
                                .replace("[skip ", "[?????? ")
                                .replace("[link ", "[?????? ")
                                .replace("[compile ", "[?????? ")
                        textView.text = txt
                    }
                }
            }
        }

        init {
            textView.text = "??????????????????"
        }
    }

    protected fun confirm(title: String, msg: String, next: Runnable?) {
        DialogHelper.confirmBlur(context, title, msg, next)
    }

    /**
     * ??????????????????pigz
     */
    protected fun checkPigz() {
        if (File("/system/xbin/pigz").exists() || File("/system/bin/pigz").exists()) {
            allowPigz = true
        }
    }

    /**
     * ?????????????????????
     */
    protected fun backupAll() {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_backup_mode, null)
        view.findViewById<TextView>(R.id.confirm_message).text = "??????????????? ${apps.size} ?????????????????????"

        val dialog = DialogHelper.customDialog(context, view)
        val includeData = view.findViewById<CompoundButton>(R.id.backup_include_data)

        // ?????????????????????????????????
        if (Build.VERSION.SDK_INT >= 30 || !checkRestoreData()) {
            includeData.isEnabled = false
            includeData.isChecked = false
        }

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
            _backupAll(true, includeData.isChecked)
        }
    }

    private fun _backupAll(apk: Boolean = true, data: Boolean = true) {
        checkPigz()

        val date = Date().time.toString()

        val sb = StringBuilder()
        sb.append("backup_date=\"$date\"\n")
        sb.append("\n")
        sb.append("backup_path=\"${CommonCmds.AbsBackUpDir}\"\n")
        sb.append("mkdir -p \${backup_path}\n")
        sb.append("\n")
        sb.append("\n")

        for (item in apps) {
            val packageName = item.packageName.toString()
            val path = item.path.toString()

            if (apk) {
                sb.append("rm -f \${backup_path}$packageName.apk\n")
                sb.append("\n")
                sb.append("echo '[copy $packageName.apk]'\n")
                sb.append("busybox cp -f $path \${backup_path}$packageName.apk\n")
                sb.append("\n")
            }
            if (data) {
                sb.append(
                        "killall -9 $packageName 2> /dev/null\n" +
                                "am kill-all $packageName 2> /dev/null\n" +
                                "am force-stop $packageName 2> /dev/null\n")
                sb.append("cd $userdataPath/$packageName\n")
                sb.append("echo '[backup ${item.appName}]'\n")
                if (allowPigz)
                    sb.append("busybox tar cpf - * --exclude ./cache --exclude ./lib | pigz > \${backup_path}$packageName.tar.gz\n")
                else
                    sb.append("busybox tar -czpf \${backup_path}$packageName.tar.gz * --exclude ./cache --exclude ./lib\n")
                sb.append("\n")
            }
        }
        sb.append("cd \${backup_path}\n")
        sb.append("chown sdcard_rw:sdcard_rw *\n")
        sb.append("chmod 777 *\n")

        sb.append("echo '[operation completed]'\n")
        execShell(sb)
    }

    /**
     * ?????????????????????
     */
    protected fun restoreAll(apk: Boolean = true, data: Boolean = true) {
        if (data) {
            if (!checkRestoreData()) {
                Toast.makeText(context, "????????????????????????????????????????????????????????????", Toast.LENGTH_LONG).show()
                return
            }
            confirm("?????????????????????",
                    "??????????????? ${apps.size} ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????FC????????????????????????"
            ) {
                _restoreAll(apk, data)
            }
        } else {
            confirm("????????????", "??????????????? ${apps.size} ?????????????????????") {
                _restoreAll(apk, data)
            }
        }
    }

    protected fun backupDataExists(packageName: String): Boolean {
        return File("$backupPath$packageName.tar.gz").exists()
    }

    private fun _restoreAll(apk: Boolean = true, data: Boolean = true) {
        val installApkTemp = FileWrite.getPrivateFilePath(context, "app_install_cache.apk")
        checkPigz()

        val sb = StringBuilder()
        sb.append("chown -R sdcard_rw:sdcard_rw \"$backupPath\" 2>/dev/null\n")
        sb.append("chmod -R 777 \"$backupPath\" 2>/dev/null\n")
        for (item in apps) {
            val packageName = item.packageName.toString()
            val apkPath = item.path.toString()
            if (apk && File(apkPath).exists()) {
                sb.append("echo '[install ${item.appName}]'\n")
                // sb.append("pm install -r \"$apkPath\" 1> /dev/null\n")

                sb.append("rm -f $installApkTemp\n")
                sb.append("cp \"$apkPath\" $installApkTemp\n")
                sb.append("pm install -r $installApkTemp 1> /dev/null\n")
                sb.append("rm -f $installApkTemp\n")
            } else if (apk && File("$backupPath$packageName.apk").exists()) {
                sb.append("echo '[install ${item.appName}]'\n")
                // sb.append("pm install -r $backupPath$packageName.apk\n")

                sb.append("rm -f $installApkTemp\n")
                sb.append("cp \"$backupPath$packageName.apk\" $installApkTemp\n")
                sb.append("pm install -r $installApkTemp 1> /dev/null\n")
                sb.append("rm -f $installApkTemp\n")
            }
            if (data && backupDataExists(packageName)) {
                sb.append("if [ -d $userdataPath/$packageName ]\n")
                sb.append(" then ")
                sb.append("echo '[restore ${item.appName}]'\n")
                //sb.append("pm clear $packageName\n")
                sb.append("sync\n")
                sb.append("cd $userdataPath/$packageName\n")
                sb.append("busybox tar -xzpf $backupPath$packageName.tar.gz\n")
                sb.append("install_group=`toybox ls -ld|cut -f3 -d ' '`\n")
                sb.append("install_own=`toybox ls -ld|cut -f4 -d ' '`\n")
                sb.append("for item in *\ndo\n")
                sb.append(
                        "if [[ ! \"\$item\" = \"lib\" ]] && [[ ! \"\$item\" = \"lib64\" ]]\n" +
                                "then\n" +
                                "chown -R \$install_group:\$install_own ./\$item\n" +
                                "fi\n" +
                                "done\n")
                //sb.append("chown -R --reference=$userdataPath/$packageName *\n")
                sb.append(" else ")
                sb.append("echo '[skip ${item.appName}]'\n")
                sb.append("sleep 1\n")
                sb.append("fi\n")
            }
        }
        sb.append("sync\n")
        sb.append("sleep 2\n")
        sb.append("echo '[operation completed]'\n")
        execShell(sb)
    }

    /**
     * ?????????????????????
     */
    protected fun modifyStateAll() {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_disable_mode, null)
        val dialog = DialogHelper.customDialog(context, view)
        view.findViewById<TextView>(R.id.confirm_message).text = "????????? ${apps.size} ????????????????????????????????????????????????"

        val switchSuspend = view.findViewById<CompoundButton>(R.id.disable_suspend)
        val switchFreeze = view.findViewById<CompoundButton>(R.id.disable_freeze)
        val switchHide = view.findViewById<CompoundButton>(R.id.disable_hide)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            switchSuspend.isEnabled = false
            switchSuspend.isEnabled = true
        }
        switchSuspend.isChecked = apps.filter { it.suspended }.size == apps.size
        switchFreeze.isChecked = apps.filter { !it.enabled }.size == apps.size

        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            val suspend = switchSuspend.isChecked
            val freeze = switchFreeze.isChecked
            val hide = switchHide.isChecked
            _modifyStateAll(suspend, freeze, hide)
        }
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun _modifyStateAll(suspend: Boolean, freeze: Boolean, hide: Boolean) {
        val androidP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        val sb = StringBuilder()
        for (item in apps) {
            val packageName = item.packageName.toString()
            if (suspend) {
                if (!item.suspended) {
                    sb.append("echo '[suspend ${item.appName}]'\n")
                    sb.append("pm suspend $packageName\n")
                }
            } else if (androidP) {
                if (item.suspended) {
                    sb.append("echo '[unsuspend ${item.appName}]'\n")
                    sb.append("am kill $packageName 2>/dev/null\n")
                    sb.append("pm unsuspend $packageName\n")
                }
            }

            if (freeze) {
                if (item.enabled) {
                    sb.append("echo '[disable ${item.appName}]'\n")
                    sb.append("pm disable ${packageName}\n")
                }
            } else {
                if (!item.enabled) {
                    sb.append("echo '[enable ${item.appName}]'\n")
                    sb.append("pm enable ${packageName}\n")
                }
            }

            if (hide) {
                sb.append("echo '[hide ${item.appName}]'\n")
                sb.append("pm hide ${packageName}\n")
            }

        }

        sb.append("echo '[operation completed]'\n")
        execShell(sb)
    }

    /**
     * ?????????????????????
     */
    protected fun deleteAll() {
        confirm("????????????", "????????? ${apps.size} ?????????????????????????????????????????????????????????????????????????????????????????????????????????") {
            if (isMagisk() && !MagiskExtend.moduleInstalled() && (isTmpfs("/system/app") || isTmpfs("/system/priv-app"))) {
                DialogHelper.confirm(context,
                        "Magisk ???????????????",
                        "????????????????????????Magisk??????ROOT????????????????????????/system/app???/system/priv-app???????????????????????????????????????????????????????????????Magisk??????????????????????????????",
                        DialogHelper.DialogButton(context.getString(R.string.btn_continue), {
                            _deleteAll()
                        }))
            } else {
                _deleteAll()
            }
        }
    }

    private fun _deleteAll() {
        val sb = StringBuilder()
        sb.append(CommonCmds.MountSystemRW)
        var useMagisk = false
        for (item in apps) {
            val packageName = item.packageName.toString()
            // ?????????????????????????????????????????????
            sb.append("echo '[disable ${item.appName}]'\n")
            sb.append("pm disable $packageName\n")

            sb.append("echo '[delete ${item.appName}]'\n")
            if (MagiskExtend.moduleInstalled()) {
                MagiskExtend.deleteSystemPath(item.path.toString())
                useMagisk = true
            } else {
                val dir = item.dir.toString()

                sb.append("rm -rf $dir/oat\n")
                sb.append("rm -rf $dir/lib\n")
                sb.append("rm -rf '${item.path}'\n")
            }
        }

        sb.append("echo '[operation completed]'\n")
        execShell(sb)
        if (useMagisk) {
            DialogHelper.helpInfo(context, "?????????Magisk??????????????????????????????~", "")
        }
    }

    /**
     * ????????????
     */
    protected fun deleteBackupAll() {
        confirm("????????????", "?????????????????????????????????", Runnable {
            _deleteBackupAll()
        })
    }

    private fun _deleteBackupAll() {
        val sb = StringBuilder()
        for (item in apps) {
            val packageName = item.packageName.toString()
            sb.append("echo '[delete ${item.appName}]'\n")

            if (item.path != null) {
                sb.append("rm -rf '${item.path}'\n")
                if (item.path == "$backupPath$packageName.apk") {
                    sb.append("rm -rf $backupPath$packageName.tar.gz\n")
                }
            } else {
                sb.append("rm -rf $backupPath$packageName.apk\n")
                sb.append("rm -rf $backupPath$packageName.tar.gz\n")
            }
        }

        sb.append("echo '[operation completed]'\n")
        execShell(sb)
    }

    /**
     * ????????????
     */
    protected fun clearAll() {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_clear_mode, null)
        view.findViewById<TextView>(R.id.confirm_message).text = "?????????????????? ${apps.size} ????????????????????????"

        val dialog = DialogHelper.customDialog(context, view)
        val userOnly = view.findViewById<CompoundButton>(R.id.clear_user_only)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            _clearAll(userOnly.isChecked)
        }
    }

    private fun _clearAll(userOnly: Boolean) {
        val um = context.getSystemService(Context.USER_SERVICE) as UserManager?
        val userHandle = android.os.Process.myUserHandle()
        var uid = 0L
        if (um != null) {
            uid = um.getSerialNumberForUser(userHandle)
        } else {
            Toast.makeText(context, "????????????ID?????????", Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()
        for (item in apps) {
            val packageName = item.packageName.toString()
            sb.append("echo '[clear ${item.appName}]'\n")

            if (userOnly) {
                sb.append("pm clear --user $uid $packageName\n")
            } else {
                sb.append("pm clear $packageName\n")
            }
        }

        sb.append("echo '[operation completed]'\n")
        execShell(sb)
    }

    /**
     * ????????????
     */
    protected fun uninstallAll() {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_uninstall_mode, null)
        view.findViewById<TextView>(R.id.confirm_message).text = "????????????????????? ${apps.size} ????????????"

        val dialog = DialogHelper.customDialog(context, view)
        val userOnly = view.findViewById<CompoundButton>(R.id.uninstall_user_only)
        val keepData = view.findViewById<CompoundButton>(R.id.uninstall_keep_data)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            _uninstallAll(userOnly.isChecked, keepData.isChecked)
        }
    }

    /**
     * ????????????
     */
    protected fun uninstallAllSystem(updated: Boolean) {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_uninstall_mode, null)
        view.findViewById<TextView>(R.id.confirm_message).text = "????????????????????? ${apps.size} ??????????????????"

        val dialog = DialogHelper.customDialog(context, view)
        val userOnly = view.findViewById<CompoundButton>(R.id.uninstall_user_only)
        val keepData = view.findViewById<CompoundButton>(R.id.uninstall_keep_data)

        userOnly.isEnabled = false
        if (updated) {
            userOnly.isEnabled = false
            keepData.isEnabled = false

            userOnly.isChecked = false
            keepData.isChecked = false
        } else {
            userOnly.isEnabled = false
            userOnly.isChecked = true
        }

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()

            _uninstallAll(userOnly.isChecked, keepData.isChecked)
        }
    }

    private fun _uninstallAll(userOnly: Boolean, keepData: Boolean) {
        if (userOnly) {
            val um = context.getSystemService(Context.USER_SERVICE) as UserManager?
            val userHandle = android.os.Process.myUserHandle()
            if (um != null) {
                val uid = um.getSerialNumberForUser(userHandle)
                _uninstallAllOnlyUser(uid, keepData)
            } else {
                Toast.makeText(context, "????????????ID?????????", Toast.LENGTH_SHORT).show()
            }
        } else {
            val sb = StringBuilder()

            for (item in apps) {
                val packageName = item.packageName.toString()
                sb.append("echo '[uninstall ${item.appName}]'\n")

                if (keepData) {
                    sb.append("pm uninstall -k $packageName\n")
                } else {
                    sb.append("pm uninstall $packageName\n")
                }
            }

            sb.append("echo '[operation completed]'\n")
            execShell(sb)
        }
    }

    private fun _uninstallAllOnlyUser(uid: Long, keepData: Boolean) {
        val sb = StringBuilder()
        for (item in apps) {
            val packageName = item.packageName.toString()
            sb.append("echo '[uninstall ${item.appName}]'\n")

            if (keepData) {
                sb.append("pm uninstall -k --user $uid $packageName\n")
            } else {
                sb.append("pm uninstall --user $uid $packageName\n")
            }
        }

        sb.append("echo '[operation completed]'\n")
        execShell(sb)
    }

    protected fun buildAll() {
        val view = context.layoutInflater.inflate(R.layout.dialog_app_dex2oat_mode, null)
        view.findViewById<TextView>(R.id.confirm_message).text = "dex2oat???????????????(?????????)????????????????????????????????????????????????????????????????????????\n\n?????????????????? ${apps.size} ???????????????dex2oat????????????"
        val switchEverything = view.findViewById<CompoundButton>(R.id.dex2oat_everything)
        val switchForce = view.findViewById<CompoundButton>(R.id.dex2oat_force)

        val dialog = DialogHelper.customDialog(context, view)
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            dialog.dismiss()
            if (switchEverything.isChecked) {
                buildAll("everything", switchForce.isChecked)
            } else {
                buildAll("speed", switchForce.isChecked)
            }
        }

    }

    private fun buildAll(mode: String, forced: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(context, "??????????????????Android N???7.0?????????????????????", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        for (item in apps) {
            val packageName = item.packageName.toString()
            sb.append("echo '[compile ${item.appName}]'\n")

            if (forced) {
                sb.append("cmd package compile -f -m $mode $packageName\n\n")
            } else {
                sb.append("cmd package compile -m $mode $packageName\n\n")
            }
        }

        sb.append("echo '[operation completed]'\n\n")
        execShell(sb)
    }
}
