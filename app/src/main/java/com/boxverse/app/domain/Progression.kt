package com.boxverse.app.domain

/** 收藏家统计，用于经验值与成就计算（只包含正式数据，不含演示数据） */
data class CollectorStats(
    val totalQuantity: Int = 0,
    val distinctFigures: Int = 0,
    val completedSeries: Int = 0,
    val hiddenOwned: Int = 0,
    val checkInStreak: Int = 0,
    val totalCheckIns: Int = 0,
    val virtualOpenings: Int = 0,
    val virtualHidden: Int = 0,
    val virtualUr: Int = 0,
    val wishlistCount: Int = 0,
    val cabinetCount: Int = 0,
    val tradeListings: Int = 0,
    val estimatedValueBase: Long = 0,
)

data class LevelInfo(
    val level: Int,
    val title: String,
    val xp: Long,
    val levelStartXp: Long,
    val nextLevelXp: Long,
) {
    val isMax: Boolean get() = level >= LevelSystem.MAX_LEVEL
    val progress: Float
        get() = if (isMax) 1f else ((xp - levelStartXp).toFloat() / (nextLevelXp - levelStartXp)).coerceIn(0f, 1f)
}

object LevelSystem {
    const val MAX_LEVEL = 50

    /** 达到第 [level] 级所需的累计经验 */
    fun xpForLevel(level: Int): Long = 25L * (level - 1) * level

    fun levelFor(xp: Long): Int {
        var level = 1
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) level++
        return level
    }

    fun title(level: Int): String = when {
        level >= 50 -> "盒宇宙传奇"
        level >= 30 -> "图鉴大师"
        level >= 20 -> "隐藏款猎人"
        level >= 10 -> "潮玩探索家"
        level >= 5 -> "收藏爱好者"
        else -> "潮玩新人"
    }

    val milestones: List<Pair<Int, String>> = listOf(
        1 to "潮玩新人", 5 to "收藏爱好者", 10 to "潮玩探索家",
        20 to "隐藏款猎人", 30 to "图鉴大师", 50 to "盒宇宙传奇",
    )

    fun info(xp: Long): LevelInfo {
        val level = levelFor(xp)
        return LevelInfo(
            level = level,
            title = title(level),
            xp = xp,
            levelStartXp = xpForLevel(level),
            nextLevelXp = xpForLevel((level + 1).coerceAtMost(MAX_LEVEL)),
        )
    }

    /** 经验值完全由真实行为推导，不会因重复计算而漂移 */
    fun computeXp(stats: CollectorStats, achievementXp: Int): Long =
        stats.totalQuantity * 10L +
            stats.distinctFigures * 20L +
            stats.completedSeries * 150L +
            stats.hiddenOwned * 60L +
            stats.totalCheckIns * 10L +
            stats.virtualOpenings * 2L +
            achievementXp
}

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val target: Long,
    val xp: Int,
    val metric: (CollectorStats) -> Long,
)

data class AchievementProgress(val def: AchievementDef, val current: Long, val unlockedAt: Long?) {
    val unlocked: Boolean get() = unlockedAt != null
    val fraction: Float get() = (current.toFloat() / def.target).coerceIn(0f, 1f)
}

object Achievements {
    val ALL: List<AchievementDef> = listOf(
        AchievementDef("first_item", "初次心动", "收藏第 1 件实物潮玩", "favorite", 1, 20) { it.totalQuantity.toLong() },
        AchievementDef("figures_10", "小有收获", "收藏 10 个不同款式", "collections", 10, 50) { it.distinctFigures.toLong() },
        AchievementDef("figures_50", "收藏达人", "收藏 50 个不同款式", "collections", 50, 120) { it.distinctFigures.toLong() },
        AchievementDef("figures_100", "百款成就", "收藏 100 个不同款式", "workspace", 100, 300) { it.distinctFigures.toLong() },
        AchievementDef("first_series", "首个端盒", "首次集齐一个系列的全部常规款", "check", 1, 100) { it.completedSeries.toLong() },
        AchievementDef("series_3", "系列收割机", "集齐 3 个系列的全部常规款", "check", 3, 200) { it.completedSeries.toLong() },
        AchievementDef("hidden_1", "隐藏款降临", "实物收藏中拥有 1 款隐藏款", "diamond", 1, 100) { it.hiddenOwned.toLong() },
        AchievementDef("hidden_5", "隐藏款猎人", "实物收藏中拥有 5 款隐藏款", "diamond", 5, 250) { it.hiddenOwned.toLong() },
        AchievementDef("checkin_3", "三日之约", "连续签到 3 天", "fire", 3, 30) { it.checkInStreak.toLong() },
        AchievementDef("checkin_7", "一周不缺席", "连续签到 7 天", "fire", 7, 80) { it.checkInStreak.toLong() },
        AchievementDef("checkin_30", "月度收藏家", "连续签到 30 天", "fire", 30, 300) { it.checkInStreak.toLong() },
        AchievementDef("open_1", "拆盒初体验", "完成第 1 次虚拟拆盒", "gift", 1, 10) { it.virtualOpenings.toLong() },
        AchievementDef("open_100", "百抽不倦", "累计虚拟拆盒 100 次", "gift", 100, 150) { it.virtualOpenings.toLong() },
        AchievementDef("virtual_hidden", "欧气满满", "虚拟拆盒开出隐藏款", "sparkle", 1, 50) { it.virtualHidden.toLong() },
        AchievementDef("virtual_ur", "欧皇降临", "虚拟拆盒开出超级隐藏 UR", "crown", 1, 120) { it.virtualUr.toLong() },
        AchievementDef("wish_5", "心之所向", "心愿清单中添加 5 个愿望", "star", 5, 30) { it.wishlistCount.toLong() },
        AchievementDef("cabinet_1", "我的展柜", "创建第 1 个收藏柜", "cabinet", 1, 30) { it.cabinetCount.toLong() },
        AchievementDef("trade_1", "以物会友", "为重复款设置第 1 张交换卡", "swap", 1, 30) { it.tradeListings.toLong() },
        AchievementDef("value_1000", "千元藏家", "持有收藏估算价值达到 1000（基准币）", "money", 100_000, 100) { it.estimatedValueBase },
    )

    fun byId(id: String): AchievementDef? = ALL.firstOrNull { it.id == id }

    /** 返回当前满足条件但尚未解锁的成就 */
    fun newlyReached(stats: CollectorStats, unlockedIds: Set<String>): List<AchievementDef> =
        ALL.filter { it.id !in unlockedIds && it.metric(stats) >= it.target }

    fun progress(stats: CollectorStats, unlocked: Map<String, Long>): List<AchievementProgress> =
        ALL.map { AchievementProgress(it, it.metric(stats).coerceAtMost(it.target), unlocked[it.id]) }

    fun xpOf(unlockedIds: Collection<String>): Int = unlockedIds.sumOf { byId(it)?.xp ?: 0 }
}
