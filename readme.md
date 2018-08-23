# Let's Automate

Automate Let's Encrypt certificate issuance, renewal and synchronize with CleverCloud.

<p align="center">
    <img src="https://raw.githubusercontent.com/MAIF/lets-automate/master/src/main/resources/public/img/letsAutomate.png?token=ABgKYW3Y2Gn5vNsGYGSAJjWaPA4ZTZSZks5bQ1bCwA%3D%3D" height="250">
    </img>
</p>

## Description

Let's automate allow you to create certificate and publish them to clever cloud with automatic renewal. 
Let's automate needs an ovh account in order to create DNS records to perform the let's encrypt DNS challenge. Let's automate is also integrated with slack so all the event are published to a dedicated topic.  

## Disclamer 

Let's automate is integrated with otoroshi (for the auth), ovh, clever cloud and slack. For the moment there is no other providers available. 
If you need this tools with other DNS provider or hosting provider your contributions are welcomed! 

## Deploy the app 

### Build the app 

```
git clone https://github.com/MAIF/lets-automate.git
cd javascript 
yarn install 
yarn build 
cd ..
gradlew shadowJar 
```

the jar is located in the folder `build/libs/letsautomate-shadow.jar`


### Ovh Key 

First you need to get a token to access ovh apis 

https://docs.ovh.com/gb/en/customer/first-steps-with-ovh-api/

```bash

curl -XPOST -H "X-Ovh-Application: YOUR_APPLICATION_ID" -H "Content-type: application/json" \
https://eu.api.ovh.com/1.0/auth/credential  -d '{
    "accessRules": [
        {
            "method": "GET",
            "path": "/*"
        }, 
        {
            "method": "POST",
            "path": "/*"
        }, 
        {
            "method": "PUT",
            "path": "/*"
        },
        {
            "method": "DELETE",
            "path": "/*"
        }
    ],
    "redirection":"https://localhost:8080"
}' --include

HTTP/1.1 200 OK
Date: Mon, 25 Jun 2018 08:57:43 GMT
Server: Apache
X-OVH-QUERYID: FR.ws-3.5b30ae87.26037.1707
Cache-Control: no-cache
Access-Control-Allow-Origin: *
Transfer-Encoding: chunked
Content-Type: application/json; charset=utf-8

{"validationUrl":"https://eu.api.ovh.com/auth/?credentialToken=A_CREDENTIAL_TOKEN","consumerKey":"A_CONSUMER_KEY","state":"pendingValidation"}%

```

Then go to the validation url and log in. 

Set the consumer key, your application id and secret in the configuration file. 

### Configuration 

| System Property  | Env variable  | Default |
| ---------------- | ------------- | ------------- |
| env | ENV | dev |
| http.port | HTTP_PORT | 8080 |
| http.host | HTTP_HOST | 0.0.0.0 |
| logout | LOGOUT_URL | |
| certificates.pollingInterval.period | LETSENCRYPT_POLLING_PERIOD | 5 |
| certificates.pollingInterval.unit | LETSENCRYPT_POLLING_UNIT | HOUR |
| ovh.applicationKey | OVH_APPLICATION_KEY | |
| ovh.applicationSecret | OVH_APPLICATION_SECRET | |
| ovh.consumerKey | OVH_CONSUMER_KEY | | 
| ovh.host | OVH_HOST | https://api.ovh.com |
| letsencrypt.server | LETSENCRYPT_SERVER | acme://letsencrypt.org/staging | 
| letsencrypt.accountId | LETSENCRYPT_ACCOUNT_ID | account | 
| postgres.host | POSTGRESQL_ADDON_HOST | localhost |
| postgres.port | POSTGRESQL_ADDON_PORT | 5432 |
| postgres.database | POSTGRESQL_ADDON_DB | lets_automate | 
| postgres.username | POSTGRESQL_ADDON_USER | default_user |
| postgres.password | POSTGRESQL_ADDON_PASSWORD | password |
| clevercloud.host | CLEVER_HOST | https://api.clever-cloud.com/ | 
| clevercloud.consumerKey | CLEVER_CONSUMER_KEY | | 
| clevercloud.consumerSecret | CLEVER_CONSUMER_SECRET | |
| clevercloud.clientToken | CLEVER_CLIENT_TOKEN | |
| clevercloud.clientSecret | CLEVER_CLIENT_SECRET | | 
| otoroshi.headerRequestId | FILTER_REQUEST_ID_HEADER_NAME | |
| otoroshi.headerGatewayStateResp | FILTER_GATEWAY_STATE_RESP_HEADER_NAME | |
| otoroshi.headerGatewayState | FILTER_GATEWAY_STATE_HEADER_NAME | |
| otoroshi.headerClaim | FILTER_CLAIM_HEADER_NAME | |
| otoroshi.sharedKey | CLAIM_SHAREDKEY | |
| otoroshi.issuer | OTOROSHI_ISSUER | |
| slack.token | SLACK_TOKEN | |
| slack.channel | SLACK_CHANNEL | |
| slack.url | SLACK_URL | https://slack.com/api |

### Run the app 

```
java -jar letsautomate-shadow.jar \
    -Denv=prod \
    -Dovh.applicationKey=xxxx \
    -Dovh.applicationSecret=xxxx \
    -Dovh.consumerKey=xxxx \
    -Dletsencrypt.server=acme://letsencrypt.org \
    -Dclevercloud.consumerKey=xxxx \
    -Dclevercloud.consumerSecret=xxxx \
    -Dclevercloud.clientToken=xxxx \
    -Dclevercloud.clientSecret=xxxx \
    -Dslack.token=xxxx \
    -Dslack.channel=xxxx 

```

### Run the app with clever cloud

First create a postgresql add on. 

Then create a java app and set the following env variables : 

```
APP_ENV=prod
CACHE_DEPENDENCIES=true
CC_PRE_BUILD_HOOK=./clevercloud/hook.sh
CLEVER_CLIENT_SECRET=xxxx
CLEVER_CLIENT_TOKEN=xxxx
CLEVER_CONSUMER_KEY=xxxx
CLEVER_CONSUMER_SECRET=xxxx
CLEVER_HOST=https://api.clever-cloud.com
ENV=prod
JAVA_VERSION=8
LETSENCRYPT_ACCOUNT_ID=account
LETSENCRYPT_POLLING_PERIOD=1
LETSENCRYPT_POLLING_UNIT=HOURS
LETSENCRYPT_SERVER=acme://letsencrypt.org
OVH_APPLICATION_KEY=xxxx
OVH_APPLICATION_SECRET=xxxx
OVH_CONSUMER_KEY=xxxx
OVH_HOST=https://api.ovh.com
PORT=8080
SLACK_CHANNEL=xxxx
SLACK_TOKEN=xoxb-xxx
```

## Run in development

### Run the app

```bash

docker-compose up

OVH_APPLICATION_KEY=xxxx OVH_APPLICATION_SECRET=xxxx OVH_CONSUMER_KEY=xxxx ./gradlew run -P env=dev 

```

```bash
cd javascript 
yarn install 
yarn start 
``` 
 
