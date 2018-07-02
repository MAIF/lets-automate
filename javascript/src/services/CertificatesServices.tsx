import {Moment} from "moment";

export interface CertificateResume {
    expire: string;
}
export interface PublicationResume {
    publishDate: string;
}

export interface CertificateError {
    type: string;
    cause: string;
}

export interface CertificateInstanceResume {
    subdomain: string;
    wildcard?: boolean;
    certificate?: CertificateResume;
    publication?: PublicationResume;
    error?: CertificateError;
}

export interface DomainResume {
    domain?: string;
    certificates: CertificateInstanceResume[];
}

export interface CreateCertificateCommand { domain: string; subdomain?: string; wildcard: boolean}
export interface OrderCertificateCommand { domain: string; subdomain?: string; wildcard: boolean }
export interface StartRenewCertificateCommand { domain: string; subdomain?: string; wildcard: boolean }
export interface RenewCertificateCommand { domain: string; subdomain?: string; wildcard: boolean }
export interface PublishCertificateCommand { domain: string; subdomain?: string }
export interface DeleteCertificateCommand { domain: string; subdomain?: string }
export type CertificateCommand = CreateCertificateCommand | OrderCertificateCommand | StartRenewCertificateCommand | RenewCertificateCommand | PublishCertificateCommand | DeleteCertificateCommand
export interface Command {
    type: string;
    command: CertificateCommand;
}

export type CertificatePayload =
    CertificateCreated |
    CertificateOrdered |
    CertificateOrderFailure |
    CertificateReOrderedStarted |
    CertificateReOrdered |
    CertificateReOrderFailure |
    CertificatePublished |
    CertificatePublishFailure |
    CertificateDeleted
export interface CertificateCreated { domain: string; subdomain?: string; wildcard: boolean;}
export interface CertificateOrdered { domain: string; subdomain?: string; wildcard: boolean; expire: string;}
export interface CertificateOrderFailure { domain: string; subdomain?: string; cause: string;}
export interface CertificateReOrderedStarted { domain: string; subdomain?: string; wildcard: boolean; expire: string;}
export interface CertificateReOrdered { domain: string; subdomain?: string; wildcard: boolean; expire: string;}
export interface CertificateReOrderFailure {domain: string; subdomain?: string; cause: string;}
export interface CertificatePublished  { domain: string; subdomain?: string; dateTime: string;}
export interface CertificatePublishFailure {domain: string; subdomain?: string; cause: string;}
export interface CertificateDeleted {domain: string; subdomain?: string}
export interface CertificateEvent {
    type: string;
    event: CertificatePayload;
}

export function CreateCertificate(command: CreateCertificateCommand): Command {
    return {type: 'CreateCertificate', command}
}
export function OrderCertificate(command: OrderCertificateCommand): Command {
    return {type: 'OrderCertificate', command}
}
export function StartRenewCertificate(command: StartRenewCertificateCommand): Command {
    return {type: 'StartRenewCertificate', command}
}
export function RenewCertificate(command: RenewCertificateCommand): Command {
    return {type: 'RenewCertificate', command}
}
export function PublishCertificate(command: PublishCertificateCommand): Command {
    return {type: 'PublishCertificate', command}
}
export function DeleteCertificate(command: DeleteCertificateCommand): Command {
    return {type: 'DeleteCertificate', command}
}

export function listCertificates(): Promise<Map<string, DomainResume>> {
    return fetch(`/api/certificates`, {
        method: 'GET',
        credentials: 'include',
        headers: {
            'Accept': 'application/json'
        }
    })
        .then(res => res.json())
        .then((json: DomainResume[]) => {
            return new Map<string, DomainResume>(json.map(toTuple));
        });
}

export function listCertificatesEvents(id: string): Promise<CertificateEvent[]> {
    return fetch(`/api/certificates/${id}/_history`, {
        method: 'GET',
        credentials: 'include',
        headers: {
            'Accept': 'application/json'
        }
    })
        .then(res => res.json());
}

let callback: ((evt:CertificateEvent) => void)[] = [];
const evtSource = new EventSource("/api/certificates/_events");
evtSource.onmessage = function(e: any) {
    const event: CertificateEvent = JSON.parse(e.data);
    callback.forEach(fun => fun(event));
};
evtSource.onerror = function (e: any) {
    console.error("Error during sse", e);
};

export function onCertificateEvent(cb: (evt:CertificateEvent) => void) {
    callback = [...callback, cb]
}

export function unregister(cb: (evt:CertificateEvent) => void) {
    callback = callback.filter(c => c != cb)
}

export function sendCommand(command: Command) {
    return fetch(`/api/certificates/_commands`, {
        method: 'POST',
        credentials: 'include',
        headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json'
        },
        body: JSON.stringify(command)
    })
    .then(res =>
        res.json().then(json =>
            [res.status, json]
        )
    );
}


function toTuple(c: DomainResume): [string, DomainResume]{
    return [c.domain, c];
}