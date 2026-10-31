package com.boxverse.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** 款式 + 所属系列/品牌/IP + 实物持有数量 + 虚拟数量 */
data class FigureRow(
    @Embedded val figure: Figure,
    val seriesName: String,
    val brandName: String,
    val ipName: String,
    val brandId: Long,
    val ipId: Long,
    val releaseYear: Int,
    val ownedQty: Int,
    val virtualQty: Int,
    val seriesDemo: Boolean,
)

data class SeriesRow(
    @Embedded val series: Series,
    val brandName: String,
    val ipName: String,
    val figureCount: Int,
)

/** 实物记录 + 款式信息 */
data class HoldingRow(
    @Embedded val item: CollectionItem,
    val figureName: String,
    val figureCode: String,
    val rarity: String,
    val artSeed: Int,
    val colorA: Long,
    val colorB: Long,
    val figureImage: String?,
    val seriesId: Long,
    val seriesName: String,
    val brandName: String,
)

private const val FIGURE_ROW_SELECT = """
    SELECT f.*, s.name AS seriesName, b.name AS brandName, i.name AS ipName,
        s.brandId AS brandId, s.ipId AS ipId, s.releaseYear AS releaseYear, s.isDemo AS seriesDemo,
        COALESCE((SELECT SUM(c.quantity) FROM collection_items c
                  WHERE c.figureId = f.id AND c.status IN ('OWNED','SHIPPING')), 0) AS ownedQty,
        (SELECT COUNT(*) FROM virtual_collection v WHERE v.figureId = f.id) AS virtualQty
    FROM figures f
    JOIN series s ON s.id = f.seriesId
    JOIN brands b ON b.id = s.brandId
    JOIN ips i ON i.id = s.ipId
"""

private const val HOLDING_ROW_SELECT = """
    SELECT c.*, f.name AS figureName, f.code AS figureCode, f.rarity AS rarity, f.artSeed AS artSeed,
        f.colorA AS colorA, f.colorB AS colorB, f.imagePath AS figureImage,
        s.id AS seriesId, s.name AS seriesName, b.name AS brandName
    FROM collection_items c
    JOIN figures f ON f.id = c.figureId
    JOIN series s ON s.id = f.seriesId
    JOIN brands b ON b.id = s.brandId
"""

@Dao
abstract class BoxDao {

    // ---------- 品牌 / IP ----------
    @Insert
    abstract suspend fun insertBrand(brand: Brand): Long

    @Query("SELECT * FROM brands WHERE name = :name LIMIT 1")
    abstract suspend fun findBrand(name: String): Brand?

    @Query("SELECT * FROM brands ORDER BY name")
    abstract fun observeBrands(): Flow<List<Brand>>

    @Insert
    abstract suspend fun insertIp(ip: IpEntity): Long

    @Query("SELECT * FROM ips WHERE brandId = :brandId AND name = :name LIMIT 1")
    abstract suspend fun findIp(brandId: Long, name: String): IpEntity?

    @Query("SELECT * FROM ips ORDER BY name")
    abstract fun observeIps(): Flow<List<IpEntity>>

    // ---------- 系列 ----------
    @Insert
    abstract suspend fun insertSeries(series: Series): Long

    @Update
    abstract suspend fun updateSeries(series: Series)

    @Delete
    abstract suspend fun deleteSeries(series: Series)

    @Query("SELECT * FROM series WHERE id = :id")
    abstract suspend fun getSeries(id: Long): Series?

    @Query("SELECT * FROM series WHERE brandId = :brandId AND name = :name LIMIT 1")
    abstract suspend fun findSeries(brandId: Long, name: String): Series?

    @Query(
        """
        SELECT s.*, b.name AS brandName, i.name AS ipName,
            (SELECT COUNT(*) FROM figures f WHERE f.seriesId = s.id) AS figureCount
        FROM series s JOIN brands b ON b.id = s.brandId JOIN ips i ON i.id = s.ipId
        ORDER BY s.createdAt DESC
        """
    )
    abstract fun observeSeriesRows(): Flow<List<SeriesRow>>

    // ---------- 款式 ----------
    @Insert
    abstract suspend fun insertFigure(figure: Figure): Long

    @Update
    abstract suspend fun updateFigure(figure: Figure)

