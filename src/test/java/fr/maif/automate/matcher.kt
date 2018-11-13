package fr.maif.automate

import arrow.core.Either
import arrow.core.orNull
import fr.maif.automate.certificate.write.CertificateEventStore
import fr.maif.automate.certificate.write.State
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.stringify
import io.kotlintest.forAll
import io.kotlintest.matchers.*


  public infix fun CertificateEventStore.shouldHaveState(state: State.CertificateState) {
    val data = this.state().blockingGet().data.values
    forAll(data){ d ->
      val (domain, subdomain, wildcard, _, privateKey, csr, certificate) = d
      val (domain1, subdomain1, wildcard1, _, privateKey1, csr1, certificate1) = state
      domain shouldEqual  domain1
      subdomain shouldEqual  subdomain1
      wildcard shouldEqual  wildcard1
      privateKey?.stringify() shouldEqual  privateKey1?.stringify()
      csr shouldEqual  csr1
      certificate?.certificate shouldEqual certificate1?.certificate
      certificate?.chain shouldEqual certificate1?.chain
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

