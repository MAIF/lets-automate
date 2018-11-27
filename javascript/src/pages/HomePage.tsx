import * as React from 'react';
import {Component} from 'react';
import {Title} from "../components/Title";

interface HomePageProps {

}

export class HomePage extends Component<HomePageProps> {
    public render() {
        return [
                <Title title={"Domains"}/>,
                <div className="col text-center">
                    <h3>Let's automate</h3>
                    <img className="logo_dashboard" src="/assets/img/letsAutomate.png"/>
                </div>
        ];
    }
}
