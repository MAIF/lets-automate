import * as React from 'react';
import {Component} from 'react';
import {CertificateEvent, listCertificatesEvents} from "../services/CertificatesServices";
import {RouteComponentProps} from "react-router";
import ReactTable from "react-table";
import {Link} from "react-router-dom";


interface CertificateHistoryState {
    events: CertificateEvent[];
}


export class CertificateHistoryPage extends Component<RouteComponentProps<any>, CertificateHistoryState> {

    constructor(props: any) {
        super(props);
        this.state = {events: []};
    }

    componentDidMount() {
        const id = this.props.match.params.id;
        listCertificatesEvents(id).then(events => {
            this.setState({events})
        })
    }

    render() {

        const columns = [{
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


        return [
            <div className="fixedTitle">
                <h1 className="page-header">
                    <Link to={"/domains"}><i className="fas fa-chevron-left fa--margin-right"/></Link>
                    History
                </h1>
            </div>,
            <div key="domain-body" className="col-md-12">
                <div className="rrow">
                    <ReactTable
                        className="fulltable -striped -highlight"
                        data={this.state.events}
                        sortable={true}
                        filterable={true}
                        defaultPageSize={10}
                        columns={columns}
                    />
                </div>
            </div>
        ];
    }
}
