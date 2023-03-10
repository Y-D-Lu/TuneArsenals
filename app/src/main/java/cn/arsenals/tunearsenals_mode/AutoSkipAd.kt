package cn.arsenals.tunearsenals_mode

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import cn.arsenals.TuneArsenals
import cn.arsenals.store.AutoSkipConfigStore
import cn.arsenals.store.SpfConfig
import java.util.*

/**
 * Created by Hello on 2020/09/10.
 */
class AutoSkipAd(private val service: AccessibilityService) {
    private val autoSkipConfigStore = AutoSkipConfigStore(service.applicationContext)
    private var displayWidth: Int = 0
    private var displayHeight: Int = 0
    private val blackListCustom = service.getSharedPreferences(SpfConfig.AUTO_SKIP_BLACKLIST, Context.MODE_PRIVATE)

    companion object {
        private var lastClickedNodeValue: AccessibilityNodeInfo? = null
        private var lastClickedNode: AccessibilityNodeInfo?
            get() {
                return lastClickedNodeValue
            }
            set(value) {
                lastClickedNodeValue = value
                lastClickedNodeText = value?.text
            }
        private var lastClickedNodeText: CharSequence? = null
        private var lastClickedApp: String? = null
        private var lastActivity: String? = null
    }

    private val autoClickBase = AutoClickBase()

    private val blackList = arrayListOf("android", "com.android.systemui", "com.miui.home", "com.tencent.mobileqq", "com.tencent.mm", "cn.arsenals.tunearsenals", "cn.arsenals.gesture", "com.android.settings")

    private fun preciseSkip(root: AccessibilityNodeInfo): Boolean {
        autoSkipConfigStore.getSkipViewId(lastActivity)?.run {
            val id = this
            root.findAccessibilityNodeInfosByViewId(id)?.run {
                for (i in indices) {
                    val node = get(i)
                    if (!(lastClickedNode == node)) {
                        lastClickedNode = node
                        lastClickedApp = root.packageName?.toString()
                        autoClickBase.clickNode(node) || autoClickBase.tryTouchNodeRect(node, service)
                        TuneArsenals.toast("TuneArsenals????????????(${id})", Toast.LENGTH_SHORT)
                    }
                }
                return true
            }
        }
        return false
    }

    // ???????????????????????????????????????????????????????????????
    // ?????????????????????????????????????????????????????????
    // ???????????????????????????????????????????????????????????????
    private fun pointFilter(rect: Rect): Boolean {
        val top = rect.top.toFloat()
        val bottom = displayHeight - rect.bottom.toFloat()
        val left = rect.left.toFloat()
        val right = displayWidth - rect.right.toFloat()
        val minSide = if (displayHeight > displayWidth) displayWidth else displayHeight
        val xMax = minSide * 0.3f
        val yMax = minSide * 0.28f
        if (top > yMax && bottom > yMax) {
            Log.d("@TuneArsenals", "Y Filter ${top} ${bottom} ${yMax}")
            return false
        }
        if (left > xMax && right > xMax) {
            Log.d("@TuneArsenals", "X Filter ${left} ${right} ${xMax}")
            return false
        }
        // Log.d("@TuneArsenals", "Y Filter ${top} ${bottom} ${yMax}")
        // Log.d("@TuneArsenals", "X Filter ${left} ${right} ${xMax}")
        return true
    }

    // ??????????????????
    private val textRegx1 = Regex("^[0-9]+[\\ss]*??????[??????]*\$")
    private val textRegx2 = Regex("^[??????]*??????[??????]*[\\ss]{0,}[0-9]+\$")

    // ??????????????????????????????????????????eventTime??????????????? ???????????????????????????????????????????????????????????????
    private var lastCompletedEventTime = 0L
    fun skipAd(event: AccessibilityEvent, precise: Boolean, displayWidth: Int, displayHeight: Int) {
        this.displayWidth = displayWidth
        this.displayHeight = displayHeight
        val packageName = event.packageName?.toString()
        if (packageName == null || this.blackList.contains(packageName) || blackListCustom.contains(packageName)) {
            return
        }

        val source = event.source ?: return

        val t = event.eventTime
        if (!(lastCompletedEventTime != t && t > lastCompletedEventTime)) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastActivity = event.className?.toString()
        }
        if (precise && preciseSkip(source)) {
            return
        }

