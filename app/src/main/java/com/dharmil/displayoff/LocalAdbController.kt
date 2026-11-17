package com.dharmil.displayoff

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.rhythmcache.adb.AdbClient
import io.github.rhythmcache.adb.FileKeyProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class WirelessDebugPorts(
    val pairingPort: Int? = null,
    val connectPort: Int? = null,
)

/** Narrow local ADB interface: no caller-provided shell commands or hosts. */
class LocalAdbController(context: Context) {
    private val appContext = context.applicationContext
    private val keyFile = File(appContext.noBackupFilesDir, "local-adb-host-key")
    private val keyProvider = FileKeyProvider(keyFile)

    /** Find this phone's currently advertised ADB ports through Android NSD/mDNS. */
    suspend fun discoverWirelessDebugPorts(): WirelessDebugPorts = withContext(Dispatchers.Main.immediate) {
        val nsd = appContext.getSystemService(NsdManager::class.java)
            ?: return@withContext WirelessDebugPorts()
        val localAddresses = localWifiAddressStrings()
        val ports = mutableMapOf<String, Int>()
        val seenServices = mutableSetOf<String>()
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        val resolveQueue = ArrayDeque<Pair<NsdServiceInfo, String>>()
        var resolving = false

        lateinit var resolveNext: () -> Unit
        resolveNext = {
            if (!resolving && resolveQueue.isNotEmpty()) {
                val (serviceInfo, wantedType) = resolveQueue.removeFirst()
                resolving = true
                try {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                            resolving = false
                            resolveNext()
                        }

                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress.orEmpty().substringBefore('%')
                            if (host in localAddresses || resolved.host?.isLoopbackAddress == true) {
                                ports[wantedType] = resolved.port
                            }
                            resolving = false
                            resolveNext()
                        }
                    })
                } catch (_: Exception) {
                    resolving = false
                    resolveNext()
                }
            }
        }

        val serviceTypes = listOf(PAIR_SERVICE, CONNECT_SERVICE)
        for (wantedType in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    val actualType = serviceInfo.serviceType.orEmpty().trimEnd('.')
                    if (actualType != wantedType) return
                    val serviceKey = "$actualType/${serviceInfo.serviceName}"
                    if (!seenServices.add(serviceKey) || seenServices.size > MAX_SERVICES_TO_RESOLVE) return
                    resolveQueue.addLast(serviceInfo to wantedType)
                    resolveNext()
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
                override fun onDiscoveryStopped(serviceType: String) = Unit
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsd.stopServiceDiscovery(this) }
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    runCatching { nsd.stopServiceDiscovery(this) }
                }
            }
            try {
                nsd.discoverServices(wantedType, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners += listener
            } catch (_: Exception) {
                // Keep the manual port-entry fallback available if NSD is blocked.
            }
        }

        try {
            delay(DISCOVERY_WINDOW_MS)
        } finally {
            listeners.forEach { listener -> runCatching { nsd.stopServiceDiscovery(listener) } }
        }

        WirelessDebugPorts(
            pairingPort = ports[PAIR_SERVICE],
            connectPort = ports[CONNECT_SERVICE],
        )
    }

    suspend fun pair(pairingPort: Int, pairingCode: String) {
        val result = withTimeout(OPERATION_TIMEOUT_MS) {
            AdbClient.pairTls(
                host = LOOPBACK,
                port = pairingPort,
                pairingCode = pairingCode,
                keyProvider = keyProvider,
                connectTimeoutMs = CONNECTION_TIMEOUT_MS,
            )
        }
        result.getOrElse { throw it }
    }

    suspend fun connect(connectPort: Int) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            AdbClient.connectTls(
                host = LOOPBACK,
                port = connectPort,
                keyProvider = keyProvider,
                handshakeTimeoutMs = CONNECTION_TIMEOUT_MS.toLong(),
            ).use { }
        }
    }

    suspend fun turnDisplayOff(connectPort: Int) {
        withTimeout(OPERATION_TIMEOUT_MS) {
            AdbClient.connectTls(
                host = LOOPBACK,
                port = connectPort,
                keyProvider = keyProvider,
                handshakeTimeoutMs = CONNECTION_TIMEOUT_MS.toLong(),
            ).use { client ->
                val result = client.shell(DISPLAY_OFF_COMMAND)
                if (!result.isSuccess) {
                    val detail = result.stderrText.trim().ifEmpty { result.stdoutText.trim() }
                    throw IllegalStateException(
                        if (detail.isBlank()) "Display command failed with exit code ${result.exitCode}."
                        else detail.take(MAX_ERROR_LENGTH),
                    )
                }
            }
        }
    }

    fun forgetLocalKey() {
        keyFile.delete()
        File(keyFile.parentFile, "${keyFile.name}.pub").delete()
    }

    fun hasLocalKey(): Boolean = keyFile.exists() && keyFile.length() > 0L

    private fun localWifiAddressStrings(): Set<String> {
        val connectivity = appContext.getSystemService(ConnectivityManager::class.java) ?: return emptySet()
        return connectivity.allNetworks
            .asSequence()
            .filter { network ->
                connectivity.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            .flatMap { network ->
                connectivity.getLinkProperties(network)?.linkAddresses.orEmpty().asSequence()
            }
            .map { address -> address.address.hostAddress.orEmpty().substringBefore('%') }
            .filter(String::isNotBlank)
            .toSet()
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
        const val PAIR_SERVICE = "_adb-tls-pairing._tcp"
        const val CONNECT_SERVICE = "_adb-tls-connect._tcp"
        const val DISCOVERY_WINDOW_MS = 3_500L
        const val MAX_SERVICES_TO_RESOLVE = 16
        const val DISPLAY_OFF_COMMAND = "cmd display power-off 0"
        const val CONNECTION_TIMEOUT_MS = 12_000
        const val OPERATION_TIMEOUT_MS = 20_000L
        const val MAX_ERROR_LENGTH = 180
    }
}
