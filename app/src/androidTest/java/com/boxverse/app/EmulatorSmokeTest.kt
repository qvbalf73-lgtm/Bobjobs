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

/** 真机/模拟器冒烟测试：使用真实触摸事件走一遍主要流程 */
@RunWith(AndroidJUnit4::class)
class EmulatorSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeout: Long = 30_000) {
        try {
            rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            val tree = runCatching { rule.onRoot(useUnmergedTree = true).printToString(maxDepth = 30) }.getOrElse { "$it" }
            throw AssertionError("等待「$text」超时。当前界面：\n$tree", e)
        }
    }

    @Test
    fun touchThroughMainFlows() {
        waitForText("每日收藏任务")

        rule.onNodeWithTag("tab_pokedex").performClick()
        waitForText("潮玩图鉴")
        waitForText("云朵甜品店")
        rule.onAllNodesWithText("云朵甜品店").onFirst().performClick()
        waitForText("常规款")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForText("潮玩图鉴")

        rule.onNodeWithTag("tab_trade").performClick()
        waitForText("交换中心")

        rule.onNodeWithTag("tab_profile").performClick()
        waitForText("成就勋章")

        rule.onNodeWithTag("tab_home").performClick()
        waitForText("每日收藏任务")

        rule.onNodeWithTag("tab_blindbox").performClick()
        waitForText("选择盲盒系列")
        rule.onAllNodesWithText("进入开盒").onFirst().performClick()
        waitForText("概率公示")
        rule.onAllNodesWithText("开 5 盒").onFirst().performClick()
        waitForText("收入虚拟收藏柜", 60_000)
        rule.onAllNodesWithText("收入虚拟收藏柜").onFirst().performClick()
        waitForText("已收入虚拟收藏柜")
    }
}
