package dev.moyi.tunnelkeep

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        // Version
        val versionText = findViewById<TextView>(R.id.version_text)
        try {
            val packageInfo: PackageInfo =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(
                        packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
            val versionName = packageInfo.versionName ?: "0.1.0"
            versionText.text = getString(R.string.help_version, versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            versionText.text = getString(R.string.help_version, "unknown")
        }

        // Source link (tappable)
        val sourceLink = findViewById<TextView>(R.id.source_link)
        sourceLink.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/sagemoyi/tunnelkeep-android"
                ))
                startActivity(intent)
            } catch (e: Exception) {
                // No browser available — ignore
            }
        }
    }
}
