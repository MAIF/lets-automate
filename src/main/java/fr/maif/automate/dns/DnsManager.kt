package fr.maif.automate.dns

import arrow.core.Either
import fr.maif.automate.commons.Error
import io.reactivex.Observable
import io.reactivex.Single
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.get

object Unit

data class Domain(val name: String, val records: List<Record>)
data class DomainResume(val name: String)
data class Record(val id: Long? = null, val target: String, val ttl: Long, val fieldType: String, val subDomain: String) {
    companion object {
        fun fromJson(json: JsonObject): Record =
            Record(
                    id = json.getLong("entityId"),
                    target = json["target"],
                    ttl = json.getLong("ttl"),
                    fieldType = json["fieldType"],
                    subDomain = json["subDomain"]
            )
    }
}


interface DnsManager {
    fun listDomains(): Observable<DomainResume>
    fun getDomain(name: String): Single<Domain>
    fun checkRecord(domain: String, record: Record): Single<List<String>>
    fun createRecord(domain: String, record: Record): Single<Either<Error, Record>>
    fun updateRecord(domain: String, recordId: Long, record: Record): Single<Either<Error, Record>>
    fun deleteRecord(domain: String, recordId: Long): Single<Either<Error, Unit>>
}

