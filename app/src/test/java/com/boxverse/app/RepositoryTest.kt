package com.boxverse.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boxverse.app.data.db.BoxDatabase
import com.boxverse.app.data.db.CollectionItem
import com.boxverse.app.data.prefs.SettingsRepository
import com.boxverse.app.data.repo.BoxRepository
import com.boxverse.app.data.repo.DemoData
import com.boxverse.app.domain.ItemStatus
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.TxType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

/** 数据层集成测试：真实 Room 数据库（内存），验证收藏、交易、开盒与虚拟/实物隔离 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = android.app.Application::class)
class RepositoryTest {
    private lateinit var db: BoxDatabase
    private lateinit var repo: BoxRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BoxDatabase::class.java).allowMainThreadQueries().build()
        repo = BoxRepository(db, SettingsRepository(context))
        runBlocking { DemoData.seed(db.dao()) }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun demoDataIsSeededAndMarked() = runBlocking {
        val series = repo.seriesRows.first()
        assertEquals(4, series.size)
        assertTrue(series.all { it.series.isDemo })
        val figures = repo.figureRows.first()
        assertTrue(figures.any { it.figure.rarityEnum == Rarity.SSR })
        assertTrue(figures.any { it.figure.rarityEnum == Rarity.UR })
        // 午后咖啡馆：重复款"摩卡小憩"持有 3 件
        assertEquals(3, figures.first { it.figure.name == "摩卡小憩" }.ownedQty)
    }

    @Test
    fun saveEditAndSellItemKeepsTransactionsConsistent() = runBlocking {
        val figure = repo.figureRows.first().first { it.figure.name == "柠檬挞" }.figure
        val id = repo.saveItem(CollectionItem(figureId = figure.id, quantity = 2, unitPrice = 6900, extraCost = 1000, acquiredEpochDay = 20000))
        var txs = repo.transactions.first().filter { it.itemId == id }
        assertEquals(1, txs.size)
        assertEquals(2, txs.first().quantity)

        // 修改数量为 3
        val item = repo.getItem(id)!!
        repo.saveItem(item.copy(quantity = 3))
        txs = repo.transactions.first().filter { it.itemId == id }
        assertEquals(3, txs.first { it.typeEnum == TxType.BUY }.quantity)

        // 售出 1 件
        val result = repo.sellItem(id, 1, 9900, 300, 20010, "二手平台", "")
        assertEquals(6900 + 1000 / 3L, result.costBasis)
        val after = repo.getItem(id)!!
        assertEquals(2, after.quantity)
        val buy = repo.transactions.first().first { it.itemId == id && it.typeEnum == TxType.BUY }
        repo.saveItem(after.copy(story = "edited"))
        // 编辑后购入数量 = 剩余 2 + 已售 1
        assertEquals(3, repo.transactions.first().first { it.id == buy.id }.quantity)
        assertEquals(3, repo.figureRows.first().first { it.figure.id == figure.id }.ownedQty + 1)

        // 全部售出后状态为已售出
        repo.sellItem(id, 2, 9900, 0, 20011, "", "")
        val sold = repo.getItem(id)!!
        assertEquals(0, sold.quantity)
        assertEquals(ItemStatus.SOLD, sold.statusEnum)
    }

    @Test
    fun virtualOpeningsAreIsolatedFromRealCollection() = runBlocking {
        val seriesId = repo.seriesRows.first().first { it.series.name == "云朵甜品店" }.series.id
        val holdingsBefore = repo.holdingRows.first().size
        val ownedBefore = repo.figureRows.first().sumOf { it.ownedQty }

        val results = repo.openBoxes(seriesId, 10, Random(7))
        assertEquals(10, results.size)
        assertEquals(10, repo.openings.first().size)
        assertTrue(results.all { it.figure.seriesId == seriesId })

        repo.saveBatchToVirtual(results.first().opening.batchId)
        assertEquals(10, repo.virtualItems.first().size)
        assertTrue(repo.openings.first().all { it.savedToVirtual })
        // 再次保存不会重复写入
        repo.saveBatchToVirtual(results.first().opening.batchId)
        assertEquals(10, repo.virtualItems.first().size)

        // 实物收藏完全不受影响
        assertEquals(holdingsBefore, repo.holdingRows.first().size)
        assertEquals(ownedBefore, repo.figureRows.first().sumOf { it.ownedQty })
        val stats = repo.collectorStats.first()
        assertEquals(10, stats.virtualOpenings)
        assertEquals(0, stats.totalQuantity) // 演示数据与虚拟数据都不计入等级统计
    }

    @Test
    fun clearDemoKeepsFiguresWithRealItems() = runBlocking {
        val figure = repo.figureRows.first().first { it.figure.name == "香芋泡芙" }.figure
        repo.saveItem(CollectionItem(figureId = figure.id, quantity = 1, unitPrice = 6900, acquiredEpochDay = 20000))
        repo.clearDemoData()

        val figures = repo.figureRows.first()
        assertEquals(1, figures.size)
        assertEquals("香芋泡芙", figures.first().figure.name)
        assertFalse(figures.first().figure.isDemo)
        assertEquals(1, repo.holdingRows.first().size)
        val series = repo.seriesRows.first()
        assertEquals(1, series.size)
        assertFalse(series.first().series.isDemo)
        assertTrue(repo.wishlist.first().none { it.isDemo })
        assertTrue(repo.cabinets.first().isEmpty())
    }
}
