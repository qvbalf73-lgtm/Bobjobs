package com.boxverse.app.data.repo

import androidx.room.withTransaction
import com.boxverse.app.data.db.AchievementUnlock
import com.boxverse.app.data.db.BlindBoxOpening
import com.boxverse.app.data.db.BoxDatabase
import com.boxverse.app.data.db.Brand
import com.boxverse.app.data.db.CabinetItem
import com.boxverse.app.data.db.CheckIn
import com.boxverse.app.data.db.CollectionItem
import com.boxverse.app.data.db.CollectionTransaction
import com.boxverse.app.data.db.DisplayCabinet
import com.boxverse.app.data.db.Figure
import com.boxverse.app.data.db.FigureRow
import com.boxverse.app.data.db.HoldingRow
import com.boxverse.app.data.db.IpEntity
import com.boxverse.app.data.db.Series
import com.boxverse.app.data.db.TradeListing
import com.boxverse.app.data.db.UserProfile
import com.boxverse.app.data.db.VirtualCollectionItem
import com.boxverse.app.data.db.WishlistItem
import com.boxverse.app.data.prefs.SettingsRepository
import com.boxverse.app.domain.Achievements
import com.boxverse.app.domain.BlindBoxEngine
import com.boxverse.app.domain.CollectorStats
import com.boxverse.app.domain.FigureRef
import com.boxverse.app.domain.HoldingRecord
import com.boxverse.app.domain.ItemStatus
import com.boxverse.app.domain.PoolFigure
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.StatsCalculator
import com.boxverse.app.domain.TxRecord
import com.boxverse.app.domain.TxType
import com.boxverse.app.util.Dates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.random.Random

/** 开盒结果 */
data class DrawResult(val opening: BlindBoxOpening, val figure: Figure)

fun HoldingRow.toRecord(): HoldingRecord = HoldingRecord(
    itemId = item.id,
    figureId = item.figureId,
    figureName = figureName,
    seriesId = seriesId,
    seriesName = seriesName,
    brandName = brandName,
    rarity = Rarity.fromName(rarity),
    quantity = item.quantity,
    unitPrice = item.unitPrice,
    extraCost = item.extraCost,
    currency = item.currency,
    estimatedUnitValue = item.estimatedValue,
    status = item.statusEnum,
    acquiredEpochDay = item.acquiredEpochDay,
    isDemo = item.isDemo,
)

fun CollectionTransaction.toRecord(): TxRecord =
    TxRecord(typeEnum, quantity, unitPrice, fee, costBasis, currency, epochDay)

fun FigureRow.toRef(): FigureRef = FigureRef(figure.id, figure.seriesId, figure.rarityEnum)

class BoxRepository(
    private val db: BoxDatabase,
    val settings: SettingsRepository,
) {
    private val dao = db.dao()

    // ---------- 观察数据 ----------
    val figureRows: Flow<List<FigureRow>> = dao.observeFigureRows()
    val seriesRows = dao.observeSeriesRows()
    val holdingRows: Flow<List<HoldingRow>> = dao.observeHoldingRows()
    val transactions = dao.observeTransactions()
    val wishlist = dao.observeWishlist()
    val openings = dao.observeOpenings()
    val virtualItems = dao.observeVirtualItems()
    val achievementUnlocks = dao.observeAchievements()
    val cabinets = dao.observeCabinets()
    val cabinetItems = dao.observeCabinetItems()
    val profile = dao.observeProfile()
    val checkIns = dao.observeCheckIns()
    val trades = dao.observeTrades()
    val brands = dao.observeBrands()
    val ips = dao.observeIps()
    val demoFigureCount = dao.observeDemoFigureCount()

    /**
     * 收藏家统计（用于经验与成就）：只统计正式数据，演示数据不计入；
     * 虚拟开盒数据单独统计，不会进入实物数量或资产估值。
     */
    val collectorStats: Flow<CollectorStats> = combine(
        combine(holdingRows, figureRows, checkIns) { h, f, c -> Triple(h, f, c) },
        combine(openings, wishlist, cabinets, trades) { o, w, cab, t -> listOf(o, w, cab, t) },
        settings.settings,
    ) { (holdings, figures, checks), others, appSettings ->
        @Suppress("UNCHECKED_CAST")
        val openingList = others[0] as List<BlindBoxOpening>
        val wishList = others[1] as List<WishlistItem>
        val cabinetList = others[2] as List<DisplayCabinet>
        val tradeList = others[3] as List<TradeListing>
        val real = holdings.filter { !it.item.isDemo }.map { it.toRecord() }
        val ownedQty = StatsCalculator.ownedQuantityByFigure(real)
        val ownedIds = ownedQty.keys
        val progress = StatsCalculator.seriesProgress(figures.map { it.toRef() }, ownedIds)
        val rarityById = figures.associate { it.figure.id to it.figure.rarityEnum }
        val days = checks.map { it.epochDay }.toSet()
        CollectorStats(
            totalQuantity = ownedQty.values.sum(),
            distinctFigures = ownedIds.size,
            completedSeries = progress.values.count { it.isComplete },
            hiddenOwned = ownedIds.count { rarityById[it]?.isHidden == true },
            checkInStreak = StatsCalculator.checkInStreak(days, Dates.today()),
            totalCheckIns = days.size,
            virtualOpenings = openingList.size,
            virtualHidden = openingList.count { it.rarityEnum.isHidden },
            virtualUr = openingList.count { it.rarityEnum == Rarity.UR },
            wishlistCount = wishList.count { !it.isDemo },
            cabinetCount = cabinetList.count { !it.isDemo },
            tradeListings = tradeList.count { !it.isDemo },
            estimatedValueBase = StatsCalculator.estimatedValue(real, appSettings.converter).total,
        )
    }

    // ---------- 初始化 ----------
    suspend fun ensureInitialized() {
        if (dao.getProfile() == null) dao.upsertProfile(UserProfile())
        if (!settings.current().seeded) {
            db.withTransaction { DemoData.seed(dao) }
            settings.setSeeded(true)
        }
    }

    // ---------- 品牌 / IP / 系列 ----------
    /** 按名称查找或创建品牌、IP 与系列，返回系列 id */
    suspend fun findOrCreateSeries(brandName: String, ipName: String, seriesName: String, year: Int): Long =
        db.withTransaction {
            val brand = dao.findBrand(brandName.trim()) ?: Brand(name = brandName.trim()).let { it.copy(id = dao.insertBrand(it)) }
            val ipLabel = ipName.trim().ifBlank { brand.name }
            val ip = dao.findIp(brand.id, ipLabel) ?: IpEntity(brandId = brand.id, name = ipLabel).let { it.copy(id = dao.insertIp(it)) }
            val existing = dao.findSeries(brand.id, seriesName.trim())
            existing?.id ?: dao.insertSeries(
                Series(brandId = brand.id, ipId = ip.id, name = seriesName.trim(), releaseYear = year, blindBoxEnabled = true)
            )
        }

    suspend fun getSeries(id: Long) = dao.getSeries(id)

    /** 新建或更新系列；品牌和 IP 按名称自动关联 */
    suspend fun saveSeries(series: Series, brandName: String, ipName: String): Long = db.withTransaction {
        val brand = dao.findBrand(brandName.trim()) ?: Brand(name = brandName.trim()).let { it.copy(id = dao.insertBrand(it)) }
        val ipLabel = ipName.trim().ifBlank { brand.name }
        val ip = dao.findIp(brand.id, ipLabel) ?: IpEntity(brandId = brand.id, name = ipLabel).let { it.copy(id = dao.insertIp(it)) }
        val s = series.copy(brandId = brand.id, ipId = ip.id)
        if (s.id == 0L) dao.insertSeries(s) else {
            dao.updateSeries(s); s.id
        }
    }

    suspend fun updateSeries(series: Series) = dao.updateSeries(series)
    suspend fun deleteSeries(series: Series) = dao.deleteSeries(series)

    // ---------- 款式 ----------
    suspend fun getFigure(id: Long) = dao.getFigure(id)
    suspend fun figuresOfSeries(seriesId: Long) = dao.getFiguresOfSeries(seriesId)

    suspend fun saveFigure(figure: Figure): Long =
        if (figure.id == 0L) dao.insertFigure(figure) else {
            dao.updateFigure(figure); figure.id
        }

    suspend fun deleteFigure(figure: Figure) = dao.deleteFigure(figure)

    // ---------- 实物收藏 ----------
    suspend fun getItem(id: Long) = dao.getItem(id)

    /** 保存实物记录，并同步维护对应的"购入"交易记录 */
    suspend fun saveItem(item: CollectionItem): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        if (item.id == 0L) {
            val id = dao.insertItem(item.copy(createdAt = now, updatedAt = now))
            dao.insertTransaction(
                CollectionTransaction(
                    itemId = id, figureId = item.figureId, type = TxType.BUY.name, quantity = item.quantity,
                    unitPrice = item.unitPrice, currency = item.currency, fee = item.extraCost,
                    epochDay = item.acquiredEpochDay, platform = item.channel, isDemo = item.isDemo,
                )
            )
            id
        } else {
            dao.updateItem(item.copy(updatedAt = now))
            val sold = dao.soldQuantity(item.id)
            val buy = dao.getBuyTransaction(item.id)
            if (buy != null) {
                dao.updateTransaction(
                    buy.copy(
                        figureId = item.figureId, quantity = item.quantity + sold, unitPrice = item.unitPrice,
                        currency = item.currency, fee = item.extraCost, epochDay = item.acquiredEpochDay, platform = item.channel,
                    )
                )
            } else {
                dao.insertTransaction(
                    CollectionTransaction(
                        itemId = item.id, figureId = item.figureId, type = TxType.BUY.name, quantity = item.quantity + sold,
                        unitPrice = item.unitPrice, currency = item.currency, fee = item.extraCost,
                        epochDay = item.acquiredEpochDay, platform = item.channel,
                    )
                )
            }
            item.id
        }
    }

    suspend fun deleteItem(item: CollectionItem) = dao.deleteItem(item)

    /** 售出部分或全部实物，记录已实现盈亏 */
    suspend fun sellItem(itemId: Long, quantity: Int, unitPrice: Long, fee: Long, epochDay: Long, platform: String, note: String) =
        db.withTransaction {
            val item = dao.getItem(itemId) ?: error("记录不存在")
            val r = StatsCalculator.sell(item.quantity, item.unitPrice, item.extraCost, quantity, unitPrice, fee)
            dao.updateItem(
                item.copy(
                    quantity = r.remainingQuantity,
                    extraCost = r.remainingExtraCost,
                    status = if (r.remainingQuantity == 0) ItemStatus.SOLD.name else item.status,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            dao.insertTransaction(
                CollectionTransaction(
                    itemId = item.id, figureId = item.figureId, type = TxType.SELL.name, quantity = quantity,
                    unitPrice = unitPrice, currency = item.currency, fee = fee, costBasis = r.costBasis,
                    epochDay = epochDay, platform = platform, note = note, isDemo = item.isDemo,
                )
            )
            r
        }

    // ---------- 心愿清单 ----------
    suspend fun getWish(id: Long) = dao.getWish(id)
    suspend fun saveWish(item: WishlistItem) = if (item.id == 0L) dao.insertWish(item) else {
        dao.updateWish(item); item.id
    }
    suspend fun deleteWish(item: WishlistItem) = dao.deleteWish(item)

    // ---------- 虚拟开盒 ----------
    /**
     * 执行虚拟开盒：按系列配置的概率抽取，结果立即写入开盒历史（可追溯）。
     * 结果不会写入实物收藏表。
     */
    suspend fun openBoxes(seriesId: Long, count: Int, random: Random = Random.Default): List<DrawResult> = db.withTransaction {
        val series = dao.getSeries(seriesId) ?: error("系列不存在")
        val figures = dao.getFiguresOfSeries(seriesId)
        val pool = figures.map { PoolFigure(it.id, it.rarityEnum) }
        require(pool.isNotEmpty()) { "该系列还没有款式" }
        val batchId = System.currentTimeMillis()
        val draws = BlindBoxEngine.drawMany(count, pool, series.rates, random)
        val openings = draws.map { d ->
            val p = BlindBoxEngine.figureProbability(d, series.rates, pool)
            BlindBoxOpening(
                seriesId = seriesId, figureId = d.id, rarity = d.rarity.name, batchId = batchId,
                batchSize = count, probabilityPpm = (p * 1_000_000).toInt(),
            )
        }
        val ids = dao.insertOpenings(openings)
        val byId = figures.associateBy { it.id }
        openings.mapIndexed { i, o -> DrawResult(o.copy(id = ids[i]), byId.getValue(o.figureId)) }
    }

    /** 把一批开盒结果收入虚拟收藏柜 */
    suspend fun saveBatchToVirtual(batchId: Long) = db.withTransaction {
        val unsaved = dao.unsavedOpeningsOfBatch(batchId)
        dao.insertVirtualItems(unsaved.map { VirtualCollectionItem(figureId = it.figureId, openingId = it.id) })
        dao.markBatchSaved(batchId)
    }

    suspend fun clearBlindBoxHistory() = db.withTransaction {
        dao.clearVirtualCollection()
        dao.clearOpenings()
    }

    // ---------- 成就 / 签到 / 资料 ----------
    suspend fun unlockAchievements(ids: List<String>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.insertAchievements(ids.map { AchievementUnlock(it, now) })
    }

    fun newlyReached(stats: CollectorStats, unlocked: Set<String>) = Achievements.newlyReached(stats, unlocked)

    suspend fun checkIn() = dao.insertCheckIn(CheckIn(Dates.today()))

    suspend fun saveProfile(profile: UserProfile) = dao.upsertProfile(profile)

    // ---------- 收藏柜 ----------
    suspend fun saveCabinet(cabinet: DisplayCabinet): Long =
        if (cabinet.id == 0L) dao.insertCabinet(cabinet) else {
            dao.updateCabinet(cabinet); cabinet.id
        }

    suspend fun deleteCabinet(cabinet: DisplayCabinet) = dao.deleteCabinet(cabinet)

    suspend fun addToCabinet(cabinetId: Long, figureIds: List<Long>) {
        var pos = dao.maxCabinetPosition(cabinetId) + 1
        dao.insertCabinetItems(figureIds.map { CabinetItem(cabinetId, it, pos++) })
    }

    suspend fun removeFromCabinet(cabinetId: Long, figureId: Long) = dao.removeCabinetItem(cabinetId, figureId)

    // ---------- 交换 ----------
    suspend fun saveTrade(listing: TradeListing) = dao.upsertTrade(listing.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteTrade(figureId: Long) = dao.deleteTrade(figureId)

    // ---------- 演示数据 / 全部数据 ----------
    suspend fun clearDemoData() = dao.clearDemoData()

    suspend fun reloadDemoData(): Boolean = db.withTransaction {
        if (dao.allFigures().any { it.isDemo }) false else {
            DemoData.seed(dao); true
        }
    }

    suspend fun wipeAll() {
        dao.wipeAll()
        dao.upsertProfile(UserProfile())
    }

    internal val database: BoxDatabase get() = db
}
