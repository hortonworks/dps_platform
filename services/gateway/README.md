# Gateway

## Functionality
01. [x] POST /auth/in with correct request body should set a cookie and return UserContext
02. [x] GET /auth/out should log users out and delete dp_jwt and hadoop-jwt cookies
03. [-] BasicAuthPreFilter - read basic auth and set usercontext
04. [x] TokenAuthPreFilter - check dp_jwt cookie or bearer token and set usercontext
05. [x] TokenInvalidationCheckPreFilter - check if dp_jwt cookie has already been invalidated
06. [x] KnoxAuthAndSyncPreFilter - check knox cookie and set usercontext
07. [-] KnoxEnsureAuthPreFilter - verify knox token if user is authenticated via a non-db user
08. [x] AuthorizationPreFilter - send 401 if usercontext is unavailable
09. [x] ContextForwardingPreFilter - read and set knox token and usercontext in upstream header 
10. [x] TokenInvalidationPostFilter - blacklist a token returned from dp-app
11. [x] SetCookiePostFilter - set cookie if not already available
12. [x] TraceFilter - do trace
13. [x] SendGatewayErrorFilter - return friendly errors for GatewayException

## Tests
01. Setup Knox and nginx
02. Check login
03. Check logout
04. /opi/random > 403 > with bearer token | dp cookie | knox user cookie | knox group cookie
05. /api/core/api/identity > with bearer token | dp cookie | knox user cookie | knox group cookie
06. check exception filter > with db | without db | with 401 -> check header
07. change password
08. check if cookie was set if unavailable
09. check if cookie is same if token was available
10. check if cookie was set if only knox cookie was available

