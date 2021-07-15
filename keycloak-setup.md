# It has been tested with KeyCloak SAML authentication.

## Step 1) Create your realm
+ [Follow the instructions in KeyCloak documentation.](https://www.keycloak.org/docs/latest/getting_started)


![keycloak realm settings](images/keycloak_realm_settings.png)
+ After create the new realm, you should get the ```Identity Provider Metada``` XML by clicking on the region pointed out above. 

```xml
<!--Identity Provider Metada XML Example:-->

<!-- "xwiki.authentication.saml2.idp.entityid" will come from the following attribute `entityID` -->
<EntityDescriptor xmlns="urn:oasis:names:tc:SAML:2.0:metadata" xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" entityID="http://<keycloak-url>/auth/realms/<realm-name>">
	<IDPSSODescriptor WantAuthnRequestsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
		<KeyDescriptor use="signing">
			<KeyInfo>
				<KeyName>
					...QmYWODphcDVdYY6pMed...
				</KeyName>
				<X509Data>
          <!-- "xwiki.authentication.saml2.idp.x509cert" will come from the following attribute -->
					<X509Certificate>
						...MIICoTCCAYkCBgF6m...
					</X509Certificate>
				</X509Data>
			</KeyInfo>
		</KeyDescriptor>
		<ArtifactResolutionService Binding="urn:oasis:names:tc:SAML:2.0:bindings:SOAP" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml/resolve" index="0" />
		<SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
		<SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
		<SingleLogoutService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Artifact" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
		<NameIDFormat>
			urn:oasis:names:tc:SAML:2.0:nameid-format:persistent
		</NameIDFormat>
		<NameIDFormat>
			urn:oasis:names:tc:SAML:2.0:nameid-format:transient
		</NameIDFormat>
		<NameIDFormat>
			urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified
		</NameIDFormat>
		<NameIDFormat>
			urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress
		</NameIDFormat>
    <!-- "xwiki.authentication.saml2.idp.single_sign_on_service.url" will come from the following attribute -->
		<SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
		<SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
		<SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:SOAP" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
		<SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Artifact" Location="<keycloak-url>/auth/realms/<realm-name>/protocol/saml" />
	</IDPSSODescriptor>
</EntityDescriptor>
```

---
## Step 2) Create Client for SAML Authentication

![keycloak client ](images/keycloak_clients.png)
+ The ```Client ID``` will be referenced in the xWiki attribute settings under ```sp.entityid``` in ```xwiki.cfg```. 
+ You must be sure to choose ```SAML``` in the ```Client protocol```.
---
## Step 3) Client Settings
+ ```Client Signature Required``` should be unchecked.
+ ```Name ID Format``` the option selected should be email.
+ ```Valid Redirect URIs``` should be ```https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin```
+ ```Master SAML Processing URL``` should be ```https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin```

![Client_Setup1](images/keycloak_clients_setup1.png)
![Client_Setup2](images/keycloak_clients_setup2.png)

---
## Step 4) Client Mappers

+ Click over the "Add Builtin" button.
+ Check all fields and click over the "Add sleected" button.

![keycloak_clients_mapper1](images/keycloak_clients_mapper1.png)
![keycloak_clients_mapper2](images/keycloak_clients_mapper2.png)
![keycloak_clients_mapper3](images/keycloak_clients_mapper3.png)

+ Updating mappers.

![keycloak_clients_mapper_givenname](images/keycloak_clients_mapper_givenname.png)
![keycloak_clients_mapper_surname](images/keycloak_clients_mapper_surname.png)
![keycloak_clients_mapper_email](images/keycloak_clients_mapper_email.png)
![keycloak_clients_mapper_role](images/keycloak_clients_mapper_role.png)

+ Add a new group settings.

![keycloak_clients_mapper_group](images/keycloak_clients_mapper_group.png)

+ Create a new user.

![keycloak_user](images/keycloak_user.png)

+ Add password to user

![keycloak_user_password](images/keycloak_user_password.png)
---
## Step 5) XWiki attributes
+ The following configurations are required in the xwiki.cfg file:
```properties
# Required properties for xwiki.cfg file
xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator
xwiki.authentication.saml2.idp.single_sign_on_service.url=<keycloak-url>/auth/realms/<realm-name>/protocol/saml
xwiki.authentication.saml2.idp.entityid=http://<keycloak-url>/auth/realms/<realm-name>
xwiki.authentication.saml2.sp.entityid=<any arbitrary string - you must use the same in the client setting `Client ID` >
xwiki.authentication.saml2.idp.x509cert=<certificate data come from Identity Provider Metada xml>
xwiki.authentication.saml2.sp.assertion_consumer_service.url=https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin
```
