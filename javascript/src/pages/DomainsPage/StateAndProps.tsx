import {
    CertificateCreated, CertificateDeleted,
    CertificateError,
    CertificateEvent, CertificateOrdered,
    CertificateOrderFailure,
    CertificatePublished,
    CertificatePublishFailure,
    CertificateReOrdered, CertificateReOrderedStarted, CertificateReOrderFailure,
    CertificateResume,
    DomainResume,
    PublicationResume
} from "../../services/CertificatesServices";

export interface DomainsPageState {
    domains: DomainAndCertificate[];
}

export function emptyDomainsPageState():DomainsPageState {
    return {domains: []};
}

export function domainsPageState(domains: DomainAndCertificate[]):DomainsPageState {
    return {domains};
}

export interface Updated {
    isUpdated: boolean;
}

export interface ErrorProps {
    error?: CertificateError
}

export interface DomainResumeProps {
    isNew: boolean;
    subdomain?: string;
    wildcard?: boolean;
    certificate?: CertificateResumeProps & Updated & ErrorProps;
    publication?: PublicationResumeProps & Updated & ErrorProps;
}
export interface CertificateResumeProps {
    isRunning?: boolean;
    expire?: string;
}
export interface PublicationResumeProps {
    publishDate?: string;
}

export interface DomainAndCertificate {
    name: string;
    error?: string;
    newCertificate?: DomainResumeProps;
    certificates?: DomainResumeProps[];
}

export function domainAndCertificateFromResume(name:string, domainResume: DomainResume) {
    if (domainResume) {
        const {certificates, domain} = domainResume;
        const c = certificates.map(cert => {
            const {subdomain, wildcard, error} = cert;
            let certificate: CertificateResume & Updated & ErrorProps;
            if (cert.certificate) {
                certificate = {...cert.certificate, isUpdated: false};
            }
            let publication: PublicationResume & Updated & ErrorProps;
            if (cert.publication) {
                publication = {publishDate: cert.publication.publishDate, isUpdated: false};
            }
            if (error) {
                if (error.type === 'CertificateOrderFailure' || error.type === 'CertificateReOrderFailure') {
                    certificate = { error, isUpdated: false, expire: null };
                } else if (error.type === 'CertificatePublishFailure') {
                    publication = { error, isUpdated: false, publishDate: null};
                }
            }
            return  {subdomain, wildcard, isNew: false, certificate, publication}
        });

        return {
            name: domain,
            certificates: c
        }
    } else{
        return {
            name
        };
    }
}


export const updateDomain = (id: string, current: DomainsPageState) => (func: (e:DomainAndCertificate) => DomainAndCertificate): DomainsPageState => {
    const mayBeDomain = current.domains.find(d => d.name === id);
    let newDomainAndCert = func(mayBeDomain);
    return {...current, domains:[...current.domains.filter(d => d.name !== id), newDomainAndCert]};
};


export const updateCertificateDomain = (domain: string, index: number, current: DomainsPageState) => (func: (e:DomainResumeProps) => DomainResumeProps): DomainsPageState => {
    const mayBeDomain = current.domains.find(d => d.name === domain);
    if (mayBeDomain) {
        let certificates = [...mayBeDomain.certificates];
        const d: DomainResumeProps = certificates[index];
        if (d) {
            certificates[index] = func(d);
        }
        let newDomain = {...mayBeDomain, certificates};
        return {...current, domains:[...current.domains.filter(d => d.name !== domain), newDomain]};
    } else {
        return current;
    }
};


export const updateCertificateDomainByName = (domain: string, name: string, current: DomainsPageState) => (func: (e:DomainResumeProps) => DomainResumeProps): DomainsPageState => {
    const mayBeDomain = current.domains.find(d => d.name === domain);
    if (mayBeDomain) {
        let certificates;
        const currentCert = (mayBeDomain.certificates || []);
        let exists = currentCert.find(c => c.subdomain == name);
        if (exists) {
            certificates = currentCert.map(c => {
                if (c.subdomain == name) {
                    return func(c)
                } else {
                    return c
                }
            });
        } else {
            certificates = [func({isNew:false}), ...currentCert];
        }
        let newDomain = {...mayBeDomain, certificates};
        return {...current, domains:[...current.domains.filter(d => d.name !== domain), newDomain]};
    } else {
        return current;
    }
};


export function isOnError(d: DomainAndCertificate): boolean {
    if (d && d.certificates) {
        d.certificates.map(c =>
            ((c.certificate && c.certificate.error) || (c.publication && c.publication.error)) || false
        ).reduce((acc, c) => acc || c, false)
    }
    return false;
}


export function applyEventToState(current: DomainsPageState, event: CertificateEvent): DomainsPageState {
    switch (event.type) {
        case "CertificateCreated":
            return handleCertificateCreated(current, event);
        case "CertificateOrdered":
            return handleCertificateOrdered(current, event);
        case "CertificateOrderFailure":
            return handleCertificateOrderedFailure(current, event);
        case "CertificateReOrderedStarted":
            return handleCertificateReOrderedStarted(current, event);
        case "CertificateReOrdered":
            return handleCertificateReOrdered(current, event);
        case "CertificateReOrderFailure":
            return handleCertificateReOrderFailure(current, event);
        case "CertificatePublished":
            return handleCertificatePublished(current, event);
        case "CertificatePublishFailure":
            return handleCertificatePublishFailure(current, event);
        case "CertificateDeleted":
            return handleCertificateDeleted(current, event);
        default:
            return;
    }
}

function handleCertificateDeleted(state: DomainsPageState, event: CertificateEvent): DomainsPageState {
    const certificateDeleted = event.event as CertificateDeleted;
    return updateDomain(certificateDeleted.domain, state)(c => {
        const certificates = c.certificates.filter(c => c.subdomain != certificateDeleted.subdomain)
        return {
            ...c,
            certificates
        };
    });
}


function handleCertificatePublishFailure(state: DomainsPageState, event: CertificateEvent): DomainsPageState {
    const certificatePublishFailure = event.event as CertificatePublishFailure;
    return updateCertificateDomainByName(certificatePublishFailure.domain, certificatePublishFailure.subdomain, state)(c => {
        return {
            ...c,
            publication: {
                error: {
                    type: "CertificatePublishFailure",
                    cause: certificatePublishFailure.cause
                },
                isUpdated: true
            }
        };
    });
}

function handleCertificatePublished(state: DomainsPageState, event: CertificateEvent) {
    const certificatePublished = event.event as CertificatePublished;
    return updateCertificateDomainByName(certificatePublished.domain, certificatePublished.subdomain, state)(c => {
        return {
            ...c,
            publication: {
                publishDate: certificatePublished.dateTime,
                isUpdated: true
            }
        };
    });
}

function handleCertificateReOrderFailure(state: DomainsPageState, event: CertificateEvent) {
    const certificateReOrderFailure = event.event as CertificateReOrderFailure;
    return updateCertificateDomainByName(certificateReOrderFailure.domain, certificateReOrderFailure.subdomain, state)(c => {
        return {
            ...c,
            certificate: {
                error: {
                    type: "CertificateReOrderFailure",
                    cause: certificateReOrderFailure.cause
                },
                isUpdated: true
            }
        };
    });
}

function handleCertificateReOrderedStarted(state: DomainsPageState, event: CertificateEvent) {
    const certificateReOrderedStarted = event.event as CertificateReOrderedStarted;
    return updateCertificateDomainByName(certificateReOrderedStarted.domain, certificateReOrderedStarted.subdomain, state)(c => {
        return {
            ...c,
            certificate: {
                isRunning: true,
                isUpdated: false
            }
        };
    });
}

function handleCertificateReOrdered(state: DomainsPageState, event: CertificateEvent) {
    const certificateReOrdered = event.event as CertificateReOrdered;
    return updateCertificateDomainByName(certificateReOrdered.domain, certificateReOrdered.subdomain, state)(c => {
        return {
            ...c,
            certificate: {
                isRunning: false,
                expire: certificateReOrdered.expire,
                isUpdated: true
            }
        };
    });
}

function handleCertificateOrderedFailure(state: DomainsPageState, event: CertificateEvent) {
    const certificateOrderFailure = event.event as CertificateOrderFailure;
    return updateCertificateDomainByName(certificateOrderFailure.domain, certificateOrderFailure.subdomain, state)(c => {
        return {
            ...c,
            certificate: {
                error: {
                    type: "CertificateOrderFailure",
                    cause: certificateOrderFailure.cause
                },
                isUpdated: true
            }
        };
    });
}

function handleCertificateOrdered(state: DomainsPageState, event: CertificateEvent) {
    const certificateOrdered = event.event as CertificateOrdered;
    return updateCertificateDomainByName(certificateOrdered.domain, certificateOrdered.subdomain, state)(c => {
        return {
            ...c,
            certificate: {
                expire: certificateOrdered.expire,
                isUpdated: true
            }
        };
    });
}

function handleCertificateCreated(state: DomainsPageState, event: CertificateEvent) {
    const certificateCreated = event.event as CertificateCreated;
    const tmpState = updateDomain(certificateCreated.domain, state)(d => {
        return {...d, newCertificate: null}
    });
    return updateCertificateDomainByName(certificateCreated.domain, certificateCreated.subdomain, tmpState)(c => {
       return {
            isNew: false,
            subdomain: certificateCreated.subdomain,
            wildcard: certificateCreated.wildcard
       };
    });
}