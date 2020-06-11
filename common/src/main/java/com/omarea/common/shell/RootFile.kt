package com.omarea.common.shell

import android.util.Log
import com.omarea.common.shared.RootFileInfo

/**
 * Created by Hello on 2018/07/06.
 */

object RootFile {
    fun itemExists(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -e \"$path\" ]]; then echo 1; fi;").equals("1")
    }

    fun fileExists(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -f \"$path\" ]]; then echo 1; fi;").equals("1")
    }

    fun fileNotEmpty(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -f \"$path\" ]] && [[ -s \"$path\" ]]; then echo 1; fi;").equals("1")
    }

    fun dirExists(path: String): Boolean {
        return KeepShellPublic.doCmdSync("if [[ -d \"$path\" ]]; then echo 1; fi;").equals("1")
    }

    fun deleteDirOrFile(path: String) {
        KeepShellPublic.doCmdSync("rm -rf \"$path\"")
    }

    // 处理像 "drwxrwx--x   3 root     root         4096 1970-07-14 17:13 vendor_de/" 这样的数据行
    private fun shellFileInfoRow(row: String, parent: String): RootFileInfo? {
        if (row.startsWith("total ")) {
            return null
        }

        val file = RootFileInfo()

        val columns = row.trim().split(" ");
        val size = columns[0]
        file.fileSize = size.toLong();

        //  8 /data/adb/modules/scene_systemless/ => /data/adb/modules/scene_systemless/
        val fileName = row.substring(row.indexOf(size) + size.length + 1);

        if (fileName == "./" || fileName == "../") {
            return null
        }

        // -F  append /dir *exe @sym |FIFO

        if (fileName.endsWith("/")) {
            file.filePath = fileName.substring(0, fileName.length - 1)
            file.isDirectory = true
        } else if (fileName.endsWith("@")) {
            file.filePath = fileName.substring(0, fileName.length - 1)
        } else if (fileName.endsWith("|")) {
            file.filePath = fileName.substring(0, fileName.length - 1)
        } else if (fileName.endsWith("*")) {
            file.filePath = fileName.substring(0, fileName.length - 1)
        } else {
            file.filePath = fileName
        }

        file.parentDir = parent

        return file
    }

    fun list(path: String): ArrayList<RootFileInfo> {
        val absPath = if (path.endsWith("/")) path.subSequence(0, path.length - 1).toString() else path
        val files = ArrayList<RootFileInfo>()
        if (dirExists(absPath)) {
            val outputInfo = KeepShellPublic.doCmdSync("ls -1Fs \"$absPath\"")
            Log.d(">>>> files", outputInfo)
            if (outputInfo != "error") {
                val rows = outputInfo.split("\n")
                for (row in rows) {
                    val file = shellFileInfoRow(row, absPath)
                    if (file != null) {
                        files.add(file)
                    } else {
                        Log.e(">>>> Scene", "MapDirError Row -> " + row)
                    }
                }
            }
        } else {
            Log.e(">>>> dir lost", absPath)
        }
        Log.d(">>>> files", "count " + files.size)

        return files
    }

    fun fileInfo(path: String): RootFileInfo? {
        val absPath = if (path.endsWith("/")) path.subSequence(0, path.length - 1).toString() else path
        val outputInfo = KeepShellPublic.doCmdSync("ls -1dFs \"$absPath\"")
        Log.d(">>>> file", outputInfo)
        if (outputInfo != "error") {
            val rows = outputInfo.split("\n")
            for (row in rows) {
                val file = shellFileInfoRow(row, absPath)
                if (file != null) {
                    file.filePath = absPath.substring(absPath.lastIndexOf("/") + 1)
                    file.parentDir = absPath.substring(0, absPath.lastIndexOf("/"))
                    return file
                } else {
                    Log.e(">>>> Scene", "MapDirError Row -> " + row)
                }
            }
        }

        return null
    }
}
