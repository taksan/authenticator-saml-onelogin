#!/bin/bash


curl -s http://localhost:8090/auth/realms/MyNewRealm/protocol/saml/descriptor > metadata.xml

keycloakSpEntityID="xwiki"
xwikiUrlLogin="http://localhost:8080/bin/loginsubmit/XWiki/XWikiLogin"
keycloakIdpEntityID=$(cat metadata.xml | sed 's/.* entityID="\([^"]*\)".*/\1/')
keycloakSingleSignOnService=$(cat metadata.xml | sed -E 's/.*SingleSignOnService.+Location="([^"]+)".*/\1/')
keycloakCertificate=$(cat metadata.xml | sed -E 's/.*<ds:X509Certificate>([^<]*).*/\1/') 


docker-compose exec web bash -c """
echo 'extension.repositories=maven-xwiki:maven:https://nexus.xwiki.org/nexus/content/groups/public/
extension.repositories=extensions.xwiki.org:xwiki:https://extensions.xwiki.org/xwiki/rest/
extension.repositories=local-xwiki:maven:http://xwiki-nexusx:8081/repository/maven-snapshots
' >> /usr/local/xwiki/data/xwiki.properties
"""


docker-compose exec web bash -c """
echo 'xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator
xwiki.authentication.saml2.idp.single_sign_on_service.url=$keycloakSingleSignOnService
xwiki.authentication.saml2.idp.entityid=$keycloakIdpEntityID
xwiki.authentication.saml2.sp.entityid=$keycloakSpEntityID
xwiki.authentication.saml2.idp.x509cert=$keycloakCertificate
xwiki.authentication.saml2.sp.assertion_consumer_service.url=$xwikiUrlLogin
' >> /usr/local/xwiki/data/xwiki.cfg
"""

docker-compose restart web