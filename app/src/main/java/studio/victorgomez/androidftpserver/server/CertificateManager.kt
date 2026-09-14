package studio.victorgomez.androidftpserver.server

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

object CertificateManager {

    private const val KEYSTORE_NAME = "androidftpserver_keystore.p12"
    private const val KEYSTORE_TYPE = "PKCS12"
    const val KEYSTORE_PASSWORD = "AndroidFtpServerKeyPassword"
    private const val KEY_ALIAS = "androidftpserver"

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Synchronized
    fun getOrCreateKeyStoreFile(context: Context): File {
        val file = File(context.filesDir, KEYSTORE_NAME)
        if (file.exists() && file.length() > 0) {
            return file
        }

        try {
            val keyPairGen = KeyPairGenerator.getInstance("RSA")
            keyPairGen.initialize(2048, SecureRandom())
            val keyPair = keyPairGen.generateKeyPair()

            val now = System.currentTimeMillis()
            val startDate = Date(now - 24 * 60 * 60 * 1000L) // Yesterday
            val endDate = Date(now + 10L * 365 * 24 * 60 * 60 * 1000L) // 10 years

            val serialNumber = BigInteger(64, SecureRandom())
            val subjectDN = X500Name("CN=Android FTP Server, O=Victor Gomez Studio, C=US")

            val certBuilder = JcaX509v3CertificateBuilder(
                subjectDN,
                serialNumber,
                startDate,
                endDate,
                subjectDN,
                keyPair.public
            )

            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val certHolder = certBuilder.build(signer)
            val cert: X509Certificate = JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(certHolder)

            val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
            keyStore.load(null, null)
            keyStore.setKeyEntry(
                KEY_ALIAS,
                keyPair.private,
                KEYSTORE_PASSWORD.toCharArray(),
                arrayOf(cert)
            )

            FileOutputStream(file).use { fos ->
                keyStore.store(fos, KEYSTORE_PASSWORD.toCharArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return file
    }

    @Synchronized
    fun getSslContext(context: Context): SSLContext {
        val ksFile = getOrCreateKeyStoreFile(context)
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        FileInputStream(ksFile).use { fis ->
            keyStore.load(fis, KEYSTORE_PASSWORD.toCharArray())
        }

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
        return sslContext
    }
}