        try {
            source.findAccessibilityNodeInfosByText("??????")?.run {
                var node: AccessibilityNodeInfo
                for (i in indices) {
                    node = get(i)
                    val className = node.className.toString().toLowerCase(Locale.getDefault())
                    if (
                            className == "android.widget.textview" || className.toLowerCase(Locale.getDefault()).contains("button")
                    ) {
                        val text = node.text.trim().replace(Regex("[\nsS???]", RegexOption.MULTILINE), "").toString()
                        if (
                                text == "??????" || text == "????????????" ||
                                textRegx1.matches(text) ||
                                textRegx2.matches(text)
                        ) {
                            if (!(lastClickedApp == packageName && (lastClickedNode == node && lastClickedNodeText == node.text))) {
                                val viewId = node.viewIdResourceName
                                val p = Rect()
                                node.getBoundsInScreen(p)
                                val splash = lastActivity?.toLowerCase(Locale.getDefault())?.contains("splash") == true
                                if (splash || pointFilter(p)) {
                                    // ??????????????????
                                    if (autoClickBase.clickNode(node)) {
                                        Log.d("@TuneArsenals", "SkipAD ??? $packageName ${p} id: ${viewId}, text:" + node.text)
                                        TuneArsenals.toast("TuneArsenals????????????(${text})", Toast.LENGTH_SHORT)
                                        return
                                    }

                                    // ??????????????????
                                    val pp = Rect()
                                    val wrapNode = node.parent
                                    if (wrapNode != null) {
                                        wrapNode.getBoundsInScreen(pp)
                                        if ((splash || pointFilter(pp)) && autoClickBase.clickNode(wrapNode)) {
                                            Log.d("@TuneArsenals", "SkipAD ??? $packageName ${p} id: ${wrapNode.viewIdResourceName}, text:" + node.text)
                                            lastClickedApp = packageName.toString()
                                            lastClickedNode = node
                                            lastCompletedEventTime = t
                                            TuneArsenals.toast("TuneArsenals????????????(${text})", Toast.LENGTH_SHORT)
                                            return
                                        }
                                    }

                                    // ?????????????????????
                                    if (autoClickBase.tryTouchNodeRect(node, service)) {
                                        lastClickedApp = packageName.toString()
                                        lastClickedNode = node
                                        lastCompletedEventTime = t
                                        TuneArsenals.toast("TuneArsenals???????????????(${text})", Toast.LENGTH_SHORT)
                                        return
                                    }
                                } else {
                                    /*
                                    // ????????????????????????????????? ?????????????????????????????????
                                    val clickableNode = if (autoClickBase.nodeClickable(node)) {
                                        node
                                    } else {
                                        val wrapNode = node.parent
                                        if (autoClickBase.nodeClickable(wrapNode)) {
                                            wrapNode
                                        } else {
                                            null
                                        }
                                    }
                                    if (clickableNode != null) {
                                        FloatAdSkipConfirm(service).showConfirm(packageName, p) {
                                            autoClickBase.clickNode(clickableNode)
                                        }
                                        Log.d("@TuneArsenals", "SkipAD SKip -> $packageName, ${source.className} ${p} id: ${viewId}, text:" + node.text)
                                    }
                                    */
                                }
                                return
                            }
                        } else {
                            Log.d("@TuneArsenals", "SkipAD -> $className???" + node.text)
                        }
                    } else {
                        Log.d("@TuneArsenals", "SkipAD -> $className???" + node.text)
                    }
                }
            }


            /*
            var autoClickKeyWords: ArrayList<String> = object : ArrayList<String>() {
                init {
                    add("????????????")
                    add("???????????????")
                }
            }
            for (ki in autoClickKeyWords.indices) {
                val nextNodes = source.findAccessibilityNodeInfosByText(autoClickKeyWords[ki])
                val keyword = autoClickKeyWords[ki]
                if (nextNodes != null && nextNodes.isNotEmpty()) {
                    var node: AccessibilityNodeInfo
                    for (i in nextNodes.indices) {
                        node = nextNodes[i]
                        val className = node.className.toString().toLowerCase(Locale.getDefault())
                        if (
                                className == "android.widget.textview" ||
                                className.toLowerCase(Locale.getDefault()).contains("button")
                        ) {
                            val text = node.text.trim().toString()
                            if (text == keyword) {
                                if (!(lastClickedApp == packageName && lastClickedNode == node)) {
                                    autoClickBase.touchOrClickNode(node, service)
                                    lastClickedApp = packageName.toString()
                                    lastClickedNode = node
                                    lastCompletedEventTime = t

                                    TuneArsenals.toast("TuneArsenals????????????(${text})", Toast.LENGTH_SHORT)
                                }
                            }

                        }
                    }
                    break
                }
            }
            */
        } catch (ex: java.lang.Exception) {
            Log.e("@TuneArsenals", "SkipAD Error -> ${ex.message}")
        }
    }
}