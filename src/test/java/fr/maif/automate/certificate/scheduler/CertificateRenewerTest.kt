package fr.maif.automate.certificate.scheduler

import arrow.core.None
import fr.maif.automate.certificate.write.CertificateEventStoreTest
import fr.maif.automate.certificate.write.State
import fr.maif.automate.commons.x509FromString
import fr.maif.automate.letsencrypt.Certificate
import io.kotlintest.forAll
import io.kotlintest.*
import io.kotlintest.specs.StringSpec
import org.shredzone.acme4j.util.KeyPairUtils
import java.time.LocalDateTime
import java.time.Month

class CertificateRenewerTest: StringSpec() {

  init {

    "Certificate should be expired" {

      val domain = "viking.com"
      val privateKey = KeyPairUtils.createKeyPair(2048)
      val csr = "csr"
      val x509Certificate = x509FromString(CertificateEventStoreTest.cert)
      val certificate = Certificate(x509Certificate, LocalDateTime.of(2019, Month.FEBRUARY, 11, 0, 0, 0), emptyList())
      val wildcard = true

      val now = LocalDateTime.of(2019, Month.JANUARY, 13, 0, 0, 0)

      val cert = State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)
      CertificateRenewer.isCertificateExpired(cert, now) shouldBe true
    }

    "Certificate should not be expired" {

      val domain = "viking.com"
      val privateKey = KeyPairUtils.createKeyPair(2048)
      val csr = "csr"
      val x509Certificate = x509FromString(CertificateEventStoreTest.cert)

      val certificate = Certificate(x509Certificate, LocalDateTime.of(2019, Month.FEBRUARY, 11, 0, 0, 0), emptyList())
      val wildcard = true

      val now = LocalDateTime.of(2018, Month.NOVEMBER, 13, 0, 0, 0)

      val cert = State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)
      CertificateRenewer.isCertificateExpired(cert, now) shouldBe false
    }
  }
}