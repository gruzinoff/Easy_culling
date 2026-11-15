package app.easyculling.ftp

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A small FTP server that only *receives* files, made for cameras with an "FTP transfer" function.
 *
 * - One user (name + password).
 * - Everything the camera uploads ends up directly in [root], whatever directory the camera
 *   creates or changes into. This keeps all photos in one folder.
 * - Uploads are written to a hidden temporary file and renamed when complete, so half-received
 *   files never show up as photos. An existing file is never overwritten: a "_1", "_2"… suffix is
 *   added instead.
 * - Passive (PASV/EPSV) and active (PORT/EPRT) data connections, REST/APPE for resumed uploads.
 *
 * Plain JVM code (no Android APIs), so it can be tested on a computer.
 */
class FtpReceiver(
    private val root: File,
    private val user: String,
    private val password: String,
    private val port: Int,
    private val log: (String) -> Unit = {},
    private val onFileReceived: (File) -> Unit = {},
) {
    private var server: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val clients = java.util.Collections.synchronizedSet(HashSet<Socket>())

    val isRunning: Boolean get() = running.get()

    @Throws(IOException::class)
    fun start() {
        if (running.get()) return
        root.mkdirs()
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(port))
        server = ss
        running.set(true)
        log("FTP server listening on port $port, saving to ${root.path}")
        Thread({ acceptLoop(ss) }, "ftp-accept").apply { isDaemon = true }.start()
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        runCatching { server?.close() }
        synchronized(clients) { clients.forEach { runCatching { it.close() } } }
        clients.clear()
        log("FTP server stopped")
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running.get()) {
            val s = try { ss.accept() } catch (e: IOException) { break }
            clients += s
            Thread({
                try {
                    Session(s).run()
                } catch (e: Exception) {
                    if (running.get()) log("Connection error: ${e.message}")
                } finally {
                    clients -= s
                    runCatching { s.close() }
                }
            }, "ftp-client").apply { isDaemon = true }.start()
        }
    }

    private inner class Session(private val control: Socket) {
        private val reader = BufferedReader(InputStreamReader(control.getInputStream(), Charsets.UTF_8))
        private val out: OutputStream = control.getOutputStream()
        private var userOk = false
        private var loggedIn = false
        private var cwd = "/" // virtual only; files always go to root
        private var passive: ServerSocket? = null
        private var activeTarget: InetSocketAddress? = null
        private var restOffset = 0L
        private var renameFrom: File? = null
        private val peer = control.inetAddress.hostAddress

        fun reply(text: String) {
            out.write((text + "\r\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }

        fun run() {
            control.soTimeout = 10 * 60_000
            log("Camera connected from $peer")
            reply("220 Easy Culling ready")
            while (running.get()) {
                val line = reader.readLine() ?: break
                val sp = line.indexOf(' ')
                val cmd = (if (sp < 0) line else line.substring(0, sp)).uppercase(Locale.US)
                val arg = if (sp < 0) "" else line.substring(sp + 1).trim()
                log(if (cmd == "PASS") "> PASS ****" else "> $line")
                if (!handle(cmd, arg)) break
            }
            closePassive()
            log("Camera disconnected ($peer)")
        }

        /** Returns false to close the connection. */
        private fun handle(cmd: String, arg: String): Boolean {
            if (!loggedIn && cmd !in setOf("USER", "PASS", "QUIT", "SYST", "FEAT", "OPTS", "AUTH", "NOOP")) {
                reply("530 Please log in"); return true
            }
            when (cmd) {
                "USER" -> { userOk = arg == user; loggedIn = false; reply("331 Password required") }
                "PASS" -> if (userOk && arg == password) {
                    loggedIn = true; log("Logged in"); reply("230 Logged in")
                } else {
                    log("Login failed (wrong user name or password)"); reply("530 Login incorrect")
                }
                "AUTH" -> reply("502 TLS not supported")
                "SYST" -> reply("215 UNIX Type: L8")
                "FEAT" -> {
                    reply("211-Features:")
                    listOf("UTF8", "PASV", "EPSV", "EPRT", "SIZE", "MDTM", "REST STREAM", "MLSD").forEach { reply(" $it") }
                    reply("211 End")
                }
                "OPTS" -> reply("200 OK")
                "NOOP" -> reply("200 OK")
                "PWD", "XPWD" -> reply("257 \"$cwd\" is the current directory")
                "CWD", "XCWD" -> { cwd = virtualPath(arg); reply("250 OK") }
                "CDUP", "XCUP" -> { cwd = virtualPath(".."); reply("250 OK") }
                "MKD", "XMKD" -> reply("257 \"${virtualPath(arg)}\" created")
                "RMD", "XRMD" -> reply("250 OK")
                "TYPE", "MODE", "STRU" -> reply("200 OK")
                "ALLO" -> reply("202 OK")
                "PASV" -> enterPassive(extended = false)
                "EPSV" -> enterPassive(extended = true)
                "PORT" -> setActive(parsePort(arg))
                "EPRT" -> setActive(parseEprt(arg))
                "REST" -> {
                    restOffset = arg.toLongOrNull() ?: 0L
                    reply("350 Restarting at $restOffset")
                }
                "STOR" -> receive(arg, append = false)
                "APPE" -> receive(arg, append = true)
                "SIZE" -> {
                    val f = fileFor(arg)
                    if (f.isFile) reply("213 ${f.length()}") else reply("550 No such file")
                }
                "MDTM" -> {
                    val f = fileFor(arg)
                    if (f.isFile) reply("213 ${mdtm(f.lastModified())}") else reply("550 No such file")
                }
                "LIST", "NLST", "MLSD" -> list(cmd)
                "RNFR" -> {
                    val f = fileFor(arg)
                    if (f.exists()) { renameFrom = f; reply("350 Ready for RNTO") } else reply("550 No such file")
                }
                "RNTO" -> {
                    val from = renameFrom
                    renameFrom = null
                    val to = fileFor(arg)
                    if (from != null && !to.exists() && from.renameTo(to)) {
                        log("Renamed ${from.name} -> ${to.name}")
                        reply("250 Renamed")
                    } else reply("550 Rename failed")
                }
                "DELE" -> reply("550 Deleting is not allowed")
                "RETR" -> reply("550 Downloading is not supported")
                "QUIT" -> { reply("221 Bye"); return false }
                else -> reply("502 Command not implemented")
            }
            return true
        }

        private fun virtualPath(arg: String): String {
            val parts = ArrayList<String>()
            if (!arg.startsWith("/")) parts += cwd.split('/').filter { it.isNotEmpty() }
            for (p in arg.split('/')) {
                when (p) {
                    "", "." -> {}
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts += p
                }
            }
            return "/" + parts.joinToString("/")
        }

        /** Every path maps to a file directly in root (folders are flattened). */
        private fun fileFor(arg: String): File {
            val name = arg.substringAfterLast('/').substringAfterLast('\\')
                .replace(Regex("[\\u0000-\\u001f:*?\"<>|]"), "_")
                .trim().ifEmpty { "upload" }
            return File(root, name)
        }

        private fun uniqueTarget(name: String): File {
            var f = File(root, name)
            if (!f.exists()) return f
            val base = f.nameWithoutExtension
            val ext = f.extension.let { if (it.isEmpty()) "" else ".$it" }
            var n = 1
            while (f.exists()) { f = File(root, "${base}_$n$ext"); n++ }
            return f
        }

        private fun enterPassive(extended: Boolean) {
            closePassive()
            activeTarget = null
            val local: InetAddress = control.localAddress
            val ss = ServerSocket()
            ss.reuseAddress = true
            ss.bind(InetSocketAddress(local, 0))
            ss.soTimeout = 60_000
            passive = ss
            val p = ss.localPort
            if (extended) {
                reply("229 Entering Extended Passive Mode (|||$p|)")
            } else {
                val ip = local.hostAddress.replace('.', ',')
                reply("227 Entering Passive Mode ($ip,${p shr 8},${p and 0xFF})")
            }
        }

        private fun setActive(target: InetSocketAddress?) {
            if (target == null) { reply("501 Bad address"); return }
            closePassive()
            activeTarget = target
            reply("200 OK")
        }

        private fun parsePort(arg: String): InetSocketAddress? = runCatching {
            val n = arg.split(',').map { it.trim().toInt() }
            InetSocketAddress("${n[0]}.${n[1]}.${n[2]}.${n[3]}", n[4] * 256 + n[5])
        }.getOrNull()

        private fun parseEprt(arg: String): InetSocketAddress? = runCatching {
            val d = arg[0]
            val p = arg.split(d).filter { it.isNotEmpty() }
            InetSocketAddress(p[1], p[2].toInt())
        }.getOrNull()

        private fun openData(): Socket? {
            passive?.let { ss ->
                return try { ss.accept().also { closePassive() } } catch (e: IOException) { closePassive(); null }
            }
            activeTarget?.let { t ->
                activeTarget = null
                return runCatching { Socket().apply { connect(t, 30_000) } }.getOrNull()
            }
            return null
        }

        private fun closePassive() {
            runCatching { passive?.close() }
            passive = null
        }

        private fun receive(arg: String, append: Boolean) {
            val requested = fileFor(arg)
            val resume = append || restOffset > 0
            val offset = restOffset
            restOffset = 0
            // A resumed upload continues the file of the same name; a new upload never overwrites.
            val target = if (resume && requested.exists()) requested else uniqueTarget(requested.name)
            val tmp = File(root, ".${target.name}.part")
            reply("150 Ready to receive ${target.name}")
            val data = openData()
            if (data == null) { reply("425 Can't open data connection"); return }
            var bytes = 0L
            // A silent connection (e.g. Wi-Fi dropped) fails after 60 s instead of hanging forever;
            // the partial file then stays hidden so it never shows up as a broken photo.
            runCatching { data.soTimeout = 60_000 }
            try {
                if (resume && target.exists()) target.renameTo(tmp)
                RandomAccessFile(tmp, "rw").use { raf ->
                    val start = when {
                        append -> raf.length()
                        offset > 0 -> offset.coerceAtMost(raf.length())
                        else -> 0L
                    }
                    raf.setLength(start)
                    raf.seek(start)
                    val buf = ByteArray(256 * 1024)
                    data.getInputStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            bytes += n
                        }
                    }
                    raf.fd.sync()
                }
                if (!tmp.renameTo(target)) throw IOException("could not finish ${target.name}")
                log("Received ${target.name} (${bytes / 1024} KB)")
                reply("226 Transfer complete")
                onFileReceived(target)
            } catch (e: Exception) {
                log("Upload of ${target.name} failed after ${bytes / 1024} KB: ${e.message}")
                // Keep the partial file hidden so the camera can resume it.
                reply("426 Transfer aborted")
            } finally {
                runCatching { data.close() }
            }
        }

        private fun list(cmd: String) {
            val files = root.listFiles { f -> f.isFile && !f.name.startsWith(".") }?.sortedBy { it.name } ?: emptyList()
            reply("150 Here comes the listing")
            val data = openData()
            if (data == null) { reply("425 Can't open data connection"); return }
            try {
                data.getOutputStream().use { o ->
                    val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                    for (f in files) {
                        val line = when (cmd) {
                            "NLST" -> f.name
                            "MLSD" -> "type=file;size=${f.length()};modify=${mdtm(f.lastModified())}; ${f.name}"
                            else -> "-rw-r--r-- 1 camera camera ${f.length()} ${fmt.format(Date(f.lastModified()))} ${f.name}"
                        }
                        o.write((line + "\r\n").toByteArray(Charsets.UTF_8))
                    }
                }
                reply("226 Listing done")
            } catch (e: SocketException) {
                reply("426 Listing aborted")
            } finally {
                runCatching { data.close() }
            }
        }

        private fun mdtm(ms: Long): String =
            SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
    }
}
