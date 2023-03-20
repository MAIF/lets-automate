import {useEffect, useState} from 'react';
import {useParams} from "react-router"
import {CertificateEvent, listCertificatesEvents} from "../services/CertificatesServices";
import ReactTable from "react-table";
import {Link} from "react-router-dom";
import React = require('react');


export function CertificateHistoryPage() {
    const [events, setEvents] = useState<CertificateEvent[]>([])
    const {id} = useParams();
    useEffect(() => {
        listCertificatesEvents(id).then(events => setEvents(events))
    }, [id])

    function getColumns() {
        return [{
            Header: 'Type',
            width: 300,
            Cell: (row: any) => {
                const d: CertificateEvent = row.original;
                return <span>{d.type}</span>;
            }
        }, {
            Header: 'Event',
            accessor: 'name',
            Cell: (row: any) => {
                const d: CertificateEvent = row.original;
                return <span>{JSON.stringify(d.event)}</span>;
            }
        }];
    }

    return (<div>
        <div className="fixedH3">
            <h3 className="page-header">
                <Link to={"/domains"}><i className="glyphicon glyphicon-chevron-left"/></Link>
                History
            </h3>
        </div>
        <div key="domain-body" className="row">
            <div className="col-md-12">
                <div className="col-md-12">
                    <div className="row">
                        <div className="row">
                            <div>
                                <div className="rrow">
                                    <ReactTable
                                        className="fulltable -striped -highlight"
                                        data={events}
                                        sortable={true}
                                        filterable={true}
                                        defaultPageSize={10}
                                        columns={getColumns()}/>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>)

}