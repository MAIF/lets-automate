import * as React from 'react';
import {Component} from 'react';
import {UnauthorizedPage, HomePage, DomainsPage, CertificateHistoryPage} from "./pages";
import {Route, RouteProps, RouteComponentProps, Switch, Redirect, withRouter} from 'react-router'
import {BrowserRouter, Link} from 'react-router-dom'
import {User} from "./commons/User";


export class LoggedApp extends Component<any> {

    render() {
        const pathname = window.location.pathname;
        const className = (part: String) => part === pathname ? 'active' : 'inactive';

        return (
            <div className="izanami-container">
              <nav className="navbar navbar-expand-md fixed-top p-0">
                <button className="navbar-toggler" type="button" data-toggle="collapse" data-target="#sidebar" aria-controls="sidebar" aria-expanded="false" aria-label="Toggle navigation">
                  Menu
                </button>
                <a className="navbar-header navbar-brand col-12 col-md-2" href="#">Let's Automate</a>
                 <ul className="navbar-nav ml-auto">
                   <li className="nav-item dropdown">
                     <a className="" href="#" id="navbarDropdown" role="button" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                       <i className="fas fa-cog" aria-hidden="true"></i>
                     </a>
                     <div className="dropdown-menu dropdown-menu-right" aria-labelledby="navbarDropdown">
                       <a className="dropdown-item" href="#">{this.props.user ? this.props.user.email : ''}</a>
                     </div>
                   </li>
                 </ul>
              </nav>
              <div className="container-fluid">
                <div className="row">
                    <div className="col-md-2 sidebar" id="sidebar">
                      <div className="sidebar-container">
                        <div className="sidebar-content">
                          <ul className="nav nav-sidebar flex-column">

                            <li className={className("/")}>
                                <Link to="/"><h2 style={{marginTop: 0, marginLeft: -25}}><i className="fas fa-tachometer-alt"/> Home
                                </h2>
                                </Link>
                            </li>
                            <li className={className("/identities")}>
                                <Link to="/domains" style={{cursor: 'pointer'}}><i
                                    className="far fa-id-badge"/>Domains</Link>
                            </li>
                          </ul>
                        </div>
                        <div className="logoContent"><img className="logo" src="/assets/img/letsAutomate.png"/></div>
                      </div>
                    </div>
                    <div className="col-xs col-sm-10 offset-sm-2 main">
                            <div className="row">
                                <Switch>
                                    <Route exact path="/" component={ HomePage } />
                                    <Route exact path="/domains" component={ DomainsPage } />
                                    <Route exact path="/domains/:id/history" component={ CertificateHistoryPage } />
                                    <Route exact path="/unauthorized" component={ UnauthorizedPage } />
                                </Switch>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        )
    }
}

export class LetSAutomate extends Component<any> {

    render() {
        return (
            <Switch>
                <Route key="route-login" path="/unauthorized" component={UnauthorizedPage}/>,
                <PrivateRoute key="private-route" path="/" component={LoggedApp} {...this.props} />
            </Switch>
        )
    }
}

const PrivateRoute: React.SFC<RouteProps & User & any> = ({ component: Component, user: User, ...rest }) => {
    return (
        <Route {...rest} render={( props: RouteComponentProps<{}>) => {
            if (!User || (User && !User.email)) {
                return <Redirect to={{
                    pathname: '/unauthorized',
                    state: { from: props.location }
                }} />;
            } else {
                return <Component {...rest} {...props} user={User} />;
            }
        }}/>
    );
};

const LetSAutomateAppRouter = withRouter(LetSAutomate);

export class RoutedLetSAutomateApp extends Component<any> {
    render() {
        return (
            <BrowserRouter basename="/">
                <LetSAutomateAppRouter {...this.props}/>
            </BrowserRouter>
        );
    }
}
