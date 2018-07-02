package fr.maif.automate.letsencrypt

import arrow.core.Either
import arrow.core.Option
import fr.maif.automate.certificate.write.CertificateOrdered
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.stringify
import fr.maif.automate.commons.x509FromString
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import org.shredzone.acme4j.util.KeyPairUtils
import java.io.StringReader
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Certificate(val certificate : X509Certificate, val expire: LocalDateTime, val chain: List<X509Certificate>) {

    companion object {
        fun fromJson(json: JsonObject): Certificate =
                Certificate(
                        certificate = x509FromString(json["certificate"]),
                        expire = LocalDateTime.parse(json.getString("expire"), DateTimeFormatter.ISO_DATE_TIME),
                        chain = json.getJsonArray("chain").list.map { x509FromString(it.toString()) }
                )
    }

    fun toJson(): JsonObject =
            json {obj(
                    "expire" to DateTimeFormatter.ISO_DATE_TIME.format(expire),
                    "certificate" to certificate.stringify(),
                    "chain" to chain.map { it.stringify() }
            )}

    override fun equals(other: Any?): Boolean {
        if (other is Certificate) {
            return (
                other.certificate.stringify() == certificate.stringify() &&
                other.expire == expire
            )
        }
        return false
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }
}
data class LetSEncryptCertificate(val domain: String, val privateKey: KeyPair, val csr: String, val certificate: Certificate)

data class LetSEncryptAccount(val userId: String, val keys: KeyPair) {

    companion object {
        fun fromJson(json: JsonObject): LetSEncryptAccount =
                LetSEncryptAccount(
                        userId = json.getString("userId"),
                        keys = KeyPairUtils.readKeyPair(StringReader(json.getString("privateKey")))
                )

    }

    fun toJson(): JsonObject = json {
        obj(
                "userId" to userId,
                "privateKey" to keys.stringify()
        )
    }
}

interface LetSEncryptManager {

    fun orderCertificate(domain: String, subdomain: Option<String>, isWildCard: Boolean): Single<Either<Error, LetSEncryptCertificate>>

}






