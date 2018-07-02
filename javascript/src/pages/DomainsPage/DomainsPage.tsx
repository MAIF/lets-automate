import * as React from 'react';
import {Component} from 'react';
import {Title} from "../../components/Title";
import {Domain, listDomains} from "../../services/DomainsService";
import ReactTable, {RowInfo, SubComponentFunction} from "react-table";
import {
    CertificateEvent,
    CertificateOrderFailure,
    CertificateReOrderFailure,
    Command,
    CreateCertificate,
    listCertificates,
    onCertificateEvent,
    OrderCertificate,
    PublishCertificate,
    DeleteCertificate,
    sendCommand,
    unregister, StartRenewCertificate
} from "../../services/CertificatesServices";
import * as moment from "moment";
import {CalendarDate} from "../../components/CalendarDate";
import * as Notifications from '../../services/NotificationsService'
import {Link} from "react-router-dom";
import {Fade} from "../../components/Fade";
import {
    applyEventToState, isOnError,
    DomainAndCertificate, domainsPageState,
    DomainsPageState, emptyDomainsPageState,
    updateDomain, domainAndCertificateFromResume, DomainResumeProps, updateCertificateDomain
} from "./StateAndProps";
import {sortBy} from 'lodash'


export class DomainsPage extends Component<any, DomainsPageState> {

    constructor(props: any) {
        super(props);
        this.state = emptyDomainsPageState();
    }

    componentDidMount(): void {
        Promise
            .all([listDomains(), listCertificates()])
            .then(([domains, certificate]) => {
                const d: DomainAndCertificate[] = Array.from(domains.values()).map((d) => {
                    return domainAndCertificateFromResume(d.name, certificate.get(d.name))
                });
                console.log('Mounting with state ', d);
                this.setState(domainsPageState(d))
            });

        onCertificateEvent(this.onEvent);
    }

    componentWillUnmount() {
        unregister(this.onEvent)
    }

    onEvent = (event: CertificateEvent) => {
        console.log('New event', event);
        this.setState(applyEventToState(this.state, event));
    };

    sendCommand = (command: Command) => {
        sendCommand(command).then(([status, json]) => {
            if (status === 200) {
                Notifications.sendNotification({message: "Request sent"})
            } else if (status === 400) {
                this.setState(updateDomain(command.command.domain, this.state)( c => {
                    return {...c, error: json.message};
                }))
            }
        })
    };

    publishedDateButton = (domain: string, d: DomainResumeProps) => {
        if (d.publication && d.publication.error) {
            const command = PublishCertificate({domain, subdomain: d.subdomain});
            const title = d.publication.error.cause || '';
            return (
                <div>
                    <div>
                        <button
                            type="button"
                            className="btn btn-sm btn-danger"
                            title={title}
                            onClick={() => {
                                this.sendCommand(command)
                            }}>
                            Retry  <i className="far fa-question-circle text-danger" title={title} />
                        </button>
                    </div>
                </div>
            );
        } else if (d.publication && d.publication.publishDate) {
            return (
                <div>
                    <CalendarDate date={moment(d.publication.publishDate)}/>
                </div>
            )
        } else if (d.certificate) {
            return <button
                type="button"
                className="btn btn-sm btn-success"
                onClick={() => {
                    this.sendCommand(PublishCertificate({domain, subdomain: d.subdomain}))
                }}>
                <i className="glyphicon glyphicon-pencil"/> to clever cloud
            </button>
        } else {
          return;
        }
    };

    certificateButton = (domain: string, d: DomainResumeProps) => {
        if (d.isNew ) {
            return <button
                type="button"
                className="btn btn-sm btn-success"
                onClick={() => {
                    this.sendCommand(CreateCertificate({domain, subdomain: d.subdomain, wildcard: d.wildcard}))
                }}>
                Order certificate
            </button>
        } else if (d.certificate ) {

            if (d.certificate.error &&
                (d.certificate.error.type === 'CertificateOrderFailure' ||
                    d.certificate.error.type === 'CertificateReOrderFailure')) {

                const command = d.certificate.error.type === 'CertificateOrderFailure' ?
                    OrderCertificate({domain, subdomain: d.subdomain, wildcard: d.wildcard}) :
                    StartRenewCertificate({domain, subdomain: d.subdomain, wildcard: d.wildcard});
                const title = d.certificate.error.cause || '';
                return (
                    <div>
                        <button
                            type="button"
                            className="btn btn-sm btn-danger"
                            title={title}
                            onClick={() => {
                                this.sendCommand(command)
                            }}>
                            Retry  <i className="far fa-question-circle text-danger" title={title} />
                        </button>
                    </div>
                );
            } else if (d.certificate.isRunning) {
                return (
                    <div>
                        <div className="loader"/>
                    </div>
                )
            } else {
                const expireMoment = moment(d.certificate.expire)
                return (
                    <div>
                        <button
                            type="button"
                            title={"renew"}
                            className="btn btn-sm btn-success"
                            onClick={() => {
                                this.sendCommand(StartRenewCertificate({domain, subdomain: d.subdomain, wildcard: d.wildcard}))
                            }}>
                            Renew certificate
                        </button>
                        <span title={`Expire ${expireMoment.format("DD MMMM YYYY")}`}> (Expire <CalendarDate date={expireMoment}/>)</span>
                    </div>
                )
            }
        } else {
            return (
                <div>
                    <div className="loader"/>
                </div>
            )
        }
    };

