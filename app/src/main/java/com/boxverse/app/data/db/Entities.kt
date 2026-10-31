package com.boxverse.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.boxverse.app.domain.ItemStatus
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.RateConfig
import com.boxverse.app.domain.TxType
import kotlinx.serialization.Serializable

/*
 * 数据库实体。金额字段统一为"分"（Long）。枚举以字符串保存，便于迁移与备份。
 * isDemo = true 表示演示数据，可一键清除。
 */

@Serializable
@Entity(tableName = "brands")
data class Brand(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "ips",
    foreignKeys = [ForeignKey(entity = Brand::class, parentColumns = ["id"], childColumns = ["brandId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("brandId")],
)
data class IpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandId: Long,
    val name: String,
    val description: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(entity = Brand::class, parentColumns = ["id"], childColumns = ["brandId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IpEntity::class, parentColumns = ["id"], childColumns = ["ipId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("brandId"), Index("ipId")],
)
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandId: Long,
    val ipId: Long,
    val name: String,
    val releaseYear: Int,
    val description: String = "",
    /** 单盒参考价（分），仅作为资料展示 */
    val boxPrice: Long = 0,
    val currency: String = "CNY",
    val themeColor: Long = 0xFFB9A8FF,
    val accentColor: Long = 0xFFFFB3D2,
    /** 虚拟开盒概率（万分之一），四档之和为 10000 */
    val rateR: Int = RateConfig.DEFAULT.r,
    val rateSR: Int = RateConfig.DEFAULT.sr,
    val rateSSR: Int = RateConfig.DEFAULT.ssr,
    val rateUR: Int = RateConfig.DEFAULT.ur,
    val blindBoxEnabled: Boolean = true,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val rates: RateConfig get() = RateConfig(rateR, rateSR, rateSSR, rateUR)
}

@Serializable
@Entity(
    tableName = "figures",
    foreignKeys = [ForeignKey(entity = Series::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("seriesId")],
)
data class Figure(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val name: String,
    /** 系列内编号，例如 "No.03" */
    val code: String = "",
    val rarity: String = Rarity.R.name,
    /** 程序化占位插画的种子与配色 */
    val artSeed: Int = 0,
    val colorA: Long = 0xFFB9A8FF,
    val colorB: Long = 0xFFFFC6DD,
    /** 用户上传的款式图（应用私有目录中的文件名） */
    val imagePath: String? = null,
    val description: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val rarityEnum: Rarity get() = Rarity.fromName(rarity)
}

@Serializable
@Entity(
    tableName = "collection_items",
    foreignKeys = [ForeignKey(entity = Figure::class, parentColumns = ["id"], childColumns = ["figureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("figureId")],
)
data class CollectionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val figureId: Long,
    val quantity: Int = 1,
    val unitPrice: Long = 0,
    val currency: String = "CNY",
    /** 物流及其他成本（本条记录合计） */
    val extraCost: Long = 0,
    val acquiredEpochDay: Long,
    val channel: String = "",
    /** 当前手动估算单价，null = 未填写 */
    val estimatedValue: Long? = null,
    val status: String = ItemStatus.OWNED.name,
    val condition: String = "全新",
    val hasBox: Boolean = true,
    val rating: Int = 0,
    val story: String = "",
    /** 实拍图文件名，以换行分隔 */
    val photoPaths: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val statusEnum: ItemStatus get() = ItemStatus.fromName(status)
    val photos: List<String> get() = photoPaths.split("\n").filter { it.isNotBlank() }
}

@Serializable
@Entity(
    tableName = "collection_transactions",
    foreignKeys = [ForeignKey(entity = CollectionItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("figureId")],
)
data class CollectionTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val figureId: Long,
    val type: String = TxType.BUY.name,
    val quantity: Int,
    val unitPrice: Long,
    val currency: String = "CNY",
    /** 购入：物流等其他成本；售出：手续费 */
    val fee: Long = 0,
    /** 售出时，售出部分的成本 */
    val costBasis: Long = 0,
    val epochDay: Long,
    val platform: String = "",
    val note: String = "",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val typeEnum: TxType get() = TxType.fromName(type)
}

@Serializable
@Entity(
    tableName = "wishlist",
    foreignKeys = [ForeignKey(entity = Figure::class, parentColumns = ["id"], childColumns = ["figureId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("figureId")],
)
data class WishlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val figureId: Long? = null,
    val name: String,
    val seriesName: String = "",
    val brandName: String = "",
    val targetPrice: Long? = null,
    val currency: String = "CNY",
    val desiredQuantity: Int = 1,
    /** 1 = 高，2 = 中，3 = 低 */
    val priority: Int = 2,
    val note: String = "",
    val purchased: Boolean = false,
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 虚拟收藏：虚拟开盒获得的藏品，与实物收藏完全隔离 */
@Serializable
@Entity(
    tableName = "virtual_collection",
    foreignKeys = [ForeignKey(entity = Figure::class, parentColumns = ["id"], childColumns = ["figureId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("figureId")],
)
data class VirtualCollectionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val figureId: Long,
    val openingId: Long? = null,
    val obtainedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "blind_box_openings",
    foreignKeys = [
        ForeignKey(entity = Series::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Figure::class, parentColumns = ["id"], childColumns = ["figureId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("seriesId"), Index("figureId"), Index("batchId")],
)
data class BlindBoxOpening(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seriesId: Long,
    val figureId: Long,
    val rarity: String,
    val batchId: Long,
    val batchSize: Int,
    /** 开盒时该款式的设定概率（万分之一 × 100，便于追溯），即概率 × 1,000,000 */
    val probabilityPpm: Int = 0,
    val savedToVirtual: Boolean = false,
    val openedAt: Long = System.currentTimeMillis(),
) {
    val rarityEnum: Rarity get() = Rarity.fromName(rarity)
}

@Serializable
@Entity(tableName = "achievements")
data class AchievementUnlock(
    @PrimaryKey val id: String,
    val unlockedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "display_cabinets")
data class DisplayCabinet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** 背景主题：LAVENDER / STARRY / SAKURA / GOLD / CREAM */
    val theme: String = "LAVENDER",
    /** 布局：SHELF / GRID */
    val layout: String = "SHELF",
    val isDemo: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "cabinet_items",
    primaryKeys = ["cabinetId", "figureId"],
    foreignKeys = [
        ForeignKey(entity = DisplayCabinet::class, parentColumns = ["id"], childColumns = ["cabinetId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Figure::class, parentColumns = ["id"], childColumns = ["figureId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("figureId")],
)
data class CabinetItem(
    val cabinetId: Long,
    val figureId: Long,
    val position: Int = 0,
)

@Serializable
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    val nickname: String = "潮玩收藏家",
    val avatarPath: String? = null,
    val bio: String = "每一次拆盒都有惊喜",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "check_ins")
data class CheckIn(
    @PrimaryKey val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

/** 重复款交换信息 */
@Serializable
@Entity(
    tableName = "trade_listings",
    foreignKeys = [ForeignKey(entity = Figure::class, parentColumns = ["id"], childColumns = ["figureId"], onDelete = ForeignKey.CASCADE)],
)
data class TradeListing(
    @PrimaryKey val figureId: Long,
    val tradeQuantity: Int = 1,
    val wantText: String = "",
    val note: String = "",
    val condition: String = "全新",
    val hasBox: Boolean = true,
    val isDemo: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)
