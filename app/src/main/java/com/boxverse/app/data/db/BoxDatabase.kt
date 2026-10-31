package com.boxverse.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        Brand::class, IpEntity::class, Series::class, Figure::class,
        CollectionItem::class, CollectionTransaction::class, WishlistItem::class,
        VirtualCollectionItem::class, BlindBoxOpening::class, AchievementUnlock::class,
        DisplayCabinet::class, CabinetItem::class, UserProfile::class, CheckIn::class, TradeListing::class,
    ],
    version = BoxDatabase.VERSION,
    exportSchema = true,
)
abstract class BoxDatabase : RoomDatabase() {
    abstract fun dao(): BoxDao

    companion object {
        const val VERSION = 1

        fun build(context: Context): BoxDatabase =
            Room.databaseBuilder(context, BoxDatabase::class.java, "boxverse.db")
                .addMigrations(*Migrations.ALL)
                // 注意：不使用 fallbackToDestructiveMigration，避免升级时静默清空用户数据
                .build()
    }
}

/**
 * 数据库迁移登记处。
 * 以后每次修改表结构：把 [BoxDatabase.VERSION] +1，并在这里新增对应的 Migration，
 * 例如：
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE figures ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
 *     }
 * }
 * 表结构快照会导出到 app/schemas 目录，便于核对每个版本的差异。
 */
object Migrations {
    val ALL: Array<Migration> = arrayOf()
}
