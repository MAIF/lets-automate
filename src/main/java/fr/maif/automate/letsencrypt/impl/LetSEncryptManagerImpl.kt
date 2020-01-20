package fr.maif.automate.letsencrypt.impl

import arrow.core.*
import arrow.data.*
import arrow.effects.*
import arrow.instances.monadError
import arrow.typeclasses.binding
import arrow.instances.either.applicative.*
import arrow.effects.observablek.monad.*
import fr.maif.automate.commons.Error
import fr.maif.automate.commons.LetSEncryptConfig
import fr.maif.automate.dns.DnsManager
import fr.maif.automate.dns.Record
import fr.maif.automate.letsencrypt.Certificate
import fr.maif.automate.letsencrypt.LetSEncryptAccount
import fr.maif.automate.letsencrypt.LetSEncryptManager
import fr.maif.automate.letsencrypt.LetSEncryptCertificate
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.array
import io.vertx.kotlin.core.json.json
import io.vertx.reactivex.ext.asyncsql.AsyncSQLClient
import io.vertx.reactivex.ext.sql.SQLConnection
import org.shredzone.acme4j.*
import org.shredzone.acme4j.challenge.Dns01Challenge
import org.shredzone.acme4j.util.CSRBuilder
import org.shredzone.acme4j.util.KeyPairUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.lang.RuntimeException
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit


fun <L, R> Observable<Either<L, R>>.obsKEitherT(): EitherT<ForObservableK, L, R>  = EitherT(this.k())
fun <L, R> Single<Either<L, R>>.obsKEitherT(): EitherT<ForObservableK, L, R>  = EitherT(this.toObservable().k())
fun <L, R> Observable<R>.obsKRightT(): EitherT<ForObservableK, L, R>  = EitherT(this.map{it.right()}.k())
fun <L, R> Single<R>.obsKRightT(): EitherT<ForObservableK, L, R>  = EitherT(this.toObservable().map{it.right()}.k())


