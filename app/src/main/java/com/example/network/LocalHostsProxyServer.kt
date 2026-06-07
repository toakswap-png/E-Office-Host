package com.example.network

import android.util.Log
import com.example.data.HostRule
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class LocalHostsProxyServer(
    private val getActiveRules: suspend () -> List<HostRule>
) {
    private val TAG = "LocalHostsProxy"
    private var serverSocket: ServerSocket? = null
    private var proxyJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val hostCache = ConcurrentHashMap<String, String>()

    var activePort: Int = 0
        private set

    var isRunning: Boolean = false
        private set

    init {
        // Refresh cache periodically or on creation
        refreshCache()
    }

    fun refreshCache() {
        scope.launch {
            try {
                val rules = getActiveRules()
                hostCache.clear()
                rules.forEach { rule ->
                    if (rule.isEnabled) {
                        hostCache[rule.hostname.trim().lowercase()] = rule.ipAddress.trim()
                    }
                }
                Log.d(TAG, "Cache refreshed with ${hostCache.size} active rules: $hostCache")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing proxy cache", e)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        proxyJob = scope.launch {
            try {
                // Bind to localhost only (127.0.0.1) for complete local security
                serverSocket = ServerSocket(0, 100, InetAddress.getByName("127.0.0.1"))
                activePort = serverSocket?.localPort ?: 0
                Log.d(TAG, "Proxy server successfully started on port $activePort")

                while (isActive && isRunning) {
                    val clientSocket = try {
                        serverSocket?.accept()
                    } catch (e: Exception) {
                        null
                    } ?: continue

                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in proxy dispatcher loop", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        isRunning = false
        activePort = 0
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // silent close
        }
        serverSocket = null
        proxyJob?.cancel()
        proxyJob = null
        Log.d(TAG, "Proxy server stopped")
    }

    private suspend fun handleClient(clientSocket: Socket) {
        val clientIn = clientSocket.getInputStream()
        val clientOut = clientSocket.getOutputStream()

        try {
            // Read first line of request
            val firstLine = readLine(clientIn)
            if (firstLine.isNullOrBlank()) {
                safeClose(clientSocket)
                return
            }

            val parts = firstLine.split("\\s+".toRegex())
            if (parts.size < 3) {
                safeClose(clientSocket)
                return
            }

            val method = parts[0]
            val destination = parts[1]
            val httpVersion = parts[2]

            var targetHost = ""
            var targetPort = 80
            var isConnectTunnel = false

            if (method.equals("CONNECT", ignoreCase = true)) {
                // HTTPS CONNECT Tunnel (e.g. CONNECT districts.upeoffice.gov.in:443 HTTP/1.1)
                isConnectTunnel = true
                val uriParts = destination.split(":")
                targetHost = uriParts[0]
                targetPort = if (uriParts.size > 1) uriParts[1].toInt() else 443

                // Drain headers of the CONNECT request until empty line
                drainHeaders(clientIn)
            } else if (destination.startsWith("http://", ignoreCase = true)) {
                // HTTP GET/POST/etc Proxy (e.g. GET http://districts.upeoffice.gov.in/index.html HTTP/1.1)
                val cleanUrl = destination.substring(7) // strip http://
                val slashIndex = cleanUrl.indexOf('/')
                val hostPort = if (slashIndex != -1) cleanUrl.substring(0, slashIndex) else cleanUrl
                val uriParts = hostPort.split(":")
                targetHost = uriParts[0]
                targetPort = if (uriParts.size > 1) uriParts[1].toInt() else 80
            } else {
                // Invalid or unhandled proxy request type
                safeClose(clientSocket)
                return
            }

            // Perform DNS Override or Fallback
            val resolvedHost = hostCache[targetHost.trim().lowercase()] ?: targetHost
            Log.d(TAG, "Routing request: $targetHost -> $resolvedHost (Port: $targetPort, ConnectTunnel: $isConnectTunnel)")

            // Connect to Target Upstream Server
            val targetSocket = Socket()
            try {
                targetSocket.connect(InetSocketAddress(resolvedHost, targetPort), 8000)
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting upstream to $resolvedHost:$targetPort", e)
                try {
                    clientOut.write("$httpVersion 502 Bad Gateway\r\nProxy-Error: Upstream connection failed\r\n\r\n".toByteArray())
                    clientOut.flush()
                } catch (writeEx: Exception) {}
                safeClose(clientSocket)
                return
            }

            val targetIn = targetSocket.getInputStream()
            val targetOut = targetSocket.getOutputStream()

            if (isConnectTunnel) {
                // Respond 200 OK to establish HTTPS tunnel
                clientOut.write("$httpVersion 200 Connection Established\r\nProxy-Agent: eOfficeHostsProxy/1.0\r\n\r\n".toByteArray())
                clientOut.flush()
            } else {
                // For direct HTTP, we must forward the modified first line to target server
                val slashIndex = destination.substring(7).indexOf('/')
                val relativePath = if (slashIndex != -1) destination.substring(7 + slashIndex) else "/"
                val rewrittenFirstLine = "$method $relativePath $httpVersion\r\n"
                targetOut.write(rewrittenFirstLine.toByteArray())
                targetOut.flush()
            }

            // Bi-directional TCP relay tunneling
            coroutineScope {
                val clientToTarget = launch(Dispatchers.IO) {
                    try {
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (clientIn.read(buffer).also { bytesRead = it } != -1) {
                            targetOut.write(buffer, 0, bytesRead)
                            targetOut.flush()
                        }
                    } catch (e: Exception) {
                        // ignore closed connection
                    } finally {
                        try { targetSocket.shutdownOutput() } catch (ex: Exception) {}
                    }
                }

                val targetToClient = launch(Dispatchers.IO) {
                    try {
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (targetIn.read(buffer).also { bytesRead = it } != -1) {
                            clientOut.write(buffer, 0, bytesRead)
                            clientOut.flush()
                        }
                    } catch (e: Exception) {
                        // ignore closed connection
                    } finally {
                        try { clientSocket.shutdownOutput() } catch (ex: Exception) {}
                    }
                }

                joinAll(clientToTarget, targetToClient)
            }

            // Cleanup sockets
            safeClose(clientSocket)
            safeClose(targetSocket)

        } catch (e: Exception) {
            Log.e(TAG, "Uncaught error handling client", e)
            safeClose(clientSocket)
        }
    }

    private fun readLine(inputStream: InputStream): String? {
        val bos = java.io.ByteArrayOutputStream()
        var lastChar = -1
        while (true) {
            val c = inputStream.read()
            if (c == -1) break
            if (c == '\n'.toInt()) {
                break
            }
            if (c != '\r'.toInt()) {
                bos.write(c)
            }
            lastChar = c
        }
        return if (bos.size() == 0 && lastChar == -1) null else bos.toString("UTF-8")
    }

    private fun drainHeaders(inputStream: InputStream) {
        while (true) {
            val line = readLine(inputStream)
            if (line.isNullOrEmpty()) break
        }
    }

    private fun safeClose(socket: Socket?) {
        try {
            socket?.close()
        } catch (e: Exception) {}
    }
}
