import * as React from "react";
import * as ReactDOMClient from 'react-dom/client';
import {RoutedLetSAutomateApp} from './App'
import * as $ from 'jquery';
import 'whatwg-fetch'
import 'react-table/react-table.css';

import './styles/main.scss'
import {User} from "./commons/User";

declare global {
    interface Window {
        $?: any;
        jQuery?: any;
    }
}

window.$ = $;
window.jQuery = $;

require('bootstrap/dist/js/bootstrap.min');



export function init(node: HTMLElement, strUser: string, logout: String) {
    let user: User = strUser ? JSON.parse(strUser) : null;
    const root = ReactDOMClient.createRoot(node);

    root.render(<RoutedLetSAutomateApp user={user} logout={logout} />);
}