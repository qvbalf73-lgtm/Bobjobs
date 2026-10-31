package com.boxverse.app.data.repo

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.boxverse.app.data.db.AchievementUnlock
import com.boxverse.app.data.db.BlindBoxOpening
import com.boxverse.app.data.db.Brand
import com.boxverse.app.data.db.CabinetItem
import com.boxverse.app.data.db.CheckIn
import com.boxverse.app.data.db.CollectionItem
import com.boxverse.app.data.db.CollectionTransaction
import com.boxverse.app.data.db.DisplayCabinet
import com.boxverse.app.data.db.Figure
import com.boxverse.app.data.db.IpEntity
import com.boxverse.app.data.db.Series
import com.boxverse.app.data.db.TradeListing
import com.boxverse.app.data.db.UserProfile
import com.boxverse.app.data.db.VirtualCollectionItem
import com.boxverse.app.data.db.WishlistItem
import com.boxverse.app.domain.Currency
import com.boxverse.app.domain.Money
import com.boxverse.app.util.Dates
import com.boxverse.app.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val formatVersion: Int = 1,
    val dbVersion: Int,
    val exportedAt: Long,
    val brands: List<Brand>,
    val ips: List<IpEntity>,
    val series: List<Series>,
    val figures: List<Figure>,
    val items: List<CollectionItem>,
    val transactions: List<CollectionTransaction>,
    val wishlist: List<WishlistItem>,
    val virtual: List<VirtualCollectionItem>,
    val openings: List<BlindBoxOpening>,
    val achievements: List<AchievementUnlock>,
    val cabinets: List<DisplayCabinet>,
    val cabinetItems: List<CabinetItem>,
    val profile: UserProfile?,
    val checkIns: List<CheckIn>,
    val trades: List<TradeListing>,
)

/** 数据备份（zip：data.json + 图片）、恢复与 CSV 导出 */
class BackupManager(private val context: Context, private val repo: BoxRepository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val dao = repo.database.dao()
        val data = BackupData(
            dbVersion = repo.database.openHelper.readableDatabase.version,
            exportedAt = System.currentTimeMillis(),
            brands = dao.allBrands(), ips = dao.allIps(), series = dao.allSeries(), figures = dao.allFigures(),
            items = dao.allItems(), transactions = dao.allTransactions(), wishlist = dao.allWishes(),
            virtual = dao.allVirtual(), openings = dao.allOpenings(), achievements = dao.allAchievements(),
            cabinets = dao.allCabinets(), cabinetItems = dao.allCabinetItems(), profile = dao.getProfile(),
            checkIns = dao.allCheckIns(), trades = dao.allTrades(),
        )
        val out = context.contentResolver.openOutputStream(uri) ?: error("无法写入所选位置")
        var images = 0
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("data.json"))
            zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            ImageStore.dir(context).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("images/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                images++
            }
        }
        images
    }

    /** 读取备份内容（不写库），用于恢复前确认 */
    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取文件")
        var data: BackupData? = null
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "data.json") {
                    data = json.decodeFromString(BackupData.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                }
                entry = zip.nextEntry
            }
        }
        data ?: error("这不是有效的盒宇宙备份文件")
    }

    /** 用备份完全替换当前数据 */
    suspend fun restore(uri: Uri) = withContext(Dispatchers.IO) {
        val data = read(uri)
        // 先解压图片
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取文件")
        val dir = ImageStore.dir(context)
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith("images/") && !entry.isDirectory) {
                    val name = File(entry.name).name
                    if (name.isNotBlank() && !name.contains("..")) {
                        File(dir, name).outputStream().use { zip.copyTo(it) }
                    }
                }
                entry = zip.nextEntry
            }
        }
        val db = repo.database
        val dao = db.dao()
        db.withTransaction {
            dao.wipeAll()
            dao.restoreBrands(data.brands); dao.restoreIps(data.ips); dao.restoreSeries(data.series)
            dao.restoreFigures(data.figures); dao.restoreItems(data.items); dao.restoreTransactions(data.transactions)
            dao.restoreWishes(data.wishlist); dao.restoreVirtual(data.virtual); dao.restoreOpenings(data.openings)
            dao.restoreAchievements(data.achievements); dao.restoreCabinets(data.cabinets)
            dao.restoreCabinetItems(data.cabinetItems); dao.restoreCheckIns(data.checkIns); dao.restoreTrades(data.trades)
            dao.upsertProfile(data.profile ?: UserProfile())
        }
    }

    /** 导出实物收藏 CSV（UTF-8 BOM，可直接用 Excel 打开） */
    suspend fun exportCsv(uri: Uri, rows: List<com.boxverse.app.data.db.HoldingRow>): Int = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri) ?: error("无法写入所选位置")
        fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        out.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("﻿")
            w.write("品牌,系列,款式,编号,稀有度,数量,币种,购买单价,其他成本,估算单价,获得日期,购买渠道,状态,成色,原盒,评分,备注,演示数据\n")
            rows.forEach { r ->
                val i = r.item
                w.write(
                    listOf(
                        esc(r.brandName), esc(r.seriesName), esc(r.figureName), esc(r.figureCode),
                        com.boxverse.app.domain.Rarity.fromName(r.rarity).fullLabel, i.quantity.toString(),
                        Currency.of(i.currency).code, Money.toInput(i.unitPrice), Money.toInput(i.extraCost),
                        Money.toInput(i.estimatedValue), Dates.format(i.acquiredEpochDay), esc(i.channel),
                        i.statusEnum.label, esc(i.condition), if (i.hasBox) "是" else "否", i.rating.toString(),
                        esc(i.story), if (i.isDemo) "是" else "否",
                    ).joinToString(",")
                )
                w.write("\n")
            }
        }
        rows.size
    }
}
