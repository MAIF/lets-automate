import * as React from 'react';
import {Component} from 'react';
import {Title} from "../components/Title";

interface HomePageProps {

}

export class HomePage extends Component<HomePageProps> {
    public render() {
        return [
                <Title title={"Domains"}/>,
                <div className="row">
                    <div className="col-md-12">
                        <h1 className="text-center">Let's automate</h1>
                        <p></p>
                        <img className="logo_izanami_dashboard center-block" src="/assets/img/letsAutomate.png"/>
                    </div>
                </div>
        ];
    }
}