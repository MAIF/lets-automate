package fr.maif.automate

import arrow.core.Either
import arrow.core.orNull
import fr.maif.automate.certificate.write.CertificateEventStore
import fr.maif.automate.certificate.write.State
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.stringify
import io.kotlintest.forAll
import io.kotlintest.*
import io.kotlintest.matchers.*


  public infix fun CertificateEventStore.shouldHaveState(state: State.CertificateState) {
    val data = this.state().blockingGet().data.values
    forAll(data){ d ->
      val (domain, subdomain, wildcard, _, privateKey, csr, certificate) = d
      val (domain1, subdomain1, wildcard1, _, privateKey1, csr1, certificate1) = state
      domain shouldBe  domain1
      subdomain shouldBe  subdomain1
      wildcard shouldBe  wildcard1
      privateKey?.stringify() shouldBe  privateKey1?.stringify()
      csr shouldBe  csr1
      certificate?.certificate shouldBe certificate1?.certificate
      certificate?.chain shouldBe certificate1?.chain
    }
  }

  public infix fun <T> Either<Error, T>.shouldBe(expected: T): Unit {
    when(this) {
      is Either.Right -> this.b shouldBe expected
      is Either.Left -> fail("Should be Right")
    }
  }

  public infix fun <T> Either<Error, T>.shouldBe(expected: (T?) -> Unit): Unit {
    this should  beInstanceOf(Either.Right::class)
    expected(this.orNull())
  }

  public infix fun <T> Either<Error, T>.shouldBeError(expected: Error): Unit {
    when(this) {
      is Either.Left -> this.a shouldBe expected
      is Either.Right -> fail("Should be Left")
    }
  }

