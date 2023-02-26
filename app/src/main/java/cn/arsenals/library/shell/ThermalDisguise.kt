package cn.arsenals.library.shell

import android.os.Build
import cn.arsenals.common.shell.KeepShellPublic
import cn.arsenals.common.shell.RootFile
import java.util.*

class ThermalDisguise {
    private final val boardSensorTemp = "/sys/class/thermal/thermal_message/board_sensor_temp"
    private final val migtMaxFreq = "/sys/module/migt/parameters/glk_maxfreq"
    private final val gameServiceApp = "com.xiaomi.gamecenter.sdk.service"
    private final val gameService = "com.xiaomi.gamecenter.sdk.service/.PidService"
    private final val tunearsenalsStorage = "tunearsenals.thermal.disguise"
    public fun supported (): Boolean {
        if (Build.MANUFACTURER.toUpperCase(Locale.getDefault()) == "XIAOMI") {
            if (PlatformUtils().getCPUName().equals("lahaina")) {
                if (PropsUtils.getProp("init.svc.mi_thermald") == "running") {
                    return RootFile.fileExists(boardSensorTemp)
                }
            }
        }
        return false
    }

    public fun disableMessage () {
        KeepShellPublic.doCmdSync("" +
                "chmod 644 $boardSensorTemp\n" +
                "echo 36500 > $boardSensorTemp\n" +
                "chmod 000 $boardSensorTemp\n" +
                "chmod 644 $migtMaxFreq\n" +
                "echo 0 0 0 > $migtMaxFreq\n" +
                "chmod 644 $migtMaxFreq\n" +
                "pm disable $gameService\n" +
                "pm clear $gameServiceApp\n" +
                "setprop $tunearsenalsStorage 1")
    }

    public fun resumeMessage () {
        KeepShellPublic.doCmdSync("" +
                "chmod 644 $boardSensorTemp\n" +
                "chmod 644 $migtMaxFreq\n" +
                "pm enable $gameService\n" +
                "setprop $tunearsenalsStorage 0")
    }

    public fun isDisabled (): Boolean {
        return PropsUtils.getProp(tunearsenalsStorage) == "1" && KeepShellPublic.doCmdSync("ls -l $boardSensorTemp").startsWith("----------")
    }
}