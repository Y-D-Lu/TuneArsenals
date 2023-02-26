package cn.arsenals.tunearsenals.activities

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import cn.arsenals.TuneArsenals
import cn.arsenals.common.shell.KeepShellPublic
import cn.arsenals.common.ui.ThemeMode
import cn.arsenals.tunearsenals.R

open class ActivityBase : AppCompatActivity() {
    public lateinit var themeMode: ThemeMode
    override fun onCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onCreate(savedInstanceState, persistentState)

        this.themeMode = ThemeSwitch.switchTheme(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        this.themeMode = ThemeSwitch.switchTheme(this)
    }

    protected val context: Context
        get() {
            return this
        }

    protected fun setBackArrow() {
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)

        // 显示返回按钮
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            this.onBackPressed()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    protected fun excludeFromRecent() {
        try {
            val service = this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (task in service.appTasks) {
                if (task.taskInfo.id == this.taskId) {
                    task.setExcludeFromRecents(true)
                }
            }
        } catch (ex: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TuneArsenals.postDelayed({
            System.gc()
        }, 500)
        if (isTaskRoot) {
            TuneArsenals.postDelayed({
                KeepShellPublic.doCmdSync("dumpsys meminfo " + context.packageName + " > /dev/null")
            }, 100)
        }
    }
}