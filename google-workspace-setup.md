# Google Workspace setup instructions

These instructions are applicable to configure Google Workspace as the IDP for XWiki
using this plugin.  

The following configurations are required in the xwiki.cfg file.

## Required properties

```sh
xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator

xwiki.authentication.saml2.idp.single_sign_on_service.url=https://accounts.google.com/o/saml2/idp?idpid=<Copy from google>
xwiki.authentication.saml2.idp.entityid=https://accounts.google.com/o/saml2?idpid=<Copy from google>
xwiki.authentication.saml2.sp.entityid=<any arbitrary string - you must use this when google asks>
xwiki.authentication.saml2.idp.x509cert=the certificate to validate\
requests. Use backslash\
for line breaks

xwiki.authentication.saml2.sp.assertion_consumer_service.url=https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin
```

## Google Workspace set up instructions

When setting up with Google Workspace SAML, follow these instructions:

0. Optional step:

If you want, you can create a custom field for your users names XWikiGroups, single value, text. 
This can be used to specify the user groups.

1. Create a SAML Custom App

2. In the second page download the metadata file (Identity Provider Metada XML). The format will look like the following:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="https://accounts.google.com/o/saml2?idpid=<IDPID>" validUntil="...">
  <md:IDPSSODescriptor WantAuthnRequestsSigned="false" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
    <md:KeyDescriptor use="signing">
      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:X509Data>
          <ds:X509Certificate>Copy this value to property xwiki.authentication.saml2.idp.x509cert</ds:X509Certificate>
        </ds:X509Data>
      </ds:KeyInfo>
    </md:KeyDescriptor>
    <md:NameIDFormat>urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress</md:NameIDFormat>
    <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://accounts.google.com/o/saml2/idp?idpid=<IDPID>"/>
    <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://accounts.google.com/o/saml2/idp?idpid=<IDPID>"/>
  </md:IDPSSODescriptor>
</md:EntityDescriptor>
```

The value of Location field of `urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST` is xwiki.authentication.saml2.idp.single_sign_on_service.url.

`entityID` value in the first line should be set on `xwiki.authentication.saml2.idp.entityid`.

2. Next to the second page:

* ACS URL: https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin
* Entity ID: the same value present on xwiki.authentication.saml2.sp.entityid
* Name ID Format: EMAIL
* Name ID Field: Basic Information > Primary email

3. Attribute mapping:

* Primary Email -> email
* First Name -> firstName
* Last Name -> lastName

If you created the custom field XWikiGroups, set up the following attribute mapping:
* XWikiGroups -> XWikiGroups 