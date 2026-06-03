package cross.stick.whatsapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

object WhatsAppIntentHelper {

    fun addStickerPackToWhatsApp(
        context: Context,
        packId: String,
        packName: String,
        authority: String
    ) {
        launchForPackage(context, packId, packName, authority, "com.whatsapp", "WhatsApp")
    }

    fun addStickerPackToWhatsAppBusiness(
        context: Context,
        packId: String,
        packName: String,
        authority: String
    ) {
        launchForPackage(context, packId, packName, authority, "com.whatsapp.w4b", "WhatsApp Business")
    }

    private fun launchForPackage(
        context: Context,
        packId: String,
        packName: String,
        authority: String,
        packageName: String,
        appName: String
    ) {
        if (!isPackageInstalled(context, packageName)) {
            Toast.makeText(context, "$appName is not installed", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", packId)
            putExtra("sticker_pack_authority", authority)
            putExtra("sticker_pack_name", packName.take(128))
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "$appName could not open sticker import", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not add sticker pack: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
