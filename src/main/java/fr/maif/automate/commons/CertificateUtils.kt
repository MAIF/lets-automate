package fr.maif.automate.commons

import org.shredzone.acme4j.toolbox.AcmeUtils
import org.shredzone.acme4j.util.KeyPairUtils
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.StringReader
import java.io.StringWriter
import java.security.KeyPair
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


fun KeyPair.stringify(): String {
    val stringWriter = StringWriter()
    KeyPairUtils.writeKeyPair(this, stringWriter)

    return stringWriter.toString()
}

fun readKeyPair(string: String): KeyPair = KeyPairUtils.readKeyPair(StringReader(string))

fun X509Certificate.stringify(): String = x509ToString(this)


fun x509FromString(certEntry: String): X509Certificate {
    var `in`: InputStream? = null
    try {
        val certEntryBytes = certEntry.toByteArray()
        `in` = ByteArrayInputStream(certEntryBytes)
        val certFactory = CertificateFactory.getInstance("X.509")

        return certFactory.generateCertificate(`in`) as X509Certificate
    } catch (ex: Throwable) {
        throw ex
    } finally {
        if (`in` != null) {
            `in`.close()
        }
    }
}

fun x509ToString(cert: X509Certificate): String {
    val writer = StringWriter()
    AcmeUtils.writeToPem(cert.encoded, AcmeUtils.PemLabel.CERTIFICATE, writer)
    return writer.toString()
}



