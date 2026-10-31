package com.boxverse.app.data.repo

import com.boxverse.app.data.db.BoxDao
import com.boxverse.app.data.db.Brand
import com.boxverse.app.data.db.CabinetItem
import com.boxverse.app.data.db.CollectionItem
import com.boxverse.app.data.db.CollectionTransaction
import com.boxverse.app.data.db.DisplayCabinet
import com.boxverse.app.data.db.Figure
import com.boxverse.app.data.db.IpEntity
import com.boxverse.app.data.db.Series
import com.boxverse.app.data.db.TradeListing
import com.boxverse.app.data.db.WishlistItem
import com.boxverse.app.domain.Rarity
import com.boxverse.app.domain.TxType
import java.time.LocalDate

/**
 * 演示数据：全部为本 App 虚构的品牌、IP、系列与角色，插画由程序绘制，
 * 不包含任何真实品牌的商标、图片或官方概率。所有记录 isDemo = true，可一键清除。
 */
object DemoData {

    private data class DemoFigure(val name: String, val rarity: Rarity, val a: Long, val b: Long, val desc: String = "")

    private data class DemoSeries(
        val brand: String,
        val brandDesc: String,
        val ip: String,
        val ipDesc: String,
        val name: String,
        val year: Int,
        val desc: String,
        val theme: Long,
        val accent: Long,
        val rates: IntArray,
        val figures: List<DemoFigure>,
    )

    private val palette = listOf(
        0xFFFFB3C7 to 0xFFFFE3EC, 0xFFB9A8FF to 0xFFE6DEFF, 0xFFA8E6CF to 0xFFE0F7EC,
        0xFFFFD3A5 to 0xFFFFF0DC, 0xFF9AD0FF to 0xFFDDF0FF, 0xFFFFF09A to 0xFFFFFAD6,
        0xFFD7B5FF to 0xFFF1E5FF, 0xFFFFA8A8 to 0xFFFFE1E1, 0xFFB5EAD7 to 0xFFE9FBF3,
    )

    private fun regular(names: List<String>, rarity: Rarity, offset: Int): List<DemoFigure> =
        names.mapIndexed { i, n ->
            val (a, b) = palette[(i + offset) % palette.size]
            DemoFigure(n, rarity, a, b)
        }

    private val SERIES = listOf(
        DemoSeries(
            brand = "星梦工坊 DreamForge", brandDesc = "专注治愈系梦幻潮玩的虚构工作室",
            ip = "棉花糖精灵 Mallow", ipDesc = "住在云朵上的甜品精灵，最爱把梦做成甜点",
            name = "云朵甜品店", year = 2025,
            desc = "Mallow 在云端开了一家甜品店，每一款甜点都藏着一个甜甜的梦。",
            theme = 0xFFC9B8FF, accent = 0xFFFFB8D6, rates = intArrayOf(8000, 1700, 250, 50),
            figures = regular(listOf("草莓奶霜", "蓝莓布丁", "抹茶团子", "焦糖布蕾", "香芋泡芙", "柠檬挞", "蜜桃冰沙", "可可熔岩"), Rarity.R, 0) +
                listOf(
                    DemoFigure("彩虹马卡龙", Rarity.SR, 0xFFFF9EC8, 0xFF9EDBFF),
                    DemoFigure("星光棉花糖", Rarity.SR, 0xFFB79CFF, 0xFFFFE08A),
                    DemoFigure("月光甜品师", Rarity.SSR, 0xFFF6C85F, 0xFFFFF1C1, "只在月圆之夜营业的神秘甜品师"),
                    DemoFigure("银河主厨", Rarity.UR, 0xFF7B6CFF, 0xFFFF8FD8, "传说能把整条银河做成蛋糕"),
                ),
        ),
        DemoSeries(
            brand = "月光盒子 MoonBox", brandDesc = "以宇宙探索为主题的虚构潮玩品牌",
            ip = "小宇航员 Pip", ipDesc = "梦想环游整个宇宙的小小宇航员",
            name = "星际漫游记", year = 2024,
            desc = "Pip 驾驶着小飞船穿越星河，在每颗星球留下一枚脚印。",
            theme = 0xFF8EA8FF, accent = 0xFFB59CFF, rates = intArrayOf(8000, 1700, 250, 50),
            figures = regular(listOf("月球漫步", "土星环滑板", "火星种花", "彗星追逐", "星云泡泡", "太空邮差"), Rarity.R, 3) +
                listOf(
                    DemoFigure("黑洞魔术师", Rarity.SR, 0xFF6F6AD8, 0xFFC7C3FF),
                    DemoFigure("极光守望者", Rarity.SR, 0xFF7FE0D0, 0xFFB7A6FF),
                    DemoFigure("星际指挥官", Rarity.SSR, 0xFFF2B84B, 0xFFFFE7A8, "带领舰队穿越小行星带的传奇指挥官"),
                    DemoFigure("宇宙之心", Rarity.UR, 0xFF5B4BFF, 0xFFFF7EB6, "宇宙诞生时的第一缕光"),
                ),
        ),
        DemoSeries(
            brand = "奶油岛 Creamy Isle", brandDesc = "温暖日常系的虚构潮玩品牌",
            ip = "喵喵奶咖 Latte Cat", ipDesc = "在海边小岛开咖啡馆的猫咪店员们",
            name = "午后咖啡馆", year = 2025,
            desc = "阳光正好的午后，猫咪店员们为你端上一杯刚刚好的咖啡。",
            theme = 0xFFFFC9A3, accent = 0xFFFFE6B8, rates = intArrayOf(8000, 1700, 250, 50),
            figures = regular(listOf("拿铁拉花", "摩卡小憩", "卡布奇诺", "冷萃猫猫", "美式打盹", "焦糖玛奇朵", "抹茶拿铁", "燕麦奶咖"), Rarity.R, 5) +
                listOf(
                    DemoFigure("咖啡师学徒", Rarity.SR, 0xFFD9A57B, 0xFFFFE2C6),
                    DemoFigure("手冲大师", Rarity.SR, 0xFFB98563, 0xFFF5D7B8),
                    DemoFigure("夜班店长", Rarity.SSR, 0xFFEFC25A, 0xFF4D3B6E, "打烊后才出现的神秘店长"),
                    DemoFigure("黄金咖啡豆", Rarity.UR, 0xFFFFC94D, 0xFFFF9F6B, "据说能实现一个愿望的咖啡豆"),
                ),
        ),
        DemoSeries(
            brand = "星梦工坊 DreamForge", brandDesc = "专注治愈系梦幻潮玩的虚构工作室",
            ip = "森林小精灵 Sprout", ipDesc = "守护森林的小精灵乐团",
            name = "森林音乐会", year = 2026,
            desc = "夜幕降临，森林里的小精灵们拿起乐器，开始一年一度的音乐会。（本系列未设置超级隐藏款）",
            theme = 0xFFA8E0B8, accent = 0xFFD8F5A8, rates = intArrayOf(8500, 1200, 300, 0),
            figures = regular(listOf("蘑菇鼓手", "松果吉他", "树叶小号", "萤火歌者", "露珠竖琴", "刺猬钢琴"), Rarity.R, 2) +
                listOf(
                    DemoFigure("指挥家猫头鹰", Rarity.SR, 0xFF9C8E7A, 0xFFE8DCC6),
                    DemoFigure("月光精灵", Rarity.SSR, 0xFFE9D27A, 0xFFB7F0E0, "只在满月出现的森林守护者"),
                ),
        ),
    )

