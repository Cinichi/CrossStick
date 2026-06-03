package cross.stick.data.importer

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File

class StickerConvDatabaseImporter(private val context: Context) {

    private val dbPath = "/data/data/com.mayakapps.stickerconv/databases/stickers"
    private val filesDir = "/data/data/com.mayakapps.stickerconv/files/stickers/"

    fun importPacks(): List<UniversalStickerPack> {
        val dbFile = File(dbPath)
        if (!dbFile.exists() || !dbFile.canRead()) return emptyList()

        val packs = mutableListOf<UniversalStickerPack>()

        try {
            val db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)

            // Imported packs
            val importedQuery = """
                SELECT pg.id AS group_id, pg.title AS group_title, pg.type,
                       ip.id AS pack_id, ip.title AS pack_title, ip.graphicType,
                       s.id AS sticker_id, s.filename, s.emojis
                FROM PackGroup pg
                JOIN ImportedPack ip ON ip.groupId = pg.id
                JOIN ImportedSticker s ON s.packId = ip.id
                ORDER BY pg.id, ip.id, s.id
            """.trimIndent()

            val importedPacks = parseStickers(db, importedQuery, SourceType.STICKERCONV_DATABASE)
            packs.addAll(importedPacks)

            // Exported packs
            val exportedQuery = """
                SELECT ep.id AS exported_pack_id, ep.title AS exported_pack_title,
                       ep.graphicType, ew.exportId, ew.imageDataVersion,
                       es.filename, es.emojis, es.indexInPack, es.sourceType
                FROM ExportedPack ep
                JOIN ExportedSticker es ON es.packId = ep.id
                LEFT JOIN ExportedWhatsappPack ew ON ew.id = ep.id
                ORDER BY ep.id, es.indexInPack
            """.trimIndent()

            val exportedPacks = parseStickers(db, exportedQuery, SourceType.STICKERCONV_DATABASE)
            packs.addAll(exportedPacks)

            db.close()
        } catch (e: Exception) {
            Log.e("StickerConvImporter", "Database read error: ${e.message}", e)
        }

        return packs
    }

    private fun parseStickers(
        db: SQLiteDatabase,
        query: String,
        sourceType: SourceType
    ): List<UniversalStickerPack> {
        val packMap = mutableMapOf<String, UniversalStickerPack>()
        val stickerLists = mutableMapOf<String, MutableList<UniversalSticker>>()

        db.rawQuery(query, null).use { cursor ->
            val packIdCol = cursor.getColumnIndexOrThrow("pack_id")
            val packTitleCol = cursor.getColumnIndexOrThrow("pack_title")
            val filenameCol = cursor.getColumnIndexOrThrow("filename")
            val emojiCol = cursor.getColumnIndexOrThrow("emojis")

            var stickerIndex = 0
            while (cursor.moveToNext()) {
                val packId = cursor.getString(packIdCol)
                val packTitle = cursor.getString(packTitleCol)
                val filename = cursor.getString(filenameCol)
                val emojiStr = cursor.getString(emojiCol)
                val emojis = emojiStr?.split(",")?.map { it.trim() } ?: listOf("😀")

                val file = File(filesDir, filename)
                if (!file.exists()) continue

                val format = StickerFileTypeDetector.detectFormat(file)
                val mimeType = StickerFileTypeDetector.getMimeType(format)

                if (!packMap.containsKey(packId)) {
                    packMap[packId] = UniversalStickerPack(
                        id = "stickerconv-$packId",
                        title = packTitle,
                        sourceAppPackage = "com.mayakapps.stickerconv",
                        sourceAuthority = null,
                        sourceType = sourceType,
                        format = format,
                        stickers = emptyList()
                    )
                    stickerLists[packId] = mutableListOf()
                }

                stickerLists[packId]!!.add(
                    UniversalSticker(
                        id = "$packId-$stickerIndex",
                        file = file,
                        originalFileName = filename,
                        emojiList = emojis,
                        index = stickerIndex,
                        width = null, height = null,
                        mimeType = mimeType
                    )
                )
                stickerIndex++
            }
        }

        return packMap.map { (id, pack) ->
            pack.copy(stickers = stickerLists[id] ?: emptyList())
        }
    }
}
