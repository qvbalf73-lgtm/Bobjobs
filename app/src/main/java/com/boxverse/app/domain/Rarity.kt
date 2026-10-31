package com.boxverse.app.domain

/**
 * 稀有度：普通 R、稀有 SR、隐藏 SSR、超级隐藏 UR。
 * SSR 与 UR 视为"隐藏款"。
 */
enum class Rarity(val code: String, val label: String, val isHidden: Boolean, val order: Int) {
    R("R", "普通", false, 0),
    SR("SR", "稀有", false, 1),
    SSR("SSR", "隐藏", true, 2),
    UR("UR", "超级隐藏", true, 3);

    val fullLabel: String get() = "$label $code"

    /** 款式类型：普通款 / 隐藏款 */
    val styleType: String get() = if (isHidden) "隐藏款" else "普通款"

    companion object {
        fun fromName(name: String?): Rarity = entries.firstOrNull { it.name == name } ?: R
    }
}

/** 实物收藏状态 */
enum class ItemStatus(val label: String, val isHolding: Boolean) {
    OWNED("已入手", true),
    SHIPPING("运输中", true),
    PREORDER("预定中", false),
    SOLD("已售出", false);

    companion object {
        fun fromName(name: String?): ItemStatus = entries.firstOrNull { it.name == name } ?: OWNED
    }
}

/** 交易类型 */
enum class TxType(val label: String) {
    BUY("购入"),
    SELL("售出");

    companion object {
        fun fromName(name: String?): TxType = entries.firstOrNull { it.name == name } ?: BUY
    }
}
