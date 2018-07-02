import * as React from 'react';
import {Component} from 'react';

interface TitleProps {
    title: String;
}

export class Title extends Component<TitleProps> {
    public render(): JSX.Element {
        if(!this.props.title) {
            return null;
        } else {
            return (
                <div className="fixedH3">
                    <h3 className="page-header">
                        {this.props.title}
                    </h3>
                </div>
            );
        }
    }
}