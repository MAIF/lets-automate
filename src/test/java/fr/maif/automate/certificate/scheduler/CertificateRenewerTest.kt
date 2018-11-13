package fr.maif.automate.certificate.scheduler

import arrow.core.None
import fr.maif.automate.certificate.write.CertificateEventStoreTest
import fr.maif.automate.certificate.write.State
import fr.maif.automate.commons.x509FromString
import fr.maif.automate.letsencrypt.Certificate
import io.kotlintest.forAll
import io.kotlintest.matchers.*
import io.kotlintest.specs.StringSpec
import org.shredzone.acme4j.util.KeyPairUtils
import java.time.LocalDateTime

class CertificateRenewerTest: StringSpec() {

  init {

    "Certificate should be expired" {

      val domain = "viking.com"
      val privateKey = KeyPairUtils.createKeyPair(2048)
      val csr = "csr"
      val x509Certificate = x509FromString(CertificateEventStoreTest.cert)
      val certificate = Certificate(x509Certificate, LocalDateTime.now().minusDays(20), emptyList())
      val wildcard = true

      val cert = State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)
      CertificateRenewer.isCertificateExpired(cert) shouldBe true
    }

    "Certificate should not be expired" {

      val domain = "viking.com"
      val privateKey = KeyPairUtils.createKeyPair(2048)
      val csr = "csr"
      val x509Certificate = x509FromString(CertificateEventStoreTest.cert)
      val certificate = Certificate(x509Certificate, LocalDateTime.now().minusDays(40), emptyList())
      val wildcard = true

      val cert = State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)
      CertificateRenewer.isCertificateExpired(cert) shouldBe false
    }
  }
}