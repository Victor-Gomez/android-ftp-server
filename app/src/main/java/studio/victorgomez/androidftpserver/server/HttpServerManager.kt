package studio.victorgomez.androidftpserver.server

import android.content.Context
import android.webkit.MimeTypeMap
import studio.victorgomez.androidftpserver.model.ServerConfig
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class HttpServerManager(private val context: Context) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool()
    @Volatile
    private var isRunning = false

    fun start(config: ServerConfig) {
        stop()
        isRunning = true
        val socket: ServerSocket = if (config.enableHttps) {
            val sslContext = CertificateManager.getSslContext(context)
            sslContext.serverSocketFactory.createServerSocket(config.httpPort)
        } else {
            ServerSocket(config.httpPort)
        }
        serverSocket = socket

        executor.execute {
            while (isRunning && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client, config) }
                } catch (e: Exception) {
                    if (!isRunning) break
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            serverSocket = null
        }
    }

    private fun handleClient(client: Socket, config: ServerConfig) {
        client.soTimeout = 30000
        try {
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())

            val headerLines = mutableListOf<String>()
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                headerLines.add(line)
                line = reader.readLine()
            }
            if (headerLines.isEmpty()) {
                client.close()
                return
            }

            val requestLine = headerLines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                client.close()
                return
            }

            val method = parts[0].uppercase(Locale.ROOT)
            val fullUri = parts[1]
            val uriPath = fullUri.substringBefore("?")
            val queryStr = fullUri.substringAfter("?", "")

            // Check Basic Auth if anonymous is disabled
            if (!config.allowAnonymous && config.users.isNotEmpty()) {
                val authHeader = headerLines.firstOrNull { it.startsWith("Authorization: Basic ", ignoreCase = true) }
                if (authHeader == null || !validateAuth(authHeader, config)) {
                    send401(output)
                    client.close()
                    return
                }
            }

            val decodedPath = URLDecoder.decode(uriPath, "UTF-8")
            val baseDir = File(config.homeDirectory)
            val targetFile = if (decodedPath == "/" || decodedPath.isEmpty()) {
                baseDir
            } else {
                File(baseDir, decodedPath.removePrefix("/"))
            }

            // Security check: ensure targetFile is within baseDir
            if (!targetFile.canonicalPath.startsWith(baseDir.canonicalPath)) {
                send403(output)
                client.close()
                return
            }

            when (method) {
                "GET" -> handleGet(targetFile, baseDir, decodedPath, queryStr, headerLines, output)
                "POST" -> handlePost(targetFile, baseDir, headerLines, input, output, config)
                else -> sendMethodNotAllowed(output)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                client.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun handleGet(
        target: File,
        baseDir: File,
        requestPath: String,
        query: String,
        headers: List<String>,
        out: OutputStream
    ) {
        if (!target.exists()) {
            send404(out)
            return
        }

        // Folder ZIP download (?zip=1)
        if (target.isDirectory && query.contains("zip=1")) {
            sendZipArchive(target, out)
            return
        }

        // Directory listing (HTML page)
        if (target.isDirectory) {
            val html = renderDirectoryHtml(target, baseDir, requestPath)
            val bytes = html.toByteArray(Charsets.UTF_8)
            val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: text/html; charset=UTF-8\r\n")
            writer.print("Content-Length: ${bytes.size}\r\n")
            writer.print("Connection: close\r\n\r\n")
            writer.flush()
            out.write(bytes)
            out.flush()
            return
        }

        // File download / Streaming with HTTP Range support
        serveFile(target, headers, out)
    }

    private fun serveFile(file: File, headers: List<String>, out: OutputStream) {
        val totalLength = file.length()
        val mimeType = getMimeType(file)

        val rangeHeader = headers.firstOrNull { it.startsWith("Range: bytes=", ignoreCase = true) }
        var start: Long = 0
        var end: Long = totalLength - 1
        var isRange = false

        if (rangeHeader != null) {
            val rangeVal = rangeHeader.substringAfter("Range: bytes=").trim()
            val dashIdx = rangeVal.indexOf('-')
            if (dashIdx != -1) {
                val startStr = rangeVal.substring(0, dashIdx).trim()
                val endStr = rangeVal.substring(dashIdx + 1).trim()
                if (startStr.isNotEmpty()) start = startStr.toLongOrNull() ?: 0
                if (endStr.isNotEmpty()) end = endStr.toLongOrNull() ?: (totalLength - 1)
                if (end >= totalLength) end = totalLength - 1
                if (start <= end) isRange = true
            }
        }

        val contentLength = if (isRange) (end - start + 1) else totalLength
        val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))

        if (isRange) {
            writer.print("HTTP/1.1 206 Partial Content\r\n")
            writer.print("Content-Range: bytes $start-$end/$totalLength\r\n")
        } else {
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Accept-Ranges: bytes\r\n")
        }

        writer.print("Content-Type: $mimeType\r\n")
        writer.print("Content-Length: $contentLength\r\n")
        writer.print("Content-Disposition: inline; filename=\"${file.name}\"\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()

        val fis = FileInputStream(file)
        fis.skip(start)
        var remaining = contentLength
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val toRead = if (remaining > buffer.size) buffer.size else remaining.toInt()
            val read = fis.read(buffer, 0, toRead)
            if (read == -1) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        fis.close()
        out.flush()
    }

    private fun sendZipArchive(folder: File, out: OutputStream) {
        val zipName = "${folder.name.ifEmpty { "files" }}.zip"
        val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/zip\r\n")
        writer.print("Content-Disposition: attachment; filename=\"$zipName\"\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()

        val zos = ZipOutputStream(out)
        val basePath = folder.absolutePath
        zipDirectory(folder, basePath, zos)
        zos.finish()
        zos.flush()
    }

    private fun zipDirectory(dir: File, basePath: String, zos: ZipOutputStream) {
        val files = dir.listFiles() ?: return
        val buffer = ByteArray(32 * 1024)
        for (f in files) {
            val relativePath = f.absolutePath.removePrefix(basePath).trimStart(File.separatorChar).replace('\\', '/')
            if (f.isDirectory) {
                val entry = ZipEntry("$relativePath/")
                zos.putNextEntry(entry)
                zos.closeEntry()
                zipDirectory(f, basePath, zos)
            } else {
                val entry = ZipEntry(relativePath)
                zos.putNextEntry(entry)
                val fis = FileInputStream(f)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                }
                fis.close()
                zos.closeEntry()
            }
        }
    }

    private fun handlePost(
        target: File,
        baseDir: File,
        headers: List<String>,
        input: InputStream,
        out: OutputStream,
        config: ServerConfig
    ) {
        if (!config.anonymousWrite && config.allowAnonymous) {
            sendForbidden(out, "Write operations are disabled.")
            return
        }

        val contentTypeHeader = headers.firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) } ?: ""
        val contentLengthHeader = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
        val contentLength = contentLengthHeader?.substringAfter(":")?.trim()?.toLongOrNull() ?: 0

        // 1. Delete Action (form urlencoded)
        if (contentTypeHeader.contains("application/x-www-form-urlencoded", ignoreCase = true)) {
            val bodyBytes = ByteArray(contentLength.toInt())
            var read = 0
            while (read < bodyBytes.size) {
                val r = input.read(bodyBytes, read, bodyBytes.size - read)
                if (r == -1) break
                read += r
            }
            val body = String(bodyBytes, Charsets.UTF_8)
            handleDeletePost(body, baseDir, out)
            return
        }

        // 2. File Upload (multipart/form-data)
        if (contentTypeHeader.contains("multipart/form-data", ignoreCase = true)) {
            val boundary = contentTypeHeader.substringAfter("boundary=").trim()
            handleMultipartUpload(target, boundary, contentLength, input, out)
            return
        }

        sendMethodNotAllowed(out)
    }

    private fun handleDeletePost(body: String, baseDir: File, out: OutputStream) {
        val params = body.split("&")
        val deleted = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (p in params) {
            val pair = p.split("=")
            if (pair.size == 2 && pair[0] == "p") {
                val relPath = URLDecoder.decode(pair[1], "UTF-8").removePrefix("/")
                val file = File(baseDir, relPath)
                if (file.canonicalPath.startsWith(baseDir.canonicalPath) && file.exists()) {
                    val ok = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (ok) deleted.add(relPath) else failed.add(relPath)
                } else {
                    failed.add(relPath)
                }
            }
        }

        val json = "{\"deleted\":[${deleted.joinToString(",") { "\"$it\"" }}],\"failed\":[${failed.joinToString(",") { "\"$it\"" }}]}"
        val bytes = json.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json; charset=UTF-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }

    private fun handleMultipartUpload(
        folder: File,
        boundary: String,
        totalLength: Long,
        input: InputStream,
        out: OutputStream
    ) {
        try {
            val targetFolder = if (folder.isDirectory) folder else folder.parentFile ?: folder
            var bytesReadTotal: Long = 0
            val lineReader = object {
                fun readLineBytes(): ByteArray? {
                    val baos = ByteArrayOutputStream()
                    var b: Int
                    while (input.read().also { b = it } != -1) {
                        bytesReadTotal++
                        if (b == '\n'.code) {
                            val arr = baos.toByteArray()
                            return if (arr.isNotEmpty() && arr.last() == '\r'.code.toByte()) {
                                arr.copyOf(arr.size - 1)
                            } else arr
                        }
                        baos.write(b)
                    }
                    return if (baos.size() > 0) baos.toByteArray() else null
                }
            }

            var filename: String? = null
            while (true) {
                val lineBytes = lineReader.readLineBytes() ?: break
                val line = String(lineBytes, Charsets.UTF_8)
                if (line.contains("filename=\"")) {
                    filename = line.substringAfter("filename=\"").substringBefore("\"")
                }
                if (line.isEmpty()) {
                    break // Empty line signifies start of file payload
                }
            }

            if (!filename.isNullOrEmpty()) {
                val cleanName = File(filename).name
                val destFile = File(targetFolder, cleanName)
                val fos = FileOutputStream(destFile)
                val boundaryBytes = ("\r\n--$boundary").toByteArray(Charsets.UTF_8)

                val buffer = ByteArray(32 * 1024)
                val window = ByteArrayOutputStream()
                var r = 0
                var done = false

                while (!done && input.read(buffer).also { r = it } != -1) {
                    window.write(buffer, 0, r)
                    val wBytes = window.toByteArray()
                    val idx = indexOfBytes(wBytes, boundaryBytes)
                    if (idx != -1) {
                        fos.write(wBytes, 0, idx)
                        done = true
                    } else if (wBytes.size > boundaryBytes.size * 2) {
                        val safeLen = wBytes.size - boundaryBytes.size
                        fos.write(wBytes, 0, safeLen)
                        window.reset()
                        window.write(wBytes, safeLen, boundaryBytes.size)
                    }
                }
                fos.flush()
                fos.close()
            }

            val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: application/json; charset=UTF-8\r\n")
            writer.print("Content-Length: 15\r\n")
            writer.print("Connection: close\r\n\r\n")
            writer.print("{\"status\":\"ok\"}")
            writer.flush()
        } catch (e: Exception) {
            e.printStackTrace()
            send500(out, e.message ?: "Upload Error")
        }
    }

    private fun indexOfBytes(haystack: ByteArray, needle: ByteArray): Int {
        for (i in 0..(haystack.size - needle.size)) {
            var found = true
            for (j in needle.indices) {
                if (haystack[i + j] != needle[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun renderDirectoryHtml(folder: File, baseDir: File, currentUriPath: String): String {
        val files = folder.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
        ) ?: emptyList()

        val cleanPath = currentUriPath.trim('/')
        val pathSegments = if (cleanPath.isEmpty()) emptyList() else cleanPath.split('/')

        val breadcrumbsHtml = StringBuilder()
        breadcrumbsHtml.append("<a href=\"/\">Home</a>")
        var accumulated = ""
        for (seg in pathSegments) {
            accumulated += "/$seg"
            breadcrumbsHtml.append("<span class=\"sep\">/</span><a href=\"$accumulated\">$seg</a>")
        }

        val parentUrl = if (pathSegments.isNotEmpty()) {
            val parentSegs = pathSegments.dropLast(1)
            if (parentSegs.isEmpty()) "/" else "/" + parentSegs.joinToString("/")
        } else null

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val gridItems = StringBuilder()
        val listItems = StringBuilder()

        for (f in files) {
            val isDir = f.isDirectory
            val name = f.name
            val size = if (isDir) "" else formatSize(f.length())
            val dateStr = sdf.format(Date(f.lastModified()))
            val fileUrl = (if (cleanPath.isEmpty()) "" else "/$cleanPath") + "/" + URLEncoder.encode(name, "UTF-8")
            val icon = getFileIcon(f)
            val bgClass = getBgClass(f)

            // Grid card
            gridItems.append("""
                <div class="card" data-path="$fileUrl" data-name="$name" data-type="${if (isDir) "folder" else "file"}" data-url="$fileUrl" onclick="cardClick(event, this)">
                  <div class="ck"><input type="checkbox" onclick="event.stopPropagation(); toggleSel('$fileUrl', this.checked)"></div>
                  <div class="ct $bgClass">
                    <span class="ti">$icon</span>
                  </div>
                  <div class="ci">
                    <div class="cn" title="$name">$name</div>
                    <div class="cs">${if (isDir) "Folder" else size}</div>
                  </div>
                </div>
            """.trimIndent())

            // List row
            listItems.append("""
                <div class="lr" data-path="$fileUrl" data-name="$name" data-type="${if (isDir) "folder" else "file"}" data-url="$fileUrl" onclick="cardClick(event, this)">
                  <input type="checkbox" onclick="event.stopPropagation(); toggleSel('$fileUrl', this.checked)">
                  <span class="lfi">$icon</span>
                  <span class="lfn ${if (isDir) "ldn" else ""}">$name</span>
                  <span class="lfs">$size</span>
                  <span class="lfd">$dateStr</span>
                  <div><a class="ldb" href="${if (isDir) "$fileUrl?zip=1" else fileUrl}" download title="Download">⬇</a></div>
                </div>
            """.trimIndent())
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Android FTP Server – $cleanPath</title>
<style>
  *{box-sizing:border-box;margin:0;padding:0}
  :root{--teal:#00897B;--teal-d:#00695C;--teal-l:#B2DFDB;--teal-dim:#E0F2F1;--bg:#F7FAFA;--sur:#FFF;--bdr:#E0EEEC;--txt:#1A2C2A;--mut:#5F7C79;--red:#E53935;--red-d:#C62828;--mono:'Courier New',monospace;--sans:system-ui,-apple-system,sans-serif}
  body{font-family:var(--sans);background:var(--bg);color:var(--txt);min-height:100vh;padding-bottom:80px}
  .hdr{background:var(--teal);padding:0 16px;display:flex;align-items:center;justify-content:space-between;height:56px;position:sticky;top:0;z-index:200;box-shadow:0 2px 12px rgba(0,105,92,.3)}
  .logo{color:#fff;display:flex;align-items:center;gap:10px;font-size:18px;font-weight:700}
  .hdr-r{display:flex;align-items:center;gap:8px}
  .vt{display:flex;background:rgba(255,255,255,.2);border-radius:8px;padding:2px}
  .vt button{background:none;border:none;color:#fff;padding:6px 12px;border-radius:6px;cursor:pointer;font-weight:700}
  .vt button.active{background:#fff;color:var(--teal)}
  .bc{padding:12px 16px;display:flex;align-items:center;gap:6px;font-size:13px;color:var(--mut);background:var(--sur);border-bottom:1px solid var(--bdr)}
  .bc a{color:var(--teal);text-decoration:none;font-weight:600}.bc a:hover{text-decoration:underline}.bc .sep{color:var(--bdr)}
  .con{max-width:1040px;margin:16px auto;padding:0 16px}
  .tb{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px;gap:8px}
  .back{display:inline-flex;align-items:center;gap:6px;color:var(--teal);text-decoration:none;font-weight:700;font-size:13px;padding:6px 12px;border:1.5px solid var(--teal-l);border-radius:8px}
  .back:hover{background:var(--teal-dim)}
  .upz{border:2px dashed var(--teal-l);border-radius:12px;padding:20px;text-align:center;margin-bottom:16px;background:var(--sur);position:relative;cursor:pointer}
  .upz.drag{border-color:var(--teal);background:var(--teal-dim)}
  .upz input{position:absolute;inset:0;opacity:0;cursor:pointer;width:100%;height:100%}
  .pgw{display:none;margin-top:10px;background:var(--teal-dim);border-radius:99px;height:6px;overflow:hidden}
  .pgb{height:100%;background:var(--teal);width:0%;transition:width .2s}
  .gv{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:12px}
  .card{background:var(--sur);border-radius:10px;border:1.5px solid var(--bdr);cursor:pointer;position:relative;transition:.15s;overflow:hidden}
  .card:hover{border-color:var(--teal);box-shadow:0 4px 12px rgba(0,137,123,.15)}
  .card.sel{border-color:var(--teal);box-shadow:0 0 0 2.5px var(--teal)}
  .ct{width:100%;aspect-ratio:1;display:flex;align-items:center;justify-content:center;font-size:42px}
  .folder-bg{background:linear-gradient(135deg,#E0F2F1,#80CBC4)}
  .img-bg{background:#E8F5E9}.vid-bg{background:#E3F2FD}.aud-bg{background:#F3E5F5}.pdf-bg{background:#FFEBEE}.file-bg{background:var(--teal-dim)}
  .ck{position:absolute;top:6px;left:6px;z-index:10}
  .ck input{width:18px;height:18px;accent-color:var(--teal);cursor:pointer}
  .ci{padding:8px 10px}
  .cn{font-size:12px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .cs{font-size:11px;color:var(--mut);margin-top:2px}
  .lv{display:none;background:var(--sur);border-radius:12px;border:1px solid var(--bdr);overflow:hidden}
  .lh,.lr{display:grid;grid-template-columns:36px 30px 1fr 90px 140px 48px;padding:10px 14px;align-items:center}
  .lh{background:var(--teal-dim);font-size:11px;font-weight:700;color:var(--mut);text-transform:uppercase}
  .lr{border-bottom:1px solid var(--bdr);cursor:pointer}
  .lr:hover{background:var(--teal-dim)}.lr.sel{background:#E0F2F1}
  .lr input[type=checkbox]{width:16px;height:16px;accent-color:var(--teal);cursor:pointer}
  .lfn{font-size:13px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  .ldn{color:var(--teal);font-weight:700}
  .lfs,.lfd{font-size:12px;color:var(--mut);font-family:var(--mono)}
  .ldb{display:inline-flex;align-items:center;justify-content:center;width:28px;height:28px;border-radius:6px;color:var(--teal);border:1px solid var(--teal-l);text-decoration:none}
  .ldb:hover{background:var(--teal);color:#fff}
  .ab{position:fixed;bottom:0;left:0;right:0;background:var(--txt);color:#fff;display:flex;align-items:center;padding:12px 20px;transform:translateY(110%);transition:transform .25s ease;z-index:300;gap:10px}
  .ab.show{transform:translateY(0)}
  .ab-cnt{font-weight:700;flex:1}
  .ab-btn{border:none;border-radius:8px;padding:8px 16px;font-size:13px;font-weight:700;cursor:pointer}
  .ab-dl{background:var(--teal);color:#fff}.ab-del{background:var(--red);color:#fff}.ab-cls{background:rgba(255,255,255,.2);color:#fff}
  .toast{position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:rgba(0,0,0,.85);color:#fff;padding:10px 20px;border-radius:8px;font-size:13px;opacity:0;transition:opacity .2s;pointer-events:none;z-index:900}
  .toast.show{opacity:1}
</style>
</head>
<body>
<div class="hdr">
  <div class="logo"><span>📶</span> Android FTP Server</div>
  <div class="hdr-r">
    <div class="vt">
      <button id="btn-grid" class="active" onclick="setView('grid')">Grid</button>
      <button id="btn-list" onclick="setView('list')">List</button>
    </div>
  </div>
</div>
<div class="bc">$breadcrumbsHtml</div>
<div class="con">
  <div class="tb">
    ${if (parentUrl != null) "<a class=\"back\" href=\"$parentUrl\">⬅ Back to Parent</a>" else "<span></span>"}
  </div>
  <div class="upz" id="dz">
    <input type="file" id="fi" multiple onchange="uploadFiles(this.files)">
    <div style="font-size:24px;margin-bottom:6px">📤 Drag & Drop files here, or click to browse</div>
    <div style="font-size:12px;color:var(--mut)">Upload files directly to this folder</div>
    <div class="pgw" id="pw"><div class="pgb" id="pb"></div></div>
  </div>
  <div class="gv" id="gv">$gridItems</div>
  <div class="lv" id="lv">
    <div class="lh"><span></span><span></span><span>Name</span><span>Size</span><span>Modified</span><span></span></div>
    $listItems
  </div>
</div>
<div class="ab" id="ab">
  <span class="ab-cnt" id="ab-cnt">0 items selected</span>
  <button class="ab-btn ab-dl" onclick="downloadSelected()">⬇ Download</button>
  <button class="ab-btn ab-del" onclick="deleteSelected()">🗑 Delete</button>
  <button class="ab-btn ab-cls" onclick="clearSel()">Cancel</button>
</div>
<div class="toast" id="t"></div>
<script>
  const sel = new Set();
  function setView(v){
    document.getElementById('gv').style.display = v==='grid'?'grid':'none';
    document.getElementById('lv').style.display = v==='list'?'block':'none';
    document.getElementById('btn-grid').classList.toggle('active', v==='grid');
    document.getElementById('btn-list').classList.toggle('active', v==='list');
  }
  function cardClick(e, el){
    if(e.target.tagName==='INPUT')return;
    const type=el.dataset.type, url=el.dataset.url;
    if(type==='folder'){location.href=url;}
    else{window.open(url,'_blank');}
  }
  function toggleSel(path, checked){
    if(checked) sel.add(path); else sel.delete(path);
    updateSel();
  }
  function updateSel(){
    document.querySelectorAll('[data-path]').forEach(el=>{
      const p=el.dataset.path, isS=sel.has(p);
      el.classList.toggle('sel', isS);
      const ck=el.querySelector('input[type=checkbox]');
      if(ck) ck.checked=isS;
    });
    const ab=document.getElementById('ab');
    if(sel.size>0){
      ab.classList.add('show');
      document.getElementById('ab-cnt').textContent=sel.size+' item(s) selected';
    } else {
      ab.classList.remove('show');
    }
  }
  function clearSel(){sel.clear();updateSel();}
  function downloadSelected(){
    sel.forEach(p=>{
      const a=document.createElement('a');
      a.href=p;
      a.download='';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    });
    clearSel();
  }
  function deleteSelected(){
    if(!confirm('Permanently delete '+sel.size+' selected item(s)?'))return;
    const fd=new URLSearchParams();
    sel.forEach(p=>fd.append('p',p));
    fetch(location.pathname,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:fd.toString()})
      .then(r=>r.json())
      .then(data=>{
        toast('Deleted '+(data.deleted||[]).length+' items');
        setTimeout(()=>location.reload(),600);
      }).catch(()=>toast('Delete failed'));
  }
  function uploadFiles(files){
    if(!files||!files.length)return;
    const pw=document.getElementById('pw'), pb=document.getElementById('pb');
    pw.style.display='block'; pb.style.width='0%';
    let done=0;
    Array.from(files).forEach(f=>{
      const fd=new FormData();
      fd.append('file',f,f.name);
      const x=new XMLHttpRequest();
      x.open('POST',location.pathname);
      x.upload.onprogress=e=>{if(e.lengthComputable)pb.style.width=((done+e.loaded/e.total)/files.length*100)+'%'};
      x.onload=()=>{done++;pb.style.width=(done/files.length*100)+'%';if(done===files.length){toast('Upload complete!');setTimeout(()=>location.reload(),800)}};
      x.onerror=()=>toast('Upload failed');
      x.send(fd);
    });
  }
  function toast(m){const t=document.getElementById('t');t.textContent=m;t.classList.add('show');setTimeout(()=>t.classList.remove('show'),3000)}
</script>
</body>
</html>
        """.trimIndent()
    }

    private fun getFileIcon(file: File): String {
        if (file.isDirectory) return "📁"
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "svg" -> "🖼️"
            "mp4", "mkv", "webm", "avi", "mov" -> "🎬"
            "mp3", "wav", "ogg", "flac", "m4a" -> "🎵"
            "pdf" -> "📄"
            "zip", "rar", "7z", "tar", "gz" -> "📦"
            "apk" -> "📱"
            "txt", "json", "xml", "csv", "html", "js", "kt" -> "📝"
            else -> "📄"
        }
    }

    private fun getBgClass(file: File): String {
        if (file.isDirectory) return "folder-bg"
        val ext = file.extension.lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "svg" -> "img-bg"
            "mp4", "mkv", "webm", "avi", "mov" -> "vid-bg"
            "mp3", "wav", "ogg", "flac", "m4a" -> "aud-bg"
            "pdf" -> "pdf-bg"
            else -> "file-bg"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val formatted = String.format(Locale.US, "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }

    private fun getMimeType(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "json" -> "application/json"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun validateAuth(authHeader: String, config: ServerConfig): Boolean {
        try {
            val base64 = authHeader.substringAfter("Basic ").trim()
            val decoded = String(android.util.Base64.decode(base64, android.util.Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split(":")
            if (parts.size >= 2) {
                val user = parts[0]
                val pass = parts[1]
                return config.users.any { it.username.equals(user, ignoreCase = true) && it.password == pass }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun send401(out: OutputStream) {
        val msg = "Authentication Required"
        val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))
        writer.print("HTTP/1.1 401 Unauthorized\r\n")
        writer.print("WWW-Authenticate: Basic realm=\"Android FTP Server\"\r\n")
        writer.print("Content-Type: text/plain\r\n")
        writer.print("Content-Length: ${msg.length}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.print(msg)
        writer.flush()
    }

    private fun send403(out: OutputStream) = sendSimple(out, 403, "Forbidden", "Access Denied")
    private fun send404(out: OutputStream) = sendSimple(out, 404, "Not Found", "Item Not Found")
    private fun sendForbidden(out: OutputStream, msg: String) = sendSimple(out, 403, "Forbidden", msg)
    private fun sendMethodNotAllowed(out: OutputStream) = sendSimple(out, 405, "Method Not Allowed", "Method Not Allowed")
    private fun send500(out: OutputStream, msg: String) = sendSimple(out, 500, "Internal Server Error", msg)

    private fun sendSimple(out: OutputStream, code: Int, status: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val writer = PrintWriter(OutputStreamWriter(out, Charsets.UTF_8))
        writer.print("HTTP/1.1 $code $status\r\n")
        writer.print("Content-Type: text/plain; charset=UTF-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n\r\n")
        writer.flush()
        out.write(bytes)
        out.flush()
    }
}
