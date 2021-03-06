# Google Workspace setup instructions

These instructions will guide you while configuring Google Workspace as the IDP for XWiki.

The following configurations are required in the `xwiki.cfg` file.

## Required properties

```sh
xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator

xwiki.authentication.saml2.idp.single_sign_on_service.url=https://accounts.google.com/o/saml2/idp?idpid=<Copy from google>
xwiki.authentication.saml2.idp.entityid=https://accounts.google.com/o/saml2?idpid=<Copy from google>
xwiki.authentication.saml2.sp.entityid=<any arbitrary string - you must use this when google asks>
xwiki.authentication.saml2.idp.x509cert=the certificate to validate\
requests. Use backslash\
for line breaks

xwiki.authentication.saml2.sp.assertion_consumer_service.url=https://<your wiki domain>/bin/loginsubmit/XWiki/XWikiLogin
```

## Google Workspace set up instructions

When setting up with Google Workspace SAML, follow these instructions:

0. Optional step:

If you want, you can create a custom field for your users names XWikiGroups, single value, text. 
This can be used to specify the user groups.

![google_user_customattributes](images/google_user_customattributes.png)
![google_user_customfield](images/google_user_customfield.png)

1. Create a SAML Custom App
    + From the Admin console Home page, go to Apps and then Web and mobile apps.
    + Click Add App and then Add custom SAML app.

![google_saml_app_add](images/google_saml_app_add.png)
![google_saml_app](images/google_saml_app.png)

2. In the second page download the metadata file (Identity Provider Metada XML). 
   
![google_app_metadata.png](images/google_app_metadata.png)

+ The format will look like the following:

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

Look for the `<md:SingleSignOnService>` XML Tag with the `Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"` attribute. The value of its `Location` attribute will be used on the `xwiki.authentication.saml2.idp.single_sign_on_service.url` property.

Simiarly, look for the `<md:EntityDescriptor>` XML Tag at the beginning of the file. The value of its `entityID` attribute will be used on the `xwiki.authentication.saml2.idp.entityid` property.

3. Next, on the "Service provider details" page:

+ ACS URL: `https://<your wiki domain>/bin/loginsubmit/XWiki/XWikiLogin`
+ Entity ID: the same value present on the `xwiki.authentication.saml2.sp.entityid` property.
+ Name ID Format: EMAIL
+ Name ID Field: Basic Information > Primary email

![google_app_service_provider](images/google_app_service_provider.png)
![google_app_service_provider_continue](images/google_app_service_provider_continue.png)

4. Attribute mapping:
```
 Primary Email -> email
 First Name -> firstName
 Last Name -> lastName
```
If you created the custom field XWikiGroups, set up the following attribute mapping, replacing the name with whichever name you used:
`XWikiGroups -> XWikiGroups` 

![google_app_mapping](images/google_app_mapping.png)

+ This is how your overview page should look like:

![google_app_overview](images/google_app_overview.png)