    @Delete
    abstract suspend fun deleteFigure(figure: Figure)

    @Query("SELECT * FROM figures WHERE id = :id")
    abstract suspend fun getFigure(id: Long): Figure?

    @Query("SELECT * FROM figures WHERE seriesId = :seriesId ORDER BY code, id")
    abstract suspend fun getFiguresOfSeries(seriesId: Long): List<Figure>

    @Query("$FIGURE_ROW_SELECT ORDER BY s.createdAt DESC, f.code, f.id")
    abstract fun observeFigureRows(): Flow<List<FigureRow>>

    // ---------- 实物收藏 ----------
    @Insert
    abstract suspend fun insertItem(item: CollectionItem): Long

    @Update
    abstract suspend fun updateItem(item: CollectionItem)

    @Delete
    abstract suspend fun deleteItem(item: CollectionItem)

    @Query("SELECT * FROM collection_items WHERE id = :id")
    abstract suspend fun getItem(id: Long): CollectionItem?

    @Query("$HOLDING_ROW_SELECT ORDER BY c.acquiredEpochDay DESC, c.id DESC")
    abstract fun observeHoldingRows(): Flow<List<HoldingRow>>

    // ---------- 交易记录 ----------
    @Insert
    abstract suspend fun insertTransaction(tx: CollectionTransaction): Long

    @Update
    abstract suspend fun updateTransaction(tx: CollectionTransaction)

    @Query("SELECT * FROM collection_transactions WHERE itemId = :itemId AND type = 'BUY' ORDER BY id LIMIT 1")
    abstract suspend fun getBuyTransaction(itemId: Long): CollectionTransaction?

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM collection_transactions WHERE itemId = :itemId AND type = 'SELL'")
    abstract suspend fun soldQuantity(itemId: Long): Int

    @Query("SELECT * FROM collection_transactions ORDER BY epochDay DESC, id DESC")
    abstract fun observeTransactions(): Flow<List<CollectionTransaction>>

    // ---------- 心愿清单 ----------
    @Insert
    abstract suspend fun insertWish(item: WishlistItem): Long

    @Update
    abstract suspend fun updateWish(item: WishlistItem)

    @Delete
    abstract suspend fun deleteWish(item: WishlistItem)

    @Query("SELECT * FROM wishlist WHERE id = :id")
    abstract suspend fun getWish(id: Long): WishlistItem?

    @Query("SELECT * FROM wishlist ORDER BY purchased, priority, createdAt DESC")
    abstract fun observeWishlist(): Flow<List<WishlistItem>>

    // ---------- 虚拟收藏与开盒 ----------
    @Insert
    abstract suspend fun insertOpenings(list: List<BlindBoxOpening>): List<Long>

    @Query("SELECT * FROM blind_box_openings ORDER BY openedAt DESC, id DESC")
    abstract fun observeOpenings(): Flow<List<BlindBoxOpening>>

    @Query("SELECT * FROM blind_box_openings WHERE batchId = :batchId AND savedToVirtual = 0")
    abstract suspend fun unsavedOpeningsOfBatch(batchId: Long): List<BlindBoxOpening>

    @Query("UPDATE blind_box_openings SET savedToVirtual = 1 WHERE batchId = :batchId")
    abstract suspend fun markBatchSaved(batchId: Long)

    @Insert
    abstract suspend fun insertVirtualItems(list: List<VirtualCollectionItem>)

    @Query("SELECT * FROM virtual_collection ORDER BY obtainedAt DESC")
    abstract fun observeVirtualItems(): Flow<List<VirtualCollectionItem>>

    @Query("DELETE FROM virtual_collection")
    abstract suspend fun clearVirtualCollection()

    @Query("DELETE FROM blind_box_openings")
    abstract suspend fun clearOpenings()

    // ---------- 成就 ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAchievements(list: List<AchievementUnlock>)

    @Query("SELECT * FROM achievements")
    abstract fun observeAchievements(): Flow<List<AchievementUnlock>>

    // ---------- 收藏柜 ----------
    @Insert
    abstract suspend fun insertCabinet(cabinet: DisplayCabinet): Long

    @Update
    abstract suspend fun updateCabinet(cabinet: DisplayCabinet)

    @Delete
    abstract suspend fun deleteCabinet(cabinet: DisplayCabinet)

