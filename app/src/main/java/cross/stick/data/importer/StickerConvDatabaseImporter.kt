package cross.stick.data.importer

import android.database.sqlite.SQLiteDatabase
import java.io.File

class StickerConvDatabaseImporter {

    private val dbPath = "/data/data/com.mayakapps.stickerconv/databases/stickers"
    private val filesDir = "/data/data/com.mayakapps.stickerconv/files/stickers/"

    fun importPacks(): List<UniversalStickerPack> {
        val dbFile = File(dbPath)
        if (!dbFile.exists() || !dbFile.canRead()) return emptyList()

        val packs = mutableListOf<UniversalStickerPack>()
        try {
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            packs.addAll(parseImported(db))
            packs.addAll(parseExported(db))
            db.close()
        } catch (_: Exception) { }
        return packs
    }

    private fun parseImported(db: SQLiteDatabase): List<UniversalStickerPack> {
        val query = """
            SELECT pg.id AS group_id, pg.title AS group_title,
                   ip.id AS pack_id, ip.title AS pack_title,
                   s.id AS sticker_id, s.filename, s.emojis
            FROM PackGroup pg
            JOIN ImportedPack ip ON ip.groupId = pg.id
            JOIN ImportedSticker s ON s.packId = ip.id
            ORDER BY pg.id, ip.id, s.id
        """
        val packMap = mutableMapOf<String, UniversalStickerPack>()
        val stickerLists = mutableMapOf<String, MutableList<UniversalSticker>>()

        db.rawQuery(query, null).use { cursor ->
            val packIdCol = cursor.getColumnIndexOrThrow("pack_id")
            val packTitleCol = cursor.getColumnIndexOrThrow("pack_title")
            val filenameCol = cursor.getColumnIndexOrThrow("filename")
            val emojiCol = cursor.getColumnIndexOrThrow("emojis")

            while (cursor.moveToNext()) {
                val packId = cursor.getString(packIdCol)
                val packTitle = cursor.getString(packTitleCol)
                val filename = cursor.getString(filenameCol)
                val emojiStr = cursor.getString(emojiCol)
                val emojis = emojiStr?.split(",")?.map { it.trim() } ?: listOf("😀")
                val file = File(filesDir, filename)
                if (!file.exists()) continue

                if (!packMap.containsKey(packId)) {
                    packMap[packId] = UniversalStickerPack(
                        id = "stickerconv-$packId",
                        title = packTitle,
                        sourcePackage = "com.mayakapps.stickerconv",
                        sourceAuthority = null,
                        sourceLayer = SourceLayer.ROOT_APP_DATABASE,
                        confidence = Confidence.HIGH,
                        format = StickerFormat.STATIC,
                        stickers = emptyList()
                    )
                    stickerLists[packId] = mutableListOf()
                }

                val format = StickerFileTypeDetector.detectFormat(file)
                val mimeType = StickerFileTypeDetector.getMimeType(format)

                stickerLists[packId]!!.add(
                    UniversalSticker(
                        id = "$packId-${stickerLists[packId]!!.size}",
                        sourcePath = file.absolutePath,
                        sourceUri = null,
                        localFile = file,
                        originalFileName = filename,
                        emojiList = emojis,
                        index = stickerLists[packId]!!.size,
                        mimeType = mimeType
                    )
                )
            }
        }

        return packMap.map { (id, pack) -> pack.copy(stickers = stickerLists[id] ?: emptyList()) }
    }

    private fun parseExported(db: SQLiteDatabase): List<UniversalStickerPack> {
        val query = """
            SELECT ep.id AS exported_pack_id, ep.title AS exported_pack_title,
                   es.filename, es.emojis, es.indexInPack
            FROM ExportedPack ep
            JOIN ExportedSticker es ON es.packId = ep.id
            ORDER BY ep.id, es.indexInPack
        """
        val packMap = mutableMapOf<String, UniversalStickerPack>()
        val stickerLists = mutableMapOf<String, MutableList<UniversalSticker>>()

        db.rawQuery(query, null).use { cursor ->
            val packIdCol = cursor.getColumnIndexOrThrow("exported_pack_id")
            val packTitleCol = cursor.getColumnIndexOrThrow("exported_pack_title")
            val filenameCol = cursor.getColumnIndexOrThrow("filename")
            val emojiCol = cursor.getColumnIndexOrThrow("emojis")

            while (cursor.moveToNext()) {
                val packId = cursor.getString(packIdCol)
                val packTitle = cursor.getString(packTitleCol)
                val filename = cursor.getString(filenameCol)
                val emojiStr = cursor.getString(emojiCol)
                val emojis = emojiStr?.split(",")?.map { it.trim() } ?: listOf("😀")
                val file = File(filesDir, filename)
                if (!file.exists()) continue

                if (!packMap.containsKey(packId)) {
                    packMap[packId] = UniversalStickerPack(
                        id = "stickerconv-exp-$packId",
                        title = packTitle,
                        sourcePackage = "com.mayakapps.stickerconv",
                        sourceAuthority = null,
                        sourceLayer = SourceLayer.ROOT_APP_DATABASE,
                        confidence = Confidence.HIGH,
                        format = StickerFormat.STATIC,
                        stickers = emptyList()
                    )
                    stickerLists[packId] = mutableListOf()
                }

                val format = StickerFileTypeDetector.detectFormat(file)
                val mimeType = StickerFileTypeDetector.getMimeType(format)

                stickerLists[packId]!!.add(
                    UniversalSticker(
                        id = "$packId-${stickerLists[packId]!!.size}",
                        sourcePath = file.absolutePath,
                        sourceUri = null,
                        localFile = file,
                        originalFileName = filename,
                        emojiList = emojis,
                        index = stickerLists[packId]!!.size,
                        mimeType = mimeType
                    )
                )
            }
        }

        return packMap.map { (id, pack) -> pack.copy(stickers = stickerLists[id] ?: emptyList()) }
    }
}
