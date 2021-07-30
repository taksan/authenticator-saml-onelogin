#!/bin/bash

set -e -o nounset

curl -sS http://localhost:8090/auth/realms/MyNewRealm/protocol/saml/descriptor > metadata.xml

key_cloak_sp_entity_id="xwiki"
xwiki_url_login="http://localhost:8080/bin/loginsubmit/XWiki/XWikiLogin"
key_cloak_idp_entity_id=$(sed 's/.* entityID="\([^"]*\)".*/\1/' metadata.xml)
key_cloak_single_sign_on_service=$(sed -E 's/.*SingleSignOnService.+Location="([^"]+)".*/\1/' metadata.xml)
key_cloak_certificate=$(sed -E 's/.*<ds:X509Certificate>([^<]*).*/\1/' metadata.xml)


docker-compose exec xwiki bash -c """
sed -i '/####CUSTOM_SETUP_START/,/####CUSTOM_SETUP_END/ d' /usr/local/xwiki/data/xwiki.properties
echo '
####CUSTOM_SETUP_START

extension.repositories=maven-xwiki:maven:https://nexus.xwiki.org/nexus/content/groups/public/
extension.repositories=extensions.xwiki.org:xwiki:https://extensions.xwiki.org/xwiki/rest/
extension.repositories=local-xwiki:maven:http://xwiki-nexus:8081/repository/maven-snapshots

####CUSTOM_SETUP_END
' >> /usr/local/xwiki/data/xwiki.properties

sed -i '/####CUSTOM_SETUP_START/,/####CUSTOM_SETUP_END/ d' /usr/local/xwiki/data/xwiki.cfg
echo '
####CUSTOM_SETUP_START

xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator
xwiki.authentication.saml2.idp.single_sign_on_service.url=$key_cloak_single_sign_on_service
xwiki.authentication.saml2.idp.entityid=$key_cloak_idp_entity_id
xwiki.authentication.saml2.sp.entityid=$key_cloak_sp_entity_id
xwiki.authentication.saml2.idp.x509cert=$key_cloak_certificate
xwiki.authentication.saml2.sp.assertion_consumer_service.url=$xwiki_url_login

####CUSTOM_SETUP_END
' >> /usr/local/xwiki/data/xwiki.cfg
"""

docker-compose restart xwiki
