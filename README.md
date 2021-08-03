# SAML 2.0 Authenticator 

This module has been developed to provide a working SAML 2.0 Authenticator and is mostly based on
[xwiki-contrib/authenticator-saml](https://github.com/xwiki-contrib/authenticator-saml), 
but replaces OpenSAML (which has reached its End of Life) by [OneLogin](https://github.com/onelogin/java-saml) 

## TL;DR

This module has been tested with the follow Identity Providers. Follow the instructions below
for assistance for proper configuration.

* [Google Workspace](google-workspace-setup.md)
* [KeyCloak](keycloak-setup.md)

If you set up this plugin with other IDPs, please feel free to contribute by opening PRs.
with your instructions.

## Dev Setup Quick Start

If you want to modify the source code or help develop the plugin, go to our [Dev Quick Start](quick-start/README.md) section and follow the instructions to set up a complete local environment. 

# Plugin configuration reference

Once you install the plugin in your XWiki instance, you will need to set up a couple of settings in your `xwiki.cfg` file to make it work properly.

## Required properties

* `xwiki.authentication.authclass=com.xwiki.authentication.saml.XWikiSAML20Authenticator`

    Default plugin class.
* `xwiki.authentication.saml2.idp.single_sign_on_service.url`

    This setting should point o the IDP authentication URL. Usually, you can get this information from a file named similarly to `metadata.xml`. Choose the "POST" URL.
* `xwiki.authentication.saml2.idp.entityid`

    The IDP entity ID is a unique string that identifies the IDP and is used by the authenticator to validate the IDP. You usually get this information from the aforementioned `metadata.xml` file.
* `xwiki.authentication.saml2.sp.entityid`

    SP stands for "Service Provider", in this case the XWiki instance. The SP entity ID is a unique string (it can be any arbitrary value chosen by you) that identifies your XWiki instance. Some people recommend using the application URL.

    Usually, you will configure this ID somewhere in the IDP, so it can tell whether authorization requests are coming from a valid application and to choose the correct configuration.
* `xwiki.authentication.saml2.idp.x509cert`

    This is used to validate the identity of the IDP provider to ensure it is legitimate. You usually get this information from the aforementioned `metadata.xml` file.
* `xwiki.authentication.saml2.sp.assertion_consumer_service.url`

    This is XWiki's URL that accepts the POST request to process the authentication coming from IDP. It will always be like this:`https://<your wiki domain>/bin/loginsubmit/XWiki/XWikiLogin`

---
## Properties with default values

The following properties already have default values that will work for most cases and don't need
to be changed, but can be customized in case you have very specific needs.

* `xwiki.authentication.saml2.fields_mapping=email=email,first_name=firstName,last_name=lastName`

    Map fields provided by the IDP with XWiki fields. The format is like this:
    `<xwiki field name>=<idp field name>`

    You can map multiple fields separating each mapping by comma.

    The default value means:

    + IDP's `email` field will be mapped to Xwiki's `email`
    + IDP's `firstName` field will be mapped to Xwiki's `first_name`
    + IDP's `lastName` field will be mapped to Xwiki's `last_name`

    The above fields are the minimum required fields.

* `xwiki.authentication.saml2.defaultGroupForNewUsers=XWiki.SamlGroup`

    You can set up the default group for users coming from the IDP. 

* `xwiki.authentication.saml2.auth_field=saml_user`

    You usually don't need to bother with this configuration. It is used to save a custom field
    in the XWiki's users, mapping the IDP's user identity into it. Once you start with a value,
    we don't recommend changing it. 

* `xwiki.authentication.saml2.xwiki_user_rule=first_name,last_name`

    This property controls how the plugin will generate the username in XWiki, based on the user's
    field values.

* `xwiki.authentication.saml2.xwiki_user_rule_capitalize=true`

    Controls whether username generation should capitalize each of the  xwiki_user_rule's attributes composition.

* `xwiki.authentication.saml2.sp.nameidformat=urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress`

    This field determines which IDP's field will be used to identify the user in XWiki. When a user authenticates, 
    this value will be stored in the field specified by `xwiki.authentication.saml2.auth_field` property.
