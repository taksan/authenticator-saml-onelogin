# SAML 2.0 Authenticator 

This module has been developed to provide a working SAML 2.0 Authenticator and is mostly based on
[https://github.com/xwiki-contrib/authenticator-saml](https://github.com/xwiki-contrib/authenticator-saml), but replaces OpenSAML by OneLogin 
[https://github.com/onelogin/java-saml](https://github.com/onelogin/java-saml) for the SAML authentication.

## TL;DR

Follow these instructions to setup with these Identity Providers:

[Google Workspace](google-workspace-setup)

[KeyCloak](keycloak-setup)

If you setup this plugin with other IDPs, please feel free to contribute with PR's
with your instructions.

## Dev Setup Quick Start

[Quick Start](dev-quick-start)

# Plugin Configurations on xwiki.cfg

## Required properties

xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator

### xwiki.authentication.saml2.idp.single_sign_on_service.url

IDP stands for Identity Provider, and is the service that provides the authentication, for example, Google Workspace, KeyCloak, and others like it.

The single_sign_on_service is used to tell what is the IDP URL where authentication happens. Usually, you can get this information from metadata.xml. Choose the "POST" URL.

### xwiki.authentication.saml2.idp.entityid

The IDP entity id is a unique string that identifies the IDP and is used by the
authenticator to validate the IDP. You usually get this information from a metadata.xml file.

### xwiki.authentication.saml2.sp.entityid

SP stands for "Service Provider", in this case the XWiki instance. The SP entity id
is a unique string (it can be any arbitrary value chosen by you) that identifies
your XWiki instance. Some people recommend to use the application URL.

Usually, you will configure this ID somewhere in the IDP, so it can tell whether authorization requests are coming from a valid application and to choose the correct configuration.

### xwiki.authentication.saml2.idp.x509cert

This is the IDP certificate. This is used to validate the identity of the IDP provder
to ensure it is legitimate.

### xwiki.authentication.saml2.sp.assertion_consumer_service.url

This is XWiki's URL that accepts the post to process the authentication coming from IDP. It will always be like this:

    https://<you wiki domain>/bin/loginsubmit/XWiki/XWikiLogin

## Optional properties

### Mapping of XWiki user fields and identity provider
xwiki.authentication.saml2.fields_mapping=email=email,first_name=firstName,last_name=lastName

### Default group for new users
xwiki.authentication.saml2.defaultGroupForNewUsers=XWiki.SamlGroup

### The name of the attribute used to cache the authentication result in the current session; optional
xwiki.authentication.saml2.auth_field=saml_user

### List of fields to use for generating an XWiki username
xwiki.authentication.saml2.xwiki_user_rule=first_name,last_name

### Capitalize each field value when generating the username
xwiki.authentication.saml2.xwiki_user_rule_capitalize=true

### NameIDFormat format; recommend leaving the default
xwiki.authentication.saml2.sp.nameidformat=urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress
