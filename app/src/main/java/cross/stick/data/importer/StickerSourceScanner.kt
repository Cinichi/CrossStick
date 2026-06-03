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

        // 1. Resolve all packages that we have visibility into
        val installedPackages = pm.getInstalledPackages(PackageManager.GET_PROVIDERS or PackageManager.GET_META_DATA)

        for (pkg in installedPackages) {
            val providers = pkg.providers ?: continue
            for (provider in providers) {
                if (!provider.exported) continue
                if (provider.readPermission == "com.whatsapp.sticker.READ") {
                    val appLabel = pkg.applicationInfo.loadLabel(pm).toString()
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

        // 2. Manual check for StickerConv (known authority)
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