    setPublicationUpdatedTo = (domain: string, index: number, value: boolean) => {
        this.setState(updateCertificateDomain(domain, index, this.state)(d => {
            if (d && d.certificate) {
                d.certificate.isUpdated = value;
            }
            return d;
        }));
    };

    setCertificateUpdatedTo = (domain: string, index: number, value: boolean) => {
        this.setState(updateCertificateDomain(domain, index, this.state)(d => {
            if (d && d.publication) {
                d.publication.isUpdated = value;
            }
            return d;
        }));
    };

    setWildcard = (domain: string, index: number) => (wildcard: boolean) => {
        this.setState(updateDomain(domain, this.state) (d => {
            return {...d, newCertificate:{...d.newCertificate,wildcard}};
        }));
    };

    setSubdomain = (domain: string, index: number) => (e: any )=> {
        const subdomain = e.target.value;
        this.setState(updateDomain(domain, this.state) (d => {
            return {...d, newCertificate:{...d.newCertificate,subdomain}};
        }));
    };

    addNewCertificate = (id:string) => () => {
        this.setState(updateDomain(id, this.state) (d => {
            return {...d, newCertificate:{isNew:true, wildcard: false}, error: null};
        }));
    };

    removeCertificate = (id:string) => () => {
        this.setState(updateDomain(id, this.state) (d => {
            return {...d, newCertificate:null, error: null};
        }));
    };

    displayCertificate: SubComponentFunction = (row: RowInfo) => {
        const rootDomain: DomainAndCertificate = row.original;
        const certificates = rootDomain.certificates || [];

        const columns = [{
            Header: 'Status',
            filterable: false,
            width: 80,
            Cell: (row: any) => {
                const d: DomainResumeProps = row.original;
                let certUpdated = (d.certificate && d.certificate.isUpdated);
                let publicationUpdated = (d.publication && d.publication.isUpdated);
                const isUpdated = certUpdated || publicationUpdated;
                const isError = d.certificate && ((d.certificate && d.certificate.error) || (d.publication && d.publication.error));
                return (
                    <Fade inProp={isUpdated} onEntered={() => {
                        if (certUpdated) {
                            this.setCertificateUpdatedTo(rootDomain.name, row.index, false)
                        } else if (publicationUpdated) {
                            this.setPublicationUpdatedTo(rootDomain.name, row.index, false)
                        }
                    }} duration={500}>
                        {isError &&
                            <div style={{textAlign: 'center', height: '100%'}}>
                                <span className="text-danger"><i className="fas fa-exclamation-triangle"/></span>
                            </div>
                        }
                        {!isError &&
                            <div style={{textAlign: 'center', height: '100%'}}>
                                <span><i className="fas fa-thumbs-up" /></span>
                            </div>
                        }
                    </Fade>
                );
            }
        }, {
            Header: 'Subdomain',
            accessor: 'subdomain',
            Cell: (row: any) => {
                const d: DomainResumeProps = row.original;
                if (d.isNew) {
                    return <input type="text" value={d.subdomain || ""} onChange={this.setSubdomain(rootDomain.name, row.index)}/>
                } else {
                    return <span>{d.subdomain}</span>
                }
            }
        }, {
            Header: 'Wildcard',
            accessor: 'wildcard',
            width: 80,
            Cell: (row: any) => {
                const d: DomainResumeProps = row.original;
                const w = d.wildcard || false;
                if (d.isNew) {
                    return (
                        <div className="text-center">
                            <input type="checkbox"
                                   checked={w}
                                   onChange={ () => this.setWildcard(rootDomain.name, row.index)(!w)}
                            />
                        </div>
                    );
                } else {
                    return (
                        <div className="text-center">
                            <input type="checkbox" checked={w} disabled/>
                        </div>
                    );
                }
            }
        }, {
            id: 'letsEncrypt',
            Header: "Let'encrypt",
            filterable: false,
            Cell: (row: any) => {
                const d: DomainResumeProps = row.original;
                const isUpdated = d.certificate && d.certificate.isUpdated;
                return (
                    <Fade onEntered={() => this.setCertificateUpdatedTo(rootDomain.name, row.index, false)} inProp={isUpdated} duration={500}>
                        <div style={{textAlign: 'center', height: '100%'}}>
                            {this.certificateButton(rootDomain.name, d)}
                        </div>
                    </Fade>
                );
            }
        }, {
            Header: 'Clever cloud',
            filterable: false,
            width: 150,
            Cell: (row: any) => {
                const d: DomainResumeProps = row.original;
                const isUpdated = d.publication && d.publication.isUpdated;
                return (
                    <Fade onEntered={() => this.setPublicationUpdatedTo(rootDomain.name, row.index, false)} inProp={isUpdated} duration={500}>
                        <div style={{textAlign: 'center', height: '100%'}}>
                            {this.publishedDateButton(rootDomain.name, d)}
                        </div>
                    </Fade>
                );
            }
        }, {
            Header: 'Actions',
            filterable: false,
            width: 80,
            Cell: (row: any) => {
                const d: DomainResumeProps = row.original;
                if (d.isNew) {
                    return (
                      <div className="text-center">
                        <button className="btn btn-sm btn-danger" onClick={this.removeCertificate(rootDomain.name)}><i className="fas fa-ban"/></button>
                      </div>
                    );
                } else {
                    return (
                      <div className="text-center">
                        <button
                        className="btn btn-sm btn-danger"
                        onClick={() => {
                            this.sendCommand(DeleteCertificate({domain: rootDomain.name, subdomain: d.subdomain}))
                        }}
                        >
                        <i className="fas fa-ban"/>
                      </button>
                    </div>
                  );
                }
            }
        }];


        let data;
        if (rootDomain.newCertificate) {
            data = [rootDomain.newCertificate, ...sortBy(certificates, [ (e:any) => {
                return e.subdomain || ''
            }])]
        } else {
            data = [...sortBy(certificates, [ (e:any) => {
                return e.subdomain || ''
            }])];
        }
        return (
            <div>

                {rootDomain.error &&
                    <span className="text-danger">{rootDomain.error}</span>
                }
                <div className="row">

                    <div className="col-sm-offset-1 col-sm-11 subTable">
                      <div className="pull-left text-right">
                        {!rootDomain.newCertificate &&
                        <button className="btn btn-sm btn-success" onClick={this.addNewCertificate(rootDomain.name)}><i className="fa fa-plus"/></button>
                        }
                        {rootDomain.newCertificate &&
                        <button className="btn btn-sm btn-success" disabled ><i className="fa fa-plus"/></button>
                        }
                      </div>
                        <ReactTable
                            className="fulltable -striped -highlight"
                            data={data}
                            sortable={false}
                            filterable={false}
                            defaultPageSize={10}
                            columns={columns}
                        />
                    </div>
                </div>
            </div>);
    };

    domains = () => {
        const columns = [{
            Header: 'Status',
            filterable: false,
            width: 100,
            Cell: (row: any) => {
                const d: DomainAndCertificate = row.original;
                let child;
                if (isOnError(d)) {
                    child = (
                        <div style={{textAlign: 'center', height: '100%'}}>
                            <span className="text-danger"><i className="fas fa-exclamation-triangle"/></span>
                        </div>
                    );
                } else {
                    child = (
                        <div style={{textAlign: 'center', height: '100%'}}>
                            <span><i className="fas fa-thumbs-up" /></span>
                        </div>
                    );
                }
                return child;
            }
        }, {
            Header: 'Domain',
            accessor: 'name'
        }, {
            Header: 'History',
            filterable: false,
            Cell: (row: any) => {
                const d: DomainAndCertificate = row.original;
                return (
                    <div style={{textAlign: 'center', height: '100%'}}>
                        <Link className="btn btn-sm btn-primary" to={`/domains/${d.name}/history`}>
                            <i className="glyphicon glyphicon-eye-open" />
                        </Link>
                    </div>
                );
            }
        }];
        const domains = sortBy(this.state.domains, [ (e:any) => {
            return e.name || ''
        }]);
        return (
            <ReactTable
                className="fulltable -striped -highlight"
                data={domains}
                sortable={true}
                filterable={true}
                defaultPageSize={10}
                columns={columns}
                SubComponent={this.displayCertificate}
                collapseOnDataChange={false}
            />
        );

    };

    public render() {
        return [
            <Title key="domain-title" title={"Domains"}/>,
            <div key="domain-body" className="row">
                <div className="col-md-12">
                    <div className="col-md-12">
                        <div className="row">
                            <div className="row">
                                <div>
                                    <div className="rrow">
                                        {this.domains()}
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        ];
    }
}
