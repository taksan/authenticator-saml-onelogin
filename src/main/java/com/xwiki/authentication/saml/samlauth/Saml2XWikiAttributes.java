package com.xwiki.authentication.saml.samlauth;

import java.util.Map;
import java.util.Set;

public class Saml2XWikiAttributes {
    public final String nameID;
    public final Map<String, String> xwikiAttributes;
    public final Set<String> groupsFromSaml;

    Saml2XWikiAttributes(String nameID, Map<String, String> xwikiAttributes, Set<String> groupsFromSaml) {
        this.nameID = nameID;
        this.xwikiAttributes = xwikiAttributes;
        this.groupsFromSaml = groupsFromSaml;
    }
}
