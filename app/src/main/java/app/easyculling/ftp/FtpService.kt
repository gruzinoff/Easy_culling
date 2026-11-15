package app.easyculling.ftp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.easyculling.MainActivity
import app.easyculling.R
import app.easyculling.app
import app.easyculling.data.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.net.NetworkInterface

data class FtpState(
    val running: Boolean = false,
    val received: Int = 0,
    val recent: List<String> = emptyList(),
    val error: String? = null,
    val folder: String = "",
)

/** One network address of the phone the camera can use. */
data class PhoneAddress(val label: String, val ip: String)

/**
 * Foreground service that keeps the FTP receiver running with the screen off or while other apps
 * are used. Shows a notification with the count of received photos and a Stop button.
 */
class FtpService : Service() {
    private var receiver: FtpReceiver? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        startForeground(
            NOTIFICATION_ID, notification(0),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (receiver == null) startReceiver()
        return START_STICKY
    }

    private fun startReceiver() {
        val prefs = app.prefs
        val folder = File(prefs.ftpFolder)
        _state.value = FtpState(running = false, folder = folder.path)
        acquireLocks()
        val r = FtpReceiver(
            root = folder,
            user = prefs.ftpUser,
            password = prefs.ftpPassword,
            port = prefs.ftpPort,
            log = { AppLog.i("FTP: $it") },
            onFileReceived = ::onReceived,
        )
        Thread {
            try {
                r.start()
                receiver = r
                _state.update { it.copy(running = true, error = null) }
            } catch (e: Exception) {
                AppLog.e("FTP server could not start", e)
                _state.update { it.copy(running = false, error = "Could not start: ${e.message}") }
                stopSelf()
            }
        }.start()
    }

    private fun onReceived(f: File) {
        MediaScannerConnection.scanFile(this, arrayOf(f.path), null, null)
        _state.update { it.copy(received = it.received + 1, recent = (listOf(f.name) + it.recent).take(20)) }
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(_state.value.received))
    }

    override fun onDestroy() {
        receiver?.stop()
        receiver = null
        releaseLocks()
        _state.update { it.copy(running = false) }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EasyCulling:ftp").apply { acquire() }
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "EasyCulling:ftp").apply { acquire() }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.release() }
        runCatching { wifiLock?.release() }
        wakeLock = null
        wifiLock = null
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Receiving from camera", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(count: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, FtpService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Receiving photos from camera")
            .setContentText(if (count == 0) "Waiting for the camera…" else "$count photos received")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .build()
    }

    companion object {
        private const val CHANNEL = "ftp"
        private const val NOTIFICATION_ID = 42
        private const val ACTION_STOP = "app.easyculling.ftp.STOP"

        private val _state = MutableStateFlow(FtpState())
        val state: StateFlow<FtpState> = _state

        fun start(context: Context) {
            _state.update { it.copy(received = 0, recent = emptyList(), error = null) }
            ContextCompat.startForegroundService(context, Intent(context, FtpService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FtpService::class.java).setAction(ACTION_STOP))
        }

        /** IPv4 addresses of the phone's Wi-Fi / hotspot / USB-tethering interfaces. */
        fun addresses(): List<PhoneAddress> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { nif ->
                    nif.inetAddresses.toList()
                        .filter { it is java.net.Inet4Address && !it.isLoopbackAddress }
                        .map { nif.name to it.hostAddress!! }
                }
                .mapNotNull { (name, ip) ->
                    val label = when {
                        name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("tun") ||
                            name.startsWith("dummy") -> null
                        name == "wlan0" -> "Wi-Fi"
                        name.startsWith("swlan") || name.startsWith("ap") || name.startsWith("softap") ||
                            name == "wlan1" || name == "wlan2" -> "Phone hotspot"
                        name.startsWith("rndis") || name.startsWith("usb") -> "USB tethering"
                        else -> name
                    }
                    label?.let { PhoneAddress(it, ip) }
                }
                .sortedBy { if (it.label == "Phone hotspot") 0 else 1 }
        }.getOrDefault(emptyList())
    }
}