class LetSEncryptManagerImpl(
        private val config: LetSEncryptConfig,
        private val letSEncryptUser: LetSEncryptUser,
        private val dnsManager: DnsManager): LetSEncryptManager {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(LetSEncryptManagerImpl::class.java)
    }

    private val session = Session(config.server)

    override fun orderCertificate(domain: String, subdomain: Option<String>, isWildCard: Boolean): Single<Either<Error, LetSEncryptCertificate>> {

        val effectiveDomain = subdomain
                .map { s ->
                    if (s.endsWith(".")) {
                        s
                    } else {
                        "$s."
                    }
                }
                .map { "$it$domain" }
                .getOrElse { domain }

        val obsK: ObservableK<Either<Error, LetSEncryptCertificate>> = EitherT.monadError<ForObservableK, Error>(ObservableK.monad()).binding {

            val acc = letSEncryptUser.getOrCreateAccount(config.accountId).obsKRightT<Error, LetSEncryptAccount>().bind()
            LOGGER.info("Get or create account {}", effectiveDomain)
            //Then build acme account
            val account = Observable.fromCallable {
                val account = AccountBuilder()
                        .agreeToTermsOfService()
                        .useKeyPair(acc.keys)
                        .create(session)
                account.right()
            }.observeOn(Schedulers.io()).obsKEitherT().bind()

            LOGGER.info("ordering certificate for {}", effectiveDomain)
            val order = orderLetsEncryptCertificate(account, effectiveDomain, isWildCard).obsKRightT<Error, Order>().bind()

            doChallenges(order, domain, effectiveDomain, subdomain).obsKEitherT().bind()

            LOGGER.info("building csr for {}", effectiveDomain)
            val privateKey = KeyPairUtils.createKeyPair(2048)
            val (csrString, csrByteString) = buildCsr(effectiveDomain, isWildCard, privateKey)

            LOGGER.info("ordering certificate for {}", effectiveDomain)

            orderCertificate(order, csrByteString).obsKEitherT().bind()

            LOGGER.info("storing certificate for {}", effectiveDomain)

            val certificate: X509Certificate = order.certificate!!.certificate

            val chain = getCertificateChain(order).obsKRightT<Error, List<X509Certificate>>().bind()

            val expire = LocalDateTime.ofInstant(certificate.notAfter.toInstant(), ZoneId.systemDefault())

            LetSEncryptCertificate(effectiveDomain, privateKey, csrString, Certificate(certificate, expire, chain))
        }.fix().value.fix()

        return obsK
                .observable
                .singleOrError()
                .doOnSuccess { _ ->
                    removeLetsEncrypotDnsRecords(domain, subdomain)
                        .map { it.fold({
                          l -> throw RuntimeException(l.message)
                        }, { it })}
                        .retry(3) { e -> true }
                        .subscribe({ s ->

                        }, { e ->
                            LOGGER.error("Error removing records for domain $domain and subdomain $subdomain", e)
                        })
                }
    }


    private fun doChallenges(order: Order, rootDomain: String, domain: String, subdomain: Option<String>): Single<Either<Error, List<Status>>> {
        return Observable.fromIterable(order.authorizations)
                .flatMap {  authorization ->
                    LOGGER.info("validating authorizations for domain {}", domain)
                    val challenge = authorization.findChallenge<Dns01Challenge>(Dns01Challenge.TYPE) as Dns01Challenge
                    LOGGER.info("creating dns record for domain {}", domain)
                    createLetSEncryptDnsRecord(rootDomain, subdomain, challenge).toObservable()
                            .flatMap {
                                it.fold({
                                    Observable.just(Left(it))
                                }, { r ->
                                    LOGGER.info("polling dns domain {}, {}", rootDomain, r)
                                    pollDns(rootDomain, r).toObservable()
                                })
                            }.flatMap {
                                it.fold({
                                    Observable.just(Left(it))
                                }, { _ ->
                                    LOGGER.info("authorizing order for domain {}", domain)
                                    authorizeOrder(domain, authorization.status, challenge).toObservable()
                                })
                            }
                }.toList().map { eithers ->
                    eithers.fold(Right(emptyList<Status>()) as Either<Error, List<Status>>) { acc, either ->
                        when(either) {
                            is Either.Right ->
                                acc.map { it.plus(either.b) }
                            is Either.Left ->
                                Left(Error(either.a.message))
                        }
                    }
                }
    }


    private fun getCertificateChain(order: Order) =
            Observable.fromCallable { order.certificate!!.certificateChain }.observeOn(Schedulers.io())

    private fun buildCsr(domain: String, isWildCard: Boolean, privateKey: KeyPair): Pair<String, ByteArray>  {
        val csrb = CSRBuilder()
        csrb.addDomains(domain)
        if (isWildCard) {
            csrb.addDomains("*.$domain")
        }
        csrb.sign(privateKey)
        val stringWriter = StringWriter()
        csrb.write(stringWriter)
        return Pair(stringWriter.toString(), csrb.encoded)
    }

    private fun createLetSEncryptDnsRecord(rootDomain: String, subdomain: Option<String>, challenge: Dns01Challenge): Single<Either<Error, Record>> {
        val digest = challenge.digest
        val acmeSubdomain = subdomain.map { "_acme-challenge.${it}" }.getOrElse { "_acme-challenge" }

        val record = Record(
                target = digest,
                subDomain = acmeSubdomain,
                fieldType = "TXT",
                ttl = 0
        )
        LOGGER.info("Fetching domain {}", rootDomain)

        return dnsManager.getDomain(rootDomain).flatMap { d ->
            LOGGER.info("Domain found : {}", d)
            val mayBeR = d.records.find { it.subDomain == acmeSubdomain }
            mayBeR.toOption()
                    .map { r ->
                        if (r.target == digest) {
                            LOGGER.info("Nothing to do on DNS record {}", r)
                            Single.just(Right(r) as Either<Error, Record>)
                        } else {
                            LOGGER.info("Creating DNS record {}", record)
                            dnsManager.createRecord(rootDomain, record).map { response -> response.map { _ -> record } }
                        }
                    }
                    .getOrElse {
                        LOGGER.info("Creating DNS record {}", record)
                        dnsManager.createRecord(rootDomain, record).map { response -> response.map { _ -> record } }
                    }
        }
    }

    private fun removeLetsEncrypotDnsRecords(rootDomain: String, subdomain: Option<String>): Single<Either<Error, Unit>> {
        val acmeSubdomain = subdomain.map { "_acme-challenge.${it}" }.getOrElse { "_acme-challenge" }
        return dnsManager.getDomain(rootDomain).flatMap { domain ->
            val toDelete = domain.records.filter { it.subDomain == acmeSubdomain }
            Observable.fromIterable(toDelete)
                    .flatMap {record ->
                        dnsManager.deleteRecord(rootDomain, record.id!!).toObservable()
                    }
                    .toList()
                    .map { res ->
                        ListK(res).sequence(Either.applicative()).fix()
                    }
                    .map { it.map { _ -> Unit }}
        }
    }

    private fun pollDns(domain: String, record: Record): Single<Either<Error, Pair<Record, List<String>>>> =
            Observable
                    .interval(30, TimeUnit.SECONDS)
                    .take(6, TimeUnit.HOURS)
                    .flatMap { _ ->
                        LOGGER.info("New DNS check")
                        dnsManager
                                .checkRecord(domain, record)
                                .onErrorReturnItem(emptyList())
                                .toObservable()
                                .doOnNext { records ->
                                    LOGGER.info("New dns record found : {}, expected {}", records, record.target)
                                }
                    }
                    .filter { it.isNotEmpty() && it.contains(record.target) }
                    .take(1)
                    .map { l -> Right(record to l) as Either<Error, Pair<Record, List<String>>> }
                    .single(Left(Error("Dns record not found after 6 hours ")) as Either<Error, Pair<Record, List<String>>>)
                    .doOnSuccess { event ->
                        event.fold({
                            err -> LOGGER.error("Error polling dns record {}: {}", record, err)
                        }, {
                            _ -> LOGGER.error("Success polling dns {}", record)
                        })
                    }


    private fun orderLetsEncryptCertificate(account: Account, domain: String, isWildCard: Boolean): Single<Order> {
        LOGGER.info("ordering certificate {}", domain)
        return if (isWildCard) {
            Single.fromCallable { account.newOrder().domains(domain, "*.$domain").create() }.observeOn(Schedulers.io())
        } else {
            return Single.fromCallable { account.newOrder().domains(domain).create() }.observeOn(Schedulers.io())
        }
    }


    private fun authorizeOrder(domain: String, status: Status, challenge: Dns01Challenge): Single<Either<Error, Status>> {
        LOGGER.info("Authorizing order {}", domain)
        if (status == Status.VALID) {
            return Single.just(Right(Status.VALID) as Either<Error, Status>)
        } else {
            if (challenge.status ==  Status.VALID) {
                return Single.just(Right(Status.VALID) as Either<Error, Status>)
            } else {
                challenge.trigger()
                return Observable
                        .interval(1, TimeUnit.SECONDS)
                        .flatMap { _ ->
                            Observable
                                    .fromCallable {
                                        challenge.update()
                                        challenge.status
                                    }
                                    .observeOn(Schedulers.io())
                        }
                        .take(10)
                        .filter { it == Status.VALID }
                        .take(1)
                        .map { s -> Right(s) as Either<Error, Status> }
                        .single(Left(Error("Challenge not accepted for $domain")))
                        .doOnSuccess { event ->
                            event.fold({
                                err -> LOGGER.error("Error authorizing order {}", err)
                            }, {
                                _ -> LOGGER.error("Order for domain {} authorized ", domain)
                            })
                        }
            }
        }
    }

    private fun orderCertificate(order: Order, csr: ByteArray): Single<Either<Error, Order>> =
            Single.fromCallable {
                order.execute(csr)
                order
            }
                    .observeOn(Schedulers.io())
                    .flatMap { o ->
                        Observable
                                .interval(3, TimeUnit.SECONDS)
                                .flatMap { _ ->
                                    Observable
                                            .fromCallable {
                                                o.update()
                                                o
                                            }
                                            .observeOn(Schedulers.io())

                                }
                                .take(10)
                                .filter { it.status == Status.VALID }
                                .take(1)
                                .map { Right(it) as Either<Error, Order> }
                                .single(Left(Error("Certificate not accepted for ${order.domains}")) as Either<Error, Order>)
                    }
                    .doOnSuccess { event ->
                        event.fold({
                            err -> LOGGER.error("Error ordering certificate {}", err)
                        }, {
                            _ -> LOGGER.error("Ordering certificate for {} succed", order.domains)
                        })
                    }
}