    @Query("SELECT * FROM display_cabinets ORDER BY createdAt DESC")
    abstract fun observeCabinets(): Flow<List<DisplayCabinet>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCabinetItems(items: List<CabinetItem>)

    @Query("DELETE FROM cabinet_items WHERE cabinetId = :cabinetId AND figureId = :figureId")
    abstract suspend fun removeCabinetItem(cabinetId: Long, figureId: Long)

    @Query("SELECT * FROM cabinet_items ORDER BY position")
    abstract fun observeCabinetItems(): Flow<List<CabinetItem>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM cabinet_items WHERE cabinetId = :cabinetId")
    abstract suspend fun maxCabinetPosition(cabinetId: Long): Int

    // ---------- 用户资料 / 签到 ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertProfile(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = 1")
    abstract fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    abstract suspend fun getProfile(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertCheckIn(checkIn: CheckIn)

    @Query("SELECT * FROM check_ins ORDER BY epochDay DESC")
    abstract fun observeCheckIns(): Flow<List<CheckIn>>

    // ---------- 交换 ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertTrade(listing: TradeListing)

    @Query("DELETE FROM trade_listings WHERE figureId = :figureId")
    abstract suspend fun deleteTrade(figureId: Long)

    @Query("SELECT * FROM trade_listings ORDER BY updatedAt DESC")
    abstract fun observeTrades(): Flow<List<TradeListing>>

    // ---------- 演示数据 ----------
    @Query("SELECT COUNT(*) FROM figures WHERE isDemo = 1")
    abstract fun observeDemoFigureCount(): Flow<Int>

    @Transaction
    open suspend fun clearDemoData() {
        deleteDemoItems()
        deleteDemoWishes()
        deleteDemoTrades()
        deleteDemoCabinets()
        // 用户在演示款式上添加了真实收藏时，保留这些款式并转为正式数据
        promoteFiguresWithRealItems()
        promoteSeriesWithRealFigures()
        promoteIpsWithRealSeries()
        promoteBrandsWithRealSeries()
        deleteDemoFigures()
        deleteDemoSeries()
        deleteDemoIps()
        deleteDemoBrands()
    }

    @Query("DELETE FROM collection_items WHERE isDemo = 1")
    abstract suspend fun deleteDemoItems()

    @Query("DELETE FROM wishlist WHERE isDemo = 1")
    abstract suspend fun deleteDemoWishes()

    @Query("DELETE FROM trade_listings WHERE isDemo = 1")
    abstract suspend fun deleteDemoTrades()

    @Query("DELETE FROM display_cabinets WHERE isDemo = 1")
    abstract suspend fun deleteDemoCabinets()

    @Query("UPDATE figures SET isDemo = 0 WHERE isDemo = 1 AND id IN (SELECT figureId FROM collection_items)")
    abstract suspend fun promoteFiguresWithRealItems()

    @Query("UPDATE series SET isDemo = 0 WHERE isDemo = 1 AND id IN (SELECT seriesId FROM figures WHERE isDemo = 0)")
    abstract suspend fun promoteSeriesWithRealFigures()

    @Query("UPDATE ips SET isDemo = 0 WHERE isDemo = 1 AND id IN (SELECT ipId FROM series WHERE isDemo = 0)")
    abstract suspend fun promoteIpsWithRealSeries()

    @Query("UPDATE brands SET isDemo = 0 WHERE isDemo = 1 AND (id IN (SELECT brandId FROM series WHERE isDemo = 0) OR id IN (SELECT brandId FROM ips WHERE isDemo = 0))")
    abstract suspend fun promoteBrandsWithRealSeries()

    @Query("DELETE FROM figures WHERE isDemo = 1")
    abstract suspend fun deleteDemoFigures()

    @Query("DELETE FROM series WHERE isDemo = 1")
    abstract suspend fun deleteDemoSeries()

    @Query("DELETE FROM ips WHERE isDemo = 1")
    abstract suspend fun deleteDemoIps()

    @Query("DELETE FROM brands WHERE isDemo = 1")
    abstract suspend fun deleteDemoBrands()

    // ---------- 备份 / 恢复 ----------
    @Query("SELECT * FROM brands") abstract suspend fun allBrands(): List<Brand>
    @Query("SELECT * FROM ips") abstract suspend fun allIps(): List<IpEntity>
    @Query("SELECT * FROM series") abstract suspend fun allSeries(): List<Series>
    @Query("SELECT * FROM figures") abstract suspend fun allFigures(): List<Figure>
    @Query("SELECT * FROM collection_items") abstract suspend fun allItems(): List<CollectionItem>
    @Query("SELECT * FROM collection_transactions") abstract suspend fun allTransactions(): List<CollectionTransaction>
    @Query("SELECT * FROM wishlist") abstract suspend fun allWishes(): List<WishlistItem>
    @Query("SELECT * FROM virtual_collection") abstract suspend fun allVirtual(): List<VirtualCollectionItem>
    @Query("SELECT * FROM blind_box_openings") abstract suspend fun allOpenings(): List<BlindBoxOpening>
    @Query("SELECT * FROM achievements") abstract suspend fun allAchievements(): List<AchievementUnlock>
    @Query("SELECT * FROM display_cabinets") abstract suspend fun allCabinets(): List<DisplayCabinet>
    @Query("SELECT * FROM cabinet_items") abstract suspend fun allCabinetItems(): List<CabinetItem>
    @Query("SELECT * FROM check_ins") abstract suspend fun allCheckIns(): List<CheckIn>
    @Query("SELECT * FROM trade_listings") abstract suspend fun allTrades(): List<TradeListing>

    @Insert abstract suspend fun restoreBrands(list: List<Brand>)
    @Insert abstract suspend fun restoreIps(list: List<IpEntity>)
    @Insert abstract suspend fun restoreSeries(list: List<Series>)
    @Insert abstract suspend fun restoreFigures(list: List<Figure>)
    @Insert abstract suspend fun restoreItems(list: List<CollectionItem>)
    @Insert abstract suspend fun restoreTransactions(list: List<CollectionTransaction>)
    @Insert abstract suspend fun restoreWishes(list: List<WishlistItem>)
    @Insert abstract suspend fun restoreVirtual(list: List<VirtualCollectionItem>)
    @Insert abstract suspend fun restoreOpenings(list: List<BlindBoxOpening>)
    @Insert abstract suspend fun restoreAchievements(list: List<AchievementUnlock>)
    @Insert abstract suspend fun restoreCabinets(list: List<DisplayCabinet>)
    @Insert abstract suspend fun restoreCabinetItems(list: List<CabinetItem>)
    @Insert abstract suspend fun restoreCheckIns(list: List<CheckIn>)
    @Insert abstract suspend fun restoreTrades(list: List<TradeListing>)

    @Query("DELETE FROM cabinet_items") abstract suspend fun wipeCabinetItems()
    @Query("DELETE FROM display_cabinets") abstract suspend fun wipeCabinets()
    @Query("DELETE FROM trade_listings") abstract suspend fun wipeTrades()
    @Query("DELETE FROM virtual_collection") abstract suspend fun wipeVirtual()
    @Query("DELETE FROM blind_box_openings") abstract suspend fun wipeOpenings()
    @Query("DELETE FROM collection_transactions") abstract suspend fun wipeTransactions()
    @Query("DELETE FROM collection_items") abstract suspend fun wipeItems()
    @Query("DELETE FROM wishlist") abstract suspend fun wipeWishes()
    @Query("DELETE FROM figures") abstract suspend fun wipeFigures()
    @Query("DELETE FROM series") abstract suspend fun wipeSeries()
    @Query("DELETE FROM ips") abstract suspend fun wipeIps()
    @Query("DELETE FROM brands") abstract suspend fun wipeBrands()
    @Query("DELETE FROM achievements") abstract suspend fun wipeAchievements()
    @Query("DELETE FROM check_ins") abstract suspend fun wipeCheckIns()
    @Query("DELETE FROM user_profile") abstract suspend fun wipeProfile()

    @Transaction
    open suspend fun wipeAll() {
        wipeCabinetItems(); wipeCabinets(); wipeTrades(); wipeVirtual(); wipeOpenings()
        wipeTransactions(); wipeItems(); wipeWishes(); wipeFigures(); wipeSeries(); wipeIps(); wipeBrands()
        wipeAchievements(); wipeCheckIns(); wipeProfile()
    }
}
