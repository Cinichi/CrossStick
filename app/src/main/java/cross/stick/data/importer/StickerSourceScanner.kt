package cross.stick.data.importer

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

data class DiscoveredStickerSource(
    val appLabel: String,
    val packageName: String,
    val authority: String,
    val isCompatible: Boolean
)

class StickerSourceScanner(private val context: Context) {

    fun scanProviders(): List<DiscoveredStickerSource> {
        val sources = mutableListOf<DiscoveredStickerSource>()
        val pm = context.packageManager

        val installedPackages = try {
            pm.getInstalledPackages(PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            Log.e("SourceScanner", "Failed to list packages", e)
            emptyList()
        }

        for (pkg in installedPackages) {
            val appInfo = pkg.applicationInfo ?: continue
            val appLabel = appInfo.loadLabel(pm).toString()
            val providers = pkg.providers ?: continue

            for (provider in providers) {
                if (!provider.exported) continue
                if (provider.readPermission == "com.whatsapp.sticker.READ") {
                    val authority = provider.authority ?: continue
                    val isCompatible = testProviderMetadata(authority)
                    sources.add(
                        DiscoveredStickerSource(
                            appLabel = appLabel,
                            packageName = pkg.packageName,
                            authority = authority,
                            isCompatible = isCompatible
                        )
                    )
                }
            }
        }

        // Manual check for StickerConv
        val stickerConvAuthority = "com.mayakapps.stickerconv.provider"
        if (sources.none { it.authority == stickerConvAuthority }) {
            try {
                pm.getPackageInfo("com.mayakapps.stickerconv", PackageManager.GET_PROVIDERS)
                val compatible = testProviderMetadata(stickerConvAuthority)
                sources.add(
                    DiscoveredStickerSource(
                        appLabel = "StickerConv",
                        packageName = "com.mayakapps.stickerconv",
                        authority = stickerConvAuthority,
                        isCompatible = compatible
                    )
                )
            } catch (_: Exception) { /* not installed */ }
        }

        return sources.distinctBy { it.authority }
    }

    private fun testProviderMetadata(authority: String): Boolean {
        return try {
            val uri = Uri.parse("content://$authority/metadata")
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (e: Exception) {
            Log.d("SourceScanner", "Provider $authority test failed: ${e.message}")
            false
        }
    }
}
