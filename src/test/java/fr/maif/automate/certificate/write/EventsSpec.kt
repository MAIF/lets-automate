package fr.maif.automate.certificate.write

import arrow.core.None
import arrow.core.toOption
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import fr.maif.automate.commons.eventsourcing.EventEnvelope
import fr.maif.automate.commons.stringify
import fr.maif.automate.commons.x509FromString
import fr.maif.automate.letsencrypt.Certificate
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.reactivex.sqlclient.templates.TupleMapper.mapper
import org.shredzone.acme4j.util.KeyPairUtils
import java.io.StringReader
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class EventsSpec: StringSpec() {
    init {
        ObjectMapper().registerKotlinModule()
        val dateTime = LocalDateTime.now()
        val dateTimeFormatted = DateTimeFormatter.ISO_DATE_TIME.format(dateTime)
        val reader = CertificateEventReader()

        val domain = "opunmaif.com"
        val subdomain = "test.opunmaif.com"
        val privateKey = KeyPairUtils.readKeyPair(StringReader("-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA0hjbt9AoMyftT1FrcZA5xnVFxsHh6UlLcxwu32tgkxxRcSMF\nErlaI/IAtGq9pcX8XeMsfoRVqQqLC0O6tALB8A8qOGMkOfjBHF2BIcFGxu0jUbFO\n4SEDbDScLPKAoxxaugmTONHX7ABG1PFJDjcnefhyGsZg3TwV5Mkqm9Dr6KJocP6u\nV/Hn16Y8CrLxljMWq/fAw49IkauoC3E9CNbKJlAQu//aElJLAnl1bTcHGOpetIrs\nQzRd1L3OhFmg5tdBvraEnMkLHcpTIIKbi4mw1kRRpnD3P/lB7gTiTa1hWtI38v2v\nn4Fy0IjmcaGMCw2U6/Ob/zXnq/fIfLY35ebNlQIDAQABAoIBAGQEX3znBGwuAsQy\nz4J1lXuL0pbnL8PeF6QJ86p6tLPz1DWW5VBfmrDoATviYLwtP5H2lvT3zMu3cDAB\noz0U3vyIS6IiWFmcuVnmjZXjK+2BcMHwNcvo6nc1qG+hVmnQEsTj0AlJpI/+Jboz\nz4KL8SgYhUGH1Q8dDpMmzTj2QYgyAuH8ZY4w0fLijXCUWUKS5VUbX1wOwpUlEMPR\nJwgg9xzzsvcNkXaYpf1sx8XFGzr4b7FAUwekavSISyQq42X7ZbJxMW31Ro4anaEF\n9ZfBFBA4zxutrbOyx4XLBX7P3D6gIz89JxtQzI+jZsVZUrdQRhMeQAx/+hR58lxK\nWhfIddUCgYEA/m2jp6UybCRnrIRhYU49AkARhLxiy7/hEhRgobw/LKbKvY0wd+QU\n4xEf1edWIprHjeeUUuOYeeRCrYlAK0193Cfb8OJBBr+saBhtOlIJTZVs5qoOTVW0\nDtnSSPwuUQOFHdfJyoDilHlf4UHfWnA74vFkHfu1QuSW7Knv9gNfm98CgYEA02Uc\nwEIiVRiiYGZo2hCKbxuBXreF55jXMr8YuRc1Pzw63JelYDn0zMc5uic0pzbXkzBc\n/ewwMJHnUdDItJngQ2YS2+Cdbc3GQP6E99IM9saKifUNMXkpyq1QYdGFqzBt2coQ\nhMrtJiJRXOEu1z/+ZR+0ItqU/xmc0RrrKX04RQsCgYAyPFuv4kJOb/G4cnr3x4bs\nRtIurzOin0RFWZiq1oxyuIwXWSVUxsgI/XyXs5GiS4VTU4JJy35zssonkY4F/sr3\nCTe4HDRSrM3Rz+DhPwlvNC3IbZNdWaqvs/p9Rq2ueU9zUQQa48/ZGQ4BYAxFwxtP\n9/GYqfZkOtZB7Fvg61GZaQKBgQCc7/3uRarTXhx+UMkayxja76KVvM2uaGurAu/J\nyY8ASSixx+tAiwZQWL9kkeKnGTHl1gvTf1svU8JVnRjD61Dw+ICbuB9n+1JpwgrZ\nKJnzQuZrEQAgcIE0NILue+wucR+8hiTJURXKL8QTniF2L84fKPBEx93BnTQskT1w\nacQyjQKBgQDqd4vSQWMUdBiH4YZhdx93pS2LNr9gdKt2f3SOQAqm5ERvr117T0If\nwtWgBppAl5VDWcbepY2WizqtQL5gewWpZjXr5GKJOCV5ISySyfkYIGTHumTodbVQ\n3PlTpd7NfDT+uYoJ7yYd6Kn5PMK0t9OGIQAjmECZziMlt7EcTHNLuA==\n-----END RSA PRIVATE KEY-----"))
        val privateKeyString = """-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEA0hjbt9AoMyftT1FrcZA5xnVFxsHh6UlLcxwu32tgkxxRcSMF\nErlaI/IAtGq9pcX8XeMsfoRVqQqLC0O6tALB8A8qOGMkOfjBHF2BIcFGxu0jUbFO\n4SEDbDScLPKAoxxaugmTONHX7ABG1PFJDjcnefhyGsZg3TwV5Mkqm9Dr6KJocP6u\nV/Hn16Y8CrLxljMWq/fAw49IkauoC3E9CNbKJlAQu//aElJLAnl1bTcHGOpetIrs\nQzRd1L3OhFmg5tdBvraEnMkLHcpTIIKbi4mw1kRRpnD3P/lB7gTiTa1hWtI38v2v\nn4Fy0IjmcaGMCw2U6/Ob/zXnq/fIfLY35ebNlQIDAQABAoIBAGQEX3znBGwuAsQy\nz4J1lXuL0pbnL8PeF6QJ86p6tLPz1DWW5VBfmrDoATviYLwtP5H2lvT3zMu3cDAB\noz0U3vyIS6IiWFmcuVnmjZXjK+2BcMHwNcvo6nc1qG+hVmnQEsTj0AlJpI/+Jboz\nz4KL8SgYhUGH1Q8dDpMmzTj2QYgyAuH8ZY4w0fLijXCUWUKS5VUbX1wOwpUlEMPR\nJwgg9xzzsvcNkXaYpf1sx8XFGzr4b7FAUwekavSISyQq42X7ZbJxMW31Ro4anaEF\n9ZfBFBA4zxutrbOyx4XLBX7P3D6gIz89JxtQzI+jZsVZUrdQRhMeQAx/+hR58lxK\nWhfIddUCgYEA/m2jp6UybCRnrIRhYU49AkARhLxiy7/hEhRgobw/LKbKvY0wd+QU\n4xEf1edWIprHjeeUUuOYeeRCrYlAK0193Cfb8OJBBr+saBhtOlIJTZVs5qoOTVW0\nDtnSSPwuUQOFHdfJyoDilHlf4UHfWnA74vFkHfu1QuSW7Knv9gNfm98CgYEA02Uc\nwEIiVRiiYGZo2hCKbxuBXreF55jXMr8YuRc1Pzw63JelYDn0zMc5uic0pzbXkzBc\n/ewwMJHnUdDItJngQ2YS2+Cdbc3GQP6E99IM9saKifUNMXkpyq1QYdGFqzBt2coQ\nhMrtJiJRXOEu1z/+ZR+0ItqU/xmc0RrrKX04RQsCgYAyPFuv4kJOb/G4cnr3x4bs\nRtIurzOin0RFWZiq1oxyuIwXWSVUxsgI/XyXs5GiS4VTU4JJy35zssonkY4F/sr3\nCTe4HDRSrM3Rz+DhPwlvNC3IbZNdWaqvs/p9Rq2ueU9zUQQa48/ZGQ4BYAxFwxtP\n9/GYqfZkOtZB7Fvg61GZaQKBgQCc7/3uRarTXhx+UMkayxja76KVvM2uaGurAu/J\nyY8ASSixx+tAiwZQWL9kkeKnGTHl1gvTf1svU8JVnRjD61Dw+ICbuB9n+1JpwgrZ\nKJnzQuZrEQAgcIE0NILue+wucR+8hiTJURXKL8QTniF2L84fKPBEx93BnTQskT1w\nacQyjQKBgQDqd4vSQWMUdBiH4YZhdx93pS2LNr9gdKt2f3SOQAqm5ERvr117T0If\nwtWgBppAl5VDWcbepY2WizqtQL5gewWpZjXr5GKJOCV5ISySyfkYIGTHumTodbVQ\n3PlTpd7NfDT+uYoJ7yYd6Kn5PMK0t9OGIQAjmECZziMlt7EcTHNLuA==\n-----END RSA PRIVATE KEY-----\n"""
        val csr = "csr"
        val c = "-----BEGIN CERTIFICATE-----\nMIIF5DCCBMygAwIBAgITAPqtrC8s+8n0MgD7U+jJnGjGEjANBgkqhkiG9w0BAQsF\nADAiMSAwHgYDVQQDDBdGYWtlIExFIEludGVybWVkaWF0ZSBYMTAeFw0xODA1MjYx\nMzQ5MzhaFw0xODA4MjQxMzQ5MzhaMBcxFTATBgNVBAMTDGFkZWxlZ3VlLmNvbTCC\nASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALR3/VIDSu7RUOW0mA8utFPY\nyPwpdzANULd33cW5t1APszVn+JPPR5MXfAXeKU1wPP27l2wYoLpAuNxSDhjDasB9\n6gSIHW53Q1O5GeXtOropriRpney3gSYHumi0SXoEoKa4TE26enR1YmewRX/IY9bu\nI3MgXtkxl1a2E/R4jg2wBm4e0O9aUsot4FiMSVVywQyzoeZuMDx06bx6NffrDCBo\nmLPm6y636GjZpViXJ1Sp7QNuzwLuCJz8Cr0D4RdyWVCp41SJQtNMNjzdlSc+LIns\nl5BhUKRotu0wBveKCmYNX/yMMd5r8S2A94C88uk8DJTY9WJMaCjCoqHa9G4oc/cC\nAwEAAaOCAxwwggMYMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcD\nAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUPw0mycrqJuKN3TFa\nPryGqLZA/PkwHwYDVR0jBBgwFoAUwMwDRrlYIMxccnDz4S7LIKb1aDowdwYIKwYB\nBQUHAQEEazBpMDIGCCsGAQUFBzABhiZodHRwOi8vb2NzcC5zdGctaW50LXgxLmxl\ndHNlbmNyeXB0Lm9yZzAzBggrBgEFBQcwAoYnaHR0cDovL2NlcnQuc3RnLWludC14\nMS5sZXRzZW5jcnlwdC5vcmcvMBcGA1UdEQQQMA6CDGFkZWxlZ3VlLmNvbTCB/gYD\nVR0gBIH2MIHzMAgGBmeBDAECATCB5gYLKwYBBAGC3xMBAQEwgdYwJgYIKwYBBQUH\nAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5cHQub3JnMIGrBggrBgEFBQcCAjCBngyB\nm1RoaXMgQ2VydGlmaWNhdGUgbWF5IG9ubHkgYmUgcmVsaWVkIHVwb24gYnkgUmVs\neWluZyBQYXJ0aWVzIGFuZCBvbmx5IGluIGFjY29yZGFuY2Ugd2l0aCB0aGUgQ2Vy\ndGlmaWNhdGUgUG9saWN5IGZvdW5kIGF0IGh0dHBzOi8vbGV0c2VuY3J5cHQub3Jn\nL3JlcG9zaXRvcnkvMIIBBAYKKwYBBAHWeQIEAgSB9QSB8gDwAHYA3Zk0/KXnJIDJ\nVmh9gTSZCEmySfe1adjHvKs/XMHzbmQAAAFjnO0rDQAABAMARzBFAiEA5byNN4cd\n28+twc1zzFZbQZrAm4aYl7UdjRFZRjwFFYYCIHWsrLP3oESyNJ/CUPXjbIbdICDM\n14ONLguK67WpF9vnAHYAsMyD5aX5fWuvfAnMKEkEhyrH6IsTLGNQt8b9JuFsbHcA\nAAFjnO0rVAAABAMARzBFAiEA9SBThXJy7u5wJsYiXqd6UVgGDHewi2nC+tYXkej3\nVL0CIH/DYwtHMwHfdAlesxGVwAkIXUAy1Qwma/MtB16i4tS8MA0GCSqGSIb3DQEB\nCwUAA4IBAQAmyw/gxzAau2QBKn13eKK/RNK82h6daxnLFI81uHWBn33hvOnLK/ic\n/TAZVov4Ni8b89SyWy1HglZorASLqFfQIVnec1RxuscceQhSYhC5doiLt/AWHWCU\n5y4QUCjWj4usSGtZiF6YFdpi4KDLz1WM/4ownJpV2p4HRCwX6SIhilBqFIpiDI5e\nlGqHWEZWYl30b+3wMg5HThcyKwXbD0ThDPP7isWPBP9vmhNnB6cUSArA1fG6YN6/\nmUTMrnSM50Ts0ZGT8bbOpi+rPHzqjubU7J2qvd7mOI3UI+PEM1XVCgJn9RJ+RS+D\n9yRsEGgi43/trdFxdo9/DWaoqdUU42b6\n-----END CERTIFICATE-----\n"
        val cert = """-----BEGIN CERTIFICATE-----\nMIIF5DCCBMygAwIBAgITAPqtrC8s+8n0MgD7U+jJnGjGEjANBgkqhkiG9w0BAQsF\nADAiMSAwHgYDVQQDDBdGYWtlIExFIEludGVybWVkaWF0ZSBYMTAeFw0xODA1MjYx\nMzQ5MzhaFw0xODA4MjQxMzQ5MzhaMBcxFTATBgNVBAMTDGFkZWxlZ3VlLmNvbTCC\nASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBALR3/VIDSu7RUOW0mA8utFPY\nyPwpdzANULd33cW5t1APszVn+JPPR5MXfAXeKU1wPP27l2wYoLpAuNxSDhjDasB9\n6gSIHW53Q1O5GeXtOropriRpney3gSYHumi0SXoEoKa4TE26enR1YmewRX/IY9bu\nI3MgXtkxl1a2E/R4jg2wBm4e0O9aUsot4FiMSVVywQyzoeZuMDx06bx6NffrDCBo\nmLPm6y636GjZpViXJ1Sp7QNuzwLuCJz8Cr0D4RdyWVCp41SJQtNMNjzdlSc+LIns\nl5BhUKRotu0wBveKCmYNX/yMMd5r8S2A94C88uk8DJTY9WJMaCjCoqHa9G4oc/cC\nAwEAAaOCAxwwggMYMA4GA1UdDwEB/wQEAwIFoDAdBgNVHSUEFjAUBggrBgEFBQcD\nAQYIKwYBBQUHAwIwDAYDVR0TAQH/BAIwADAdBgNVHQ4EFgQUPw0mycrqJuKN3TFa\nPryGqLZA/PkwHwYDVR0jBBgwFoAUwMwDRrlYIMxccnDz4S7LIKb1aDowdwYIKwYB\nBQUHAQEEazBpMDIGCCsGAQUFBzABhiZodHRwOi8vb2NzcC5zdGctaW50LXgxLmxl\ndHNlbmNyeXB0Lm9yZzAzBggrBgEFBQcwAoYnaHR0cDovL2NlcnQuc3RnLWludC14\nMS5sZXRzZW5jcnlwdC5vcmcvMBcGA1UdEQQQMA6CDGFkZWxlZ3VlLmNvbTCB/gYD\nVR0gBIH2MIHzMAgGBmeBDAECATCB5gYLKwYBBAGC3xMBAQEwgdYwJgYIKwYBBQUH\nAgEWGmh0dHA6Ly9jcHMubGV0c2VuY3J5cHQub3JnMIGrBggrBgEFBQcCAjCBngyB\nm1RoaXMgQ2VydGlmaWNhdGUgbWF5IG9ubHkgYmUgcmVsaWVkIHVwb24gYnkgUmVs\neWluZyBQYXJ0aWVzIGFuZCBvbmx5IGluIGFjY29yZGFuY2Ugd2l0aCB0aGUgQ2Vy\ndGlmaWNhdGUgUG9saWN5IGZvdW5kIGF0IGh0dHBzOi8vbGV0c2VuY3J5cHQub3Jn\nL3JlcG9zaXRvcnkvMIIBBAYKKwYBBAHWeQIEAgSB9QSB8gDwAHYA3Zk0/KXnJIDJ\nVmh9gTSZCEmySfe1adjHvKs/XMHzbmQAAAFjnO0rDQAABAMARzBFAiEA5byNN4cd\n28+twc1zzFZbQZrAm4aYl7UdjRFZRjwFFYYCIHWsrLP3oESyNJ/CUPXjbIbdICDM\n14ONLguK67WpF9vnAHYAsMyD5aX5fWuvfAnMKEkEhyrH6IsTLGNQt8b9JuFsbHcA\nAAFjnO0rVAAABAMARzBFAiEA9SBThXJy7u5wJsYiXqd6UVgGDHewi2nC+tYXkej3\nVL0CIH/DYwtHMwHfdAlesxGVwAkIXUAy1Qwma/MtB16i4tS8MA0GCSqGSIb3DQEB\nCwUAA4IBAQAmyw/gxzAau2QBKn13eKK/RNK82h6daxnLFI81uHWBn33hvOnLK/ic\n/TAZVov4Ni8b89SyWy1HglZorASLqFfQIVnec1RxuscceQhSYhC5doiLt/AWHWCU\n5y4QUCjWj4usSGtZiF6YFdpi4KDLz1WM/4ownJpV2p4HRCwX6SIhilBqFIpiDI5e\nlGqHWEZWYl30b+3wMg5HThcyKwXbD0ThDPP7isWPBP9vmhNnB6cUSArA1fG6YN6/\nmUTMrnSM50Ts0ZGT8bbOpi+rPHzqjubU7J2qvd7mOI3UI+PEM1XVCgJn9RJ+RS+D\n9yRsEGgi43/trdFxdo9/DWaoqdUU42b6\n-----END CERTIFICATE-----\n"""
        val x509Certificate = x509FromString(c)
        val certificate = Certificate(x509Certificate, dateTime, emptyList())

        println(CertificateOrdered(domain, None, true, privateKey, csr, certificate).toJson().encode())

        val tests = listOf(

                CertificateCreated(domain, None, true) to """{"domain":"$domain","wildcard":true}""",
                CertificateCreated(domain, subdomain.toOption(), true) to """{"domain":"$domain","subdomain":"$subdomain","wildcard":true}""",

                CertificateOrdered(domain, None, true, privateKey, csr, certificate) to """{"domain":"$domain","wildcard":true,"privateKey":"$privateKeyString","csr":"csr","certificate":{"expire":"$dateTimeFormatted","certificate":"$cert","chain":[]}}""",
                CertificateOrdered(domain, subdomain.toOption(), true, privateKey, csr, certificate) to """{"domain":"opunmaif.com","subdomain":"$subdomain","wildcard":true,"privateKey":"$privateKeyString","csr":"csr","certificate":{"expire":"$dateTimeFormatted","certificate":"$cert","chain":[]}}""",

                CertificateOrderFailure(domain, None, "Oups") to """{"domain":"$domain","cause":"Oups"}""",
                CertificateOrderFailure(domain, subdomain.toOption(), "Oups") to """{"domain":"$domain","subdomain":"$subdomain","cause":"Oups"}""",

                CertificateReOrderedStarted(domain, None, true) to """{"domain":"$domain","wildcard":true}""",
                CertificateReOrderedStarted(domain, subdomain.toOption(), true) to """{"domain":"opunmaif.com","subdomain":"$subdomain","wildcard":true}""",

                CertificateReOrdered(domain, None, true, privateKey, csr, certificate) to """{"domain":"$domain","wildcard":true,"privateKey":"$privateKeyString","csr":"csr","certificate":{"expire":"$dateTimeFormatted","certificate":"$cert","chain":[]}}""",
                CertificateReOrdered(domain, subdomain.toOption(), true, privateKey, csr, certificate) to """{"domain":"opunmaif.com","subdomain":"$subdomain","wildcard":true,"privateKey":"$privateKeyString","csr":"csr","certificate":{"expire":"$dateTimeFormatted","certificate":"$cert","chain":[]}}""",

                CertificateReOrderFailure(domain, None, "Oups") to """{"domain":"$domain","cause":"Oups"}""",
                CertificateReOrderFailure(domain, subdomain.toOption(), "Oups") to """{"domain":"$domain","subdomain":"$subdomain","cause":"Oups"}""",

                CertificatePublished(domain, None, dateTime) to """{"domain":"$domain","dateTime":"$dateTimeFormatted"}""",
                CertificatePublished(domain, subdomain.toOption(), dateTime) to """{"domain":"$domain","subdomain":"$subdomain","dateTime":"$dateTimeFormatted"}""",

                CertificatePublishFailure(domain, subdomain.toOption(), "Oups") to """{"domain":"$domain","subdomain":"$subdomain","cause":"Oups"}""",
                CertificatePublishFailure(domain, None, "Oups") to """{"domain":"$domain","cause":"Oups"}"""
        )


        "Serializing" {
            tests.forEach { p ->
                p.first.toJson().encode() shouldBe p.second
//                Json.mapper.writeValueAsString(p.first.toJson()) shouldBe p.second
            }
        }


        "Deserializing" {
            tests.forEach { p ->
                val envelope = EventEnvelope(
                        "1",
                        "1",
                        1,
                        p.first::class.java.simpleName,
                        "1.0",
                        JsonObject(p.second),
                        dateTime,
                        json { obj() }
                )
                reader.read(envelope) shouldBe p.first
            }
        }

    }
}