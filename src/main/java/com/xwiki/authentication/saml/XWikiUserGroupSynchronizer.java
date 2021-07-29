/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.authentication.saml;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.StringClass;
import java.util.Optional;
import java.util.Set;
import org.xwiki.model.reference.DocumentReference;
import static com.xwiki.authentication.saml.SamlAuthenticationResponseHandler.PROPERTY_TO_STORE_SAML_MANAGED_GROUPS;
import static com.xwiki.authentication.saml.SamlAuthenticationResponseHandler.USER_XCLASS;
import static org.apache.commons.compress.utils.Sets.newHashSet;

public class XWikiUserGroupSynchronizer {

    private final XWikiGroupManager groupManager;
    private final XWikiContext context;

    public XWikiUserGroupSynchronizer(XWikiGroupManager groupManager, XWikiContext context) {
        this.groupManager = groupManager;
        this.context = context;
    }

    public void syncUserGroups(DocumentReference userReference, Saml2XwikiAttributes attributes) throws XWikiException {
        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final BaseObject userObj = userDoc.getXObject(USER_XCLASS);

        removeUserFromGroupsMissingInSamlGroups(userReference, userObj, attributes);

        addUserToGroupsInSamlGroups(userReference, attributes);

        saveUserWithUpdatedGroups(userDoc, userObj, attributes);
    }

    private void removeUserFromGroupsMissingInSamlGroups(
            DocumentReference userReference,
            BaseObject userObj,
            Saml2XwikiAttributes attributes)
            throws XWikiException {

        final Optional<StringProperty> samlManagedGroupsProp = Optional.ofNullable((StringProperty) userObj.get(PROPERTY_TO_STORE_SAML_MANAGED_GROUPS));
        final Set<String> previousManagedGroups = newHashSet(samlManagedGroupsProp.map(StringProperty::getValue).orElse("").split(","));
        previousManagedGroups.removeAll(attributes.groupsFromSaml);
        for (String group: previousManagedGroups)
            groupManager.removeUserFromGroup(userReference.getName(), group, context);
    }

    private void addUserToGroupsInSamlGroups(DocumentReference userReference, Saml2XwikiAttributes attributes) throws XWikiException {
        for (String group: attributes.groupsFromSaml)
            groupManager.addUserToGroup(userReference.getName(), group, context);
    }

    private void saveUserWithUpdatedGroups(XWikiDocument userDoc, BaseObject userObj, Saml2XwikiAttributes attributes) throws XWikiException {
        userObj.put(PROPERTY_TO_STORE_SAML_MANAGED_GROUPS, new StringClass().fromString(String.join(",", attributes.groupsFromSaml)));
        context.getWiki().saveDocument(userDoc, context);
    }
}
