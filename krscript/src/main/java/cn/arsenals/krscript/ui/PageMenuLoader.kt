package cn.arsenals.krscript.ui

import android.content.Context
import cn.arsenals.krscript.executor.ScriptEnvironmen
import cn.arsenals.krscript.model.PageMenuOption
import cn.arsenals.krscript.model.PageNode

class PageMenuLoader(private val applicationContext: Context, private val pageNode: PageNode) {
    private var menuOptions:ArrayList<PageMenuOption>? = null

    fun load (): ArrayList<PageMenuOption>? {
        pageNode.run {
            if (menuOptions == null) {
                pageNode.run {
                    if (pageMenuOptionsSh.isNotEmpty()) {
                        val result = ScriptEnvironmen.executeResultRoot(applicationContext, pageMenuOptionsSh, this)
                        if (result != "error") {
                            val items = result.split("\n")
                            for (item in items) {
                                val option = PageMenuOption(pageConfigPath)
                                if (item.contains("|")) {
                                    item.split("|").run {
                                        option.key = this[0]
                                        option.title = this[1]
                                    }
                                } else {
                                    option.key = item
                                    option.title = item
                                }
                            }
                        }
                    } else if (pageMenuOptions != null) {
                        menuOptions = pageMenuOptions
                    }
                }
            }
        }

        return menuOptions
    }
}