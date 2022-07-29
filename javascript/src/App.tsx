import * as React from 'react';
import {Component} from 'react';
import {CertificateHistoryPage, DomainsPage, HomePage, UnauthorizedPage} from "./pages";
import {Navigate, Route, Routes} from 'react-router'
import {BrowserRouter, Link} from 'react-router-dom'
import {User} from "./commons/User";


export class LoggedApp extends Component<any> {

    render() {
        const pathname = window.location.pathname;
        const className = (part: String) => part === pathname ? 'active' : 'inactive';

        return (
            <div className="izanami-container container-fluid">
                <nav className="navbar navbar-inverse navbar-fixed-top">
                    <div className="navbar-header col-md-2"><a href="/" className="navbar-brand"
                                                               style={{display: 'flex'}}>Let's automate</a>
                    </div>
                    <div className="container-fluid">
                        <div id="navbar" className="navbar-collapse collapse">
                            <ul className="nav navbar-nav navbar-right">
                                <li><a
                                    href={this.props.logout}>{this.props.user ? this.props.user.email : ''}&nbsp;
                                    <span className="glyphicon glyphicon-off"/></a></li>
                            </ul>
                            <form className="navbar-form navbar-left">
                                <div className="form-group" style={{marginRight: 10}}>
                                </div>
                            </form>
                        </div>
                    </div>
                </nav>

                <div className="container-fluid">
                    <div className="row">
                        <div className="analytics-viewer-bottom-container"
                             style={{display: 'flex', flexDirection: 'row', width: '100%', height: '100%'}}>
                            <div className="col-md-2 sidebar">
                                <div className="sidebar-container">
                                    <div className="sidebar-content">
                                        <ul className="nav nav-sidebar">
                                            <li className={className("/")}>
                                                <Link to="/"><h3 style={{marginTop: 0, marginLeft: -25}}><i
                                                    className="fa fa-tachometer"/> Home
                                                </h3>
                                                </Link>
                                            </li>
                                            <li className={className("/identities")}>
                                                <Link to="/domains" style={{cursor: 'pointer'}}><i
                                                    className="fa fa-id-badge"/>Domains</Link>
                                            </li>
                                        </ul>
                                    </div>
                                    <div className="logoContent"><img className="logo"
                                                                      src="/assets/img/letsAutomate.png"/></div>
                                </div>
                            </div>
                            <div className="col-md-10 col-md-offset-2 main">
                                <div className="row">
                                    <div className="izanami-container">
                                        <div className="row">
                                            <Routes>
                                                <Route path="/" element={<HomePage/>} />
                                                <Route path="/domains" element={<DomainsPage/>} />
                                                <Route path="/domains/:id/history" element={<CertificateHistoryPage/>}/>
                                                <Route path="/unauthorized" element={<UnauthorizedPage/>}/>
                                            </Routes>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        )
    }
}

export class LetSAutomate extends React.Component {

    render() {
        return (
            <Routes>
                <Route key="route-login" path="/unauthorized" element={<UnauthorizedPage/>}/>,
                <Route key="private-route" path="*"
                       element={<PrivateRoute {...this.props}><LoggedApp {...this.props}/></PrivateRoute>}/>
            </Routes>
        )
    }
}

type PrivateRouteProps = {
    user: User,
    children: JSX.Element
}

const PrivateRoute: React.FC = ({children, user, ...rest}: PrivateRouteProps) => {
    return ((!user || (user && !user.email)) ? <Navigate to={'/unauthorized'}/> : <>{children}</>)
}

type LetsRoutedProps = {
    user: User,
    logout: String
}

export const RoutedLetSAutomateApp = (props: LetsRoutedProps) => (
    <BrowserRouter basename="/">
        <LetSAutomate {...props}/>
    </BrowserRouter>
);
