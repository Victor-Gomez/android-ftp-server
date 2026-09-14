package studio.victorgomez.androidftpserver.server

import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.util.concurrent.Executors

class SsdpDiscoveryService {
    private var socket: MulticastSocket? = null
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var isRunning = false

    fun start(ip: String, httpPort: Int) {
        stop()
        isRunning = true

        executor.execute {
            try {
                val group = InetAddress.getByName("239.255.255.250")
                val s = MulticastSocket(1900)
                s.joinGroup(group)
                s.soTimeout = 4000
                socket = s

                val buffer = ByteArray(1024)
                while (isRunning && !s.isClosed) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        val msg = String(packet.data, 0, packet.length)
                        if (msg.startsWith("M-SEARCH", ignoreCase = true)) {
                            val response = "HTTP/1.1 200 OK\r\n" +
                                    "CACHE-CONTROL: max-age=1800\r\n" +
                                    "LOCATION: http://$ip:$httpPort/\r\n" +
                                    "SERVER: Android-WiFi-FTP/1.0 UPnP/1.1\r\n" +
                                    "ST: upnp:rootdevice\r\n" +
                                    "USN: uuid:wifi-ftp-server::$ip\r\n\r\n"
                            val respBytes = response.toByteArray(Charsets.UTF_8)
                            val respPacket = DatagramPacket(
                                respBytes,
                                respBytes.size,
                                packet.address,
                                packet.port
                            )
                            s.send(respPacket)
                        }
                    } catch (e: Exception) {
                        if (!isRunning) break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket = null
        }
    }
}
