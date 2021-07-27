package com.xwiki.authentication.saml;

import java.util.Map;
import java.util.Set;

class Saml2XwikiAttributes {
    public final String nameID;
    public final Map<String, String> xwikiAttributes;
    public final Set<String> groupsFromSaml;

    Saml2XwikiAttributes(String nameID, Map<String, String> xwikiAttributes, Set<String> groupsFromSaml) {
        this.nameID = nameID;
        this.xwikiAttributes = xwikiAttributes;
        this.groupsFromSaml = groupsFromSaml;
    }
}
