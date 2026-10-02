package com.pesutgo.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.GridLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.pesutgo.app.R
import com.pesutgo.app.core.AppConfig
import com.pesutgo.app.core.SessionManager
import com.pesutgo.app.web.WebViewActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configureHome()

        if (savedInstanceState == null && intent?.data == null) {
            val session = SessionManager(this)

            if (
                session.wasAuthenticated &&
                !session.lastUrl.isNullOrBlank()
            ) {
                openWeb(
                    session.lastUrl!!,
                    session.lastRole ?: AppConfig.ROLE_CUSTOMER
                )
            }
        }

        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun configureHome() {

        findViewById<TextView>(R.id.dateText).text =
            formatToday()

        findViewById<android.view.View>(
            R.id.providerLoginButton
        ).setOnClickListener {

            openWeb(
                AppConfig.PROVIDER_LOGIN_URL,
                AppConfig.ROLE_PROVIDER
            )
        }

        findViewById<android.view.View>(
            R.id.customerLoginButton
        ).setOnClickListener {

            openWeb(
                AppConfig.CUSTOMER_LOGIN_URL,
                AppConfig.ROLE_CUSTOMER
            )
        }

        findViewById<android.view.View>(
            R.id.navHome
        ).setOnClickListener {
            // Sudah berada di halaman Beranda.
        }

        findViewById<android.view.View>(
            R.id.navOrders
        ).setOnClickListener {

            openWeb(
                AppConfig.CUSTOMER_ORDER_URL,
                AppConfig.ROLE_CUSTOMER
            )
        }

        findViewById<android.view.View>(
            R.id.navActivity
        ).setOnClickListener {

            openWeb(
                AppConfig.CUSTOMER_HISTORY_URL,
                AppConfig.ROLE_CUSTOMER
            )
        }

        findViewById<android.view.View>(
            R.id.navProfile
        ).setOnClickListener {

            openWeb(
                AppConfig.CUSTOMER_PROFILE_URL,
                AppConfig.ROLE_CUSTOMER
            )
        }

        configureServiceItems()
    }

    private fun configureServiceItems() {

        val items = listOf(
            Triple(
                "🛵",
                "Ojek",
                "Antar Anda\ndengan Nyaman"
            ),
            Triple(
                "🚕",
                "PesutGo Car",
                "Perjalanan\nLebih Nyaman"
            ),
            Triple(
                "📦",
                "Kurir",
                "Kirim Barang\nLebih Mudah"
            ),
            Triple(
                "🛍️",
                "Pesan Antar",
                "Pesan, Kami Antar\ndengan Praktis"
            ),
            Triple(
                "🛒",
                "Belanja",
                "Belanja Kebutuhan\nHarian"
            ),
            Triple(
                "🛠️",
                "Jasa",
                "Berbagai Jasa\nuntuk Anda"
            ),
            Triple(
                "📍",
                "Titip & Ambil",
                "Praktis dan Aman"
            ),
            Triple(
                "✦",
                "Lainnya",
                "Lihat Semua\nLayanan"
            )
        )

        val grid =
            findViewById<GridLayout>(
                R.id.servicesGrid
            )

        for (
            index in 0 until
            minOf(grid.childCount, items.size)
        ) {

            val root = grid.getChildAt(index)

            val (icon, name, desc) =
                items[index]

            root.findViewById<TextView>(
                R.id.serviceIcon
            )?.text = icon

            root.findViewById<TextView>(
                R.id.serviceName
            )?.text = name

            root.findViewById<TextView>(
                R.id.serviceDesc
            )?.text = desc

            // Service cards sengaja tidak memiliki
            // click listener karena hanya visual.
        }
    }

    private fun openWeb(
        url: String,
        role: String
    ) {

        startActivity(
            Intent(
                this,
                WebViewActivity::class.java
            ).apply {

                putExtra(
                    WebViewActivity.EXTRA_URL,
                    url
                )

                putExtra(
                    WebViewActivity.EXTRA_ROLE,
                    role
                )
            }
        )
    }

    private fun handleDeepLink(
        intent: Intent?
    ) {

        val uri = intent?.data ?: return

        val host =
            uri.host?.lowercase(Locale.ROOT)

        if (
            host == "pesutgo.com" ||
            host == "www.pesutgo.com"
        ) {

            openWeb(
                uri.toString(),
                AppConfig.ROLE_CUSTOMER
            )
        }
    }

    private fun formatToday(): String {

        val formatter =
            SimpleDateFormat(
                "EEEE,\nd MMMM yyyy",
                Locale("id", "ID")
            )

        return formatter
            .format(Date())
            .replaceFirstChar {
                it.titlecase(
                    Locale("id", "ID")
                )
            }
    }
}
