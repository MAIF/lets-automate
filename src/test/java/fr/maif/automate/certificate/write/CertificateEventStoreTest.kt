package fr.maif.automate.certificate.write

import arrow.core.*
import arrow.core.Either.Left
import arrow.core.Either.Right
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.eventsourcing.InMemoryEventStore
import fr.maif.automate.commons.stringify
import fr.maif.automate.commons.x509FromString
import fr.maif.automate.letsencrypt.Certificate
import fr.maif.automate.letsencrypt.LetSEncryptManager
import fr.maif.automate.letsencrypt.LetSEncryptCertificate
import fr.maif.automate.publisher.CertificatePublisher
import io.kotlintest.forAll
import io.kotlintest.matchers.*
import io.kotlintest.specs.StringSpec
import io.reactivex.Single
import io.vertx.core.json.Json
import org.shredzone.acme4j.util.KeyPairUtils
import java.security.KeyPair
import java.time.LocalDateTime
import fr.maif.automate.*

class CertificateEventStoreTest: StringSpec() {

    companion object {
        val cert = "-----BEGIN CERTIFICATE-----\nMIIF5DCCBMygAwIBAgITAPqtrC8s+8n0MgD7U+jJnGjGEjANBgkqhkiG9w0BAQsF\nADAiMSAwHgYDVQQDDBdGYWtlIExFIEludGVybWVkaWF0ZSBYMTAeFw0xODA1MjYx\nMzQ5MzhaFw0xODA4MjQxMzQ5MzhaMBcxFTATBgNVBAMTDGFkZWxlZ3VlLmNvbTCC\nASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALR3/VIDSu7RUOW0mA8utFPY\nyPwpdzANULd33cW5t1APszVn+JPPR5MXfAXeKU1wPP27l2wYoLpAuNxSDhjDasB9\n6gSIHW53Q1O5GeXtOropriRpney3gSYHumi0SXoEoKa4TE26enR1YmewRX/IY9bu\nI3MgXtkxl1a2E/R4jg2wBm4e0O9aUsot4FiMSVVywQyzoeZuMDx06bx6NffrDCBo\nmLPm6y636GjZpViXJ1Sp7QNuzwLuCJz8Cr0D4RdyWVCp41SJQtNMNjzdlSc+LIns\nl5BhUKRotu0wBveKCmYNX/yMMd5r8S2A94C88uk8DJTY9WJMaCjCoqHa9G4oc/cC\nAwEAAaOCAxwwggMYMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcD\nAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUPw0mycrqJuKN3TFa\nPryGqLZA/PkwHwYDVR0jBBgwFoAUwMwDRrlYIMxccnDz4S7LIKb1aDowdwYIKwYB\nBQUHAQEEazBpMDIGCCsGAQUFBzABhiZodHRwOi8vb2NzcC5zdGctaW50LXgxLmxl\ndHNlbmNyeXB0Lm9yZzAzBggrBgEFBQcwAoYnaHR0cDovL2NlcnQuc3RnLWludC14\nMS5sZXRzZW5jcnlwdC5vcmcvMBcGA1UdEQQQMA6CDGFkZWxlZ3VlLmNvbTCB/gYD\nVR0gBIH2MIHzMAgGBmeBDAECATCB5gYLKwYBBAGC3xMBAQEwgdYwJgYIKwYBBQUH\nAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5cHQub3JnMIGrBggrBgEFBQcCAjCBngyB\nm1RoaXMgQ2VydGlmaWNhdGUgbWF5IG9ubHkgYmUgcmVsaWVkIHVwb24gYnkgUmVs\neWluZyBQYXJ0aWVzIGFuZCBvbmx5IGluIGFjY29yZGFuY2Ugd2l0aCB0aGUgQ2Vy\ndGlmaWNhdGUgUG9saWN5IGZvdW5kIGF0IGh0dHBzOi8vbGV0c2VuY3J5cHQub3Jn\nL3JlcG9zaXRvcnkvMIIBBAYKKwYBBAHWeQIEAgSB9QSB8gDwAHYA3Zk0/KXnJIDJ\nVmh9gTSZCEmySfe1adjHvKs/XMHzbmQAAAFjnO0rDQAABAMARzBFAiEA5byNN4cd\n28+twc1zzFZbQZrAm4aYl7UdjRFZRjwFFYYCIHWsrLP3oESyNJ/CUPXjbIbdICDM\n14ONLguK67WpF9vnAHYAsMyD5aX5fWuvfAnMKEkEhyrH6IsTLGNQt8b9JuFsbHcA\nAAFjnO0rVAAABAMARzBFAiEA9SBThXJy7u5wJsYiXqd6UVgGDHewi2nC+tYXkej3\nVL0CIH/DYwtHMwHfdAlesxGVwAkIXUAy1Qwma/MtB16i4tS8MA0GCSqGSIb3DQEB\nCwUAA4IBAQAmyw/gxzAau2QBKn13eKK/RNK82h6daxnLFI81uHWBn33hvOnLK/ic\n/TAZVov4Ni8b89SyWy1HglZorASLqFfQIVnec1RxuscceQhSYhC5doiLt/AWHWCU\n5y4QUCjWj4usSGtZiF6YFdpi4KDLz1WM/4ownJpV2p4HRCwX6SIhilBqFIpiDI5e\nlGqHWEZWYl30b+3wMg5HThcyKwXbD0ThDPP7isWPBP9vmhNnB6cUSArA1fG6YN6/\nmUTMrnSM50Ts0ZGT8bbOpi+rPHzqjubU7J2qvd7mOI3UI+PEM1XVCgJn9RJ+RS+D\n9yRsEGgi43/trdFxdo9/DWaoqdUU42b6\n-----END CERTIFICATE-----\n"
    }

    init {
        Json.mapper.registerModule(KotlinModule())

        " Command should emit expected event" {

            //INIT
            val domain = "viking.com"
            val privateKey = KeyPairUtils.createKeyPair(2048)
            val csr = "csr"
            val x509Certificate =x509FromString(cert)
            val certificate = Certificate(x509Certificate, LocalDateTime.now(), emptyList())
            val wildcard = true

            val letSEncryptManager = mock<LetSEncryptManager>{
                on { orderCertificate(domain, None, wildcard) }
                        .doReturn(Single.just(LetSEncryptCertificate(domain, privateKey, csr, certificate).right() as Either<Error, LetSEncryptCertificate>))
            }

            val certificatePublisher = object: CertificatePublisher {
                override fun publishCertificate(domain: String, privateKey: KeyPair, csr: String, certificate: Certificate): Single<Either<Error, Unit>> =
                    Single.just(Unit.right() as Either<Error, Unit>)
            }

            val eventStore = InMemoryEventStore()
            val certificateEventStore = CertificateEventStore("certificate", letSEncryptManager, certificatePublisher, eventStore)

            //TESTS
            val certificateCreated = certificateEventStore.onCommand(CreateCertificate(domain, None, wildcard)).blockingGet()
            certificateCreated shouldBe CertificateCreated(domain, None, wildcard)
            certificateEventStore shouldHaveState State.CertificateState(domain, None, wildcard)

            val certificateOrdered = certificateEventStore.onCommand(OrderCertificate(domain, None, wildcard)).blockingGet()
            certificateOrdered shouldBe CertificateOrdered(domain, None, wildcard, privateKey, csr, certificate)
            certificateEventStore shouldHaveState State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)

            val certificateReOrderedStarted = certificateEventStore.onCommand(StartRenewCertificate(domain, None, wildcard)).blockingGet()
            certificateReOrderedStarted shouldBe CertificateReOrderedStarted(domain, None, wildcard)
            certificateEventStore shouldHaveState State.CertificateState(domain, None, wildcard, true, privateKey, csr, certificate)

            val certificateReOrdered = certificateEventStore.onCommand(RenewCertificate(domain, None, wildcard)).blockingGet()
            certificateReOrdered shouldBe CertificateReOrdered(domain, None, wildcard, privateKey, csr, certificate)
            certificateEventStore shouldHaveState State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)

            val certificatePublished = certificateEventStore.onCommand(PublishCertificate(domain, None)).blockingGet()
            certificatePublished shouldBe { e ->
                val event = e as CertificatePublished
                event.domain shouldBe domain
            }
            certificateEventStore shouldHaveState State.CertificateState(domain, None, wildcard, false, privateKey, csr, certificate)
        }


        "A domain should exist when a certificate is ordered or renewed" {
            val domain = "viking.com"
            val privateKey = KeyPairUtils.createKeyPair(2048)
            val csr = "csr"
            val x509Certificate =x509FromString(cert)
            val certificate = Certificate(x509Certificate, LocalDateTime.now(), emptyList())
            val wildcard = true

            val letSEncryptManager = mock<LetSEncryptManager>()
            val certificatePublisher = mock<CertificatePublisher>()
            val eventStore = InMemoryEventStore()
            val certificateEventStore = CertificateEventStore("certificate", letSEncryptManager, certificatePublisher, eventStore)

            val certificateOrdered = certificateEventStore.onCommand(OrderCertificate(domain, None, wildcard)).blockingGet()
            certificateOrdered shouldBeError Error("Domain $domain should be created")

            val certificateReOrdered = certificateEventStore.onCommand(RenewCertificate(domain, None, wildcard)).blockingGet()
            certificateReOrdered shouldBeError Error("Domain $domain should be created")

            val certificatePublished = certificateEventStore.onCommand(PublishCertificate(domain, None)).blockingGet()
            certificatePublished shouldBeError Error("Domain $domain should be created")
        }
    }

}