package com.boxverse.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 冒烟测试：真正启动 App，访问五个主页面，并完成一次虚拟开盒 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeout: Long = 30_000) {
        try {
            rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            val tree = runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 30) }.getOrElse { "无法打印界面：$it" }
            throw AssertionError("等待「$text」超时。当前界面：\n$tree", e)
        }
    }

    @Test
    fun allTabsOpenAndBlindBoxWorks() {
        waitForText("实物收藏总数")
        waitForText("每日收藏任务")

        rule.onNodeWithTag("tab_pokedex").performClick()
        waitForText("潮玩图鉴")
        waitForText("云朵甜品店")

        rule.onNodeWithTag("tab_trade").performClick()
        waitForText("交换中心")
        waitForText("摩卡小憩")

        rule.onNodeWithTag("tab_profile").performClick()
        waitForText("成就勋章")

        rule.onNodeWithTag("tab_blindbox").performClick()
        waitForText("选择盲盒系列")
        rule.onAllNodesWithText("进入开盒").onFirst().performClick()
        waitForText("概率公示")
        rule.onAllNodesWithText("开 1 盒").onFirst().performClick()
        waitForText("收入虚拟收藏柜", 60_000)
        rule.onAllNodesWithText("收入虚拟收藏柜").onFirst().performClick()
        waitForText("已收入虚拟收藏柜")
    }
}
