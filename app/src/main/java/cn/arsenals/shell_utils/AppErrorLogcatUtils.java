package cn.arsenals.shell_utils;

import android.os.Environment;

import cn.arsenals.common.shell.KeepShellPublic;
import cn.arsenals.common.shell.RootFile;

public class AppErrorLogcatUtils {
    private final String logPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/tunearsenals-error.log";

    public String catLogInfo() {
        if (!RootFile.INSTANCE.fileExists(logPath)) {
            return KeepShellPublic.INSTANCE.doCmdSync("logcat -d *:E > \"" + logPath + "\"");
        }
        return KeepShellPublic.INSTANCE.doCmdSync("cat \"" + logPath + "\"");
    }

    public void catLogInfo2File(int pid) {
        KeepShellPublic.INSTANCE.doCmdSync("logcat -d *:E --pid " + pid + " > \"" + logPath + "\"");
    }
}