    suspend fun seed(dao: BoxDao) {
        val today = LocalDate.now()
        val brandIds = mutableMapOf<String, Long>()
        val figureIds = mutableMapOf<String, Long>()
        val seriesIds = mutableListOf<Long>()
        var seed = 7

        for (s in SERIES) {
            val brandId = brandIds.getOrPut(s.brand) { dao.insertBrand(Brand(name = s.brand, description = s.brandDesc, isDemo = true)) }
            val ipId = dao.insertIp(IpEntity(brandId = brandId, name = s.ip, description = s.ipDesc, isDemo = true))
            val seriesId = dao.insertSeries(
                Series(
                    brandId = brandId, ipId = ipId, name = s.name, releaseYear = s.year, description = s.desc,
                    boxPrice = 6900, currency = "CNY", themeColor = s.theme, accentColor = s.accent,
                    rateR = s.rates[0], rateSR = s.rates[1], rateSSR = s.rates[2], rateUR = s.rates[3],
                    blindBoxEnabled = true, isDemo = true,
                    createdAt = System.currentTimeMillis() - seriesIds.size * 1000L,
                )
            )
            seriesIds += seriesId
            s.figures.forEachIndexed { i, f ->
                seed = (seed * 31 + 17) % 100_000
                val id = dao.insertFigure(
                    Figure(
                        seriesId = seriesId, name = f.name, code = "No.%02d".format(i + 1), rarity = f.rarity.name,
                        artSeed = seed, colorA = f.a, colorB = f.b, description = f.desc, isDemo = true,
                    )
                )
                figureIds[f.name] = id
            }
        }

        suspend fun own(
            name: String, qty: Int, price: Long, monthsAgo: Long, dayOfMonth: Int = 8,
            channel: String = "官方小程序", est: Long? = null, extra: Long = 0, currency: String = "CNY",
            rating: Int = 4, story: String = "", condition: String = "全新", hasBox: Boolean = true,
        ) {
            val figureId = figureIds[name] ?: return
            val date = today.minusMonths(monthsAgo).withDayOfMonth(dayOfMonth.coerceAtMost(28)).let {
                if (it.isAfter(today)) today else it
            }.toEpochDay()
            val itemId = dao.insertItem(
                CollectionItem(
                    figureId = figureId, quantity = qty, unitPrice = price, currency = currency, extraCost = extra,
                    acquiredEpochDay = date, channel = channel, estimatedValue = est, rating = rating,
                    story = story, condition = condition, hasBox = hasBox, isDemo = true,
                )
            )
            dao.insertTransaction(
                CollectionTransaction(
                    itemId = itemId, figureId = figureId, type = TxType.BUY.name, quantity = qty, unitPrice = price,
                    currency = currency, fee = extra, epochDay = date, platform = channel, isDemo = true,
                )
            )
        }

        // 云朵甜品店：6/12 + 隐藏款
        own("草莓奶霜", 1, 6900, 9, story = "人生第一个盲盒，拆开那一刻就爱上了。", rating = 5)
        own("蓝莓布丁", 1, 6900, 8)
        own("抹茶团子", 1, 6900, 7, est = 7900)
        own("焦糖布蕾", 1, 6900, 6)
        own("彩虹马卡龙", 1, 6900, 5, est = 12900, rating = 5)
        own("月光甜品师", 1, 39900, 4, channel = "二手平台", est = 46900, extra = 1200, rating = 5, story = "蹲了三周才在二手平台收到，品相完美！")
        // 午后咖啡馆：常规款全收集 + 重复款
        own("拿铁拉花", 2, 6900, 3, channel = "线下门店", est = 6900)
        own("摩卡小憩", 3, 6900, 3, channel = "线下门店", est = 6500)
        own("卡布奇诺", 1, 6900, 3, channel = "线下门店")
        own("冷萃猫猫", 1, 6900, 2, channel = "电商平台")
        own("美式打盹", 1, 6900, 2, channel = "电商平台")
        own("焦糖玛奇朵", 1, 6900, 2, channel = "电商平台", est = 8900)
        own("抹茶拿铁", 1, 6900, 1, channel = "朋友交换", rating = 5, story = "和同好用一只重复的摩卡换来的。")
        own("燕麦奶咖", 1, 6900, 1, channel = "线下门店")
        own("咖啡师学徒", 1, 6900, 1, channel = "线下门店", est = 10900)
        own("手冲大师", 1, 6900, 0, dayOfMonth = 2, channel = "线下门店", est = 11900)
        // 星际漫游记：3/10（其中一件为新加坡购入，演示多币种）
        own("月球漫步", 1, 1890, 5, channel = "海外门店", currency = "SGD", extra = 0, story = "旅行时在新加坡买的。")
        own("火星种花", 1, 6900, 4)
        own("黑洞魔术师", 1, 6900, 0, dayOfMonth = 1, est = 9900)

        dao.upsertTrade(
            TradeListing(
                figureId = figureIds.getValue("摩卡小憩"), tradeQuantity = 2,
                wantText = "云朵甜品店 · 香芋泡芙 / 柠檬挞", note = "可面交或邮寄，优先同系列互换",
                condition = "全新拆盒", hasBox = true, isDemo = true,
            )
        )

        dao.insertWish(WishlistItem(figureId = figureIds["银河主厨"], name = "银河主厨", seriesName = "云朵甜品店", brandName = "星梦工坊 DreamForge", targetPrice = 89900, priority = 1, note = "梦中情娃，预算内就收！", isDemo = true))
        dao.insertWish(WishlistItem(figureId = figureIds["香芋泡芙"], name = "香芋泡芙", seriesName = "云朵甜品店", brandName = "星梦工坊 DreamForge", targetPrice = 7500, priority = 2, isDemo = true))
        dao.insertWish(WishlistItem(figureId = figureIds["星际指挥官"], name = "星际指挥官", seriesName = "星际漫游记", brandName = "月光盒子 MoonBox", targetPrice = 45000, priority = 2, desiredQuantity = 1, isDemo = true))
        dao.insertWish(WishlistItem(name = "森林音乐会 端盒", seriesName = "森林音乐会", brandName = "星梦工坊 DreamForge", targetPrice = 55200, priority = 3, desiredQuantity = 1, note = "新系列，等发售", isDemo = true))

        val cabinetId = dao.insertCabinet(DisplayCabinet(name = "我的甜品小屋", description = "云朵甜品店专属展柜", theme = "SAKURA", layout = "SHELF", isDemo = true))
        val cabinetFigures = listOf("草莓奶霜", "蓝莓布丁", "抹茶团子", "焦糖布蕾", "彩虹马卡龙", "月光甜品师")
        dao.insertCabinetItems(cabinetFigures.mapIndexedNotNull { i, n -> figureIds[n]?.let { CabinetItem(cabinetId, it, i) } })
        val cafeId = dao.insertCabinet(DisplayCabinet(name = "午后咖啡馆", description = "常规款全收集纪念", theme = "CREAM", layout = "GRID", isDemo = true))
        val cafe = listOf("拿铁拉花", "摩卡小憩", "卡布奇诺", "冷萃猫猫", "美式打盹", "焦糖玛奇朵", "抹茶拿铁", "燕麦奶咖", "咖啡师学徒", "手冲大师")
        dao.insertCabinetItems(cafe.mapIndexedNotNull { i, n -> figureIds[n]?.let { CabinetItem(cafeId, it, i) } })
    }
}