interface LetSEncryptUser {
    fun getOrCreateAccount(id: String): Single<LetSEncryptAccount>
}

class LetSEncryptUserPostgres(private val pgClient: AsyncSQLClient) : LetSEncryptUser {

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(LetSEncryptUserPostgres::class.java)
        const val KEY_SIZE = 2048
        const val TABLE_NAME = "account"
    }

    override fun getOrCreateAccount(id: String): Single<LetSEncryptAccount> {
        LOGGER.info("Get or create certificate account for {}", id)
        return pgClient
                .rxGetConnection()
                .flatMap { connexion ->
                    connexion
                            .rxSetAutoCommit(false)
                            .toSingleDefault(Unit).flatMap {
                                getAccount(connexion, id)
                                        .flatMap { mayBeAccount ->
                                            mayBeAccount.map { account ->
                                                Single.just(account)
                                            }.getOrElse {
                                                createAccount(connexion, id)
                                            }
                                        }
                            }.onErrorResumeNext { e ->
                                connexion.rxRollback()
                                        .toSingle { Unit }
                                        .flatMap { Single.error<LetSEncryptAccount>(e) }
                            }.flatMap { a ->
                                connexion.rxCommit().toSingle { a }
                            }.doFinally {
                                connexion.rxClose().subscribe()
                            }
                }
    }

    private fun getAccount(connexion: SQLConnection, id: String): Single<Option<LetSEncryptAccount>> {
        return connexion
                .rxQueryWithParams("SELECT payload FROM $TABLE_NAME where accountId = ? ", json { array(id) })
                .map { rs ->
                    val rows = rs.rows
                    if (rows.isEmpty()) {
                        None
                    } else {
                        val json = JsonObject(rows.first().getString("payload"))
                        Some(LetSEncryptAccount.fromJson(json))
                    }
                }
    }

    private fun createAccount(connexion: SQLConnection, id: String): Single<LetSEncryptAccount> {
        val account = LetSEncryptAccount(id, KeyPairUtils.createKeyPair(KEY_SIZE))
        return connexion
                .rxUpdateWithParams(
                        """INSERT INTO $TABLE_NAME (accountId, payload) VALUES (?, ?::JSON)""",
                        json { array(id, account.toJson().encode()) }
                )
                .map { _ -> account }
    }

}
