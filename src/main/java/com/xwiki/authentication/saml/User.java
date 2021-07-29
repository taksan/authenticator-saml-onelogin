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
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.classes.StringClass;
import java.util.HashMap;
import java.util.Map;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_USER_CREATE;
import static com.xpn.xwiki.XWikiException.MODULE_XWIKI_PLUGINS;
import static com.xwiki.authentication.saml.SamlAuthenticationResponseHandler.*;
import static com.xwiki.authentication.saml.SamlAuthenticator.PROFILE_PARENT;

public class User {
    private final String nameID;
    private final DocumentReference userReference;
    private final XWikiContext context;
    private final XWikiDocument userDoc;
    private final BaseObject userObj;

    public User(String nameID, DocumentReference userReferenceForName, XWikiContext context) throws XWikiException {
        this.nameID = nameID;
        this.userReference = userReferenceForName;
        this.context = context;
        this.userDoc = context.getWiki().getDocument(userReference, context);
        this.userObj = userDoc.getXObject(USER_XCLASS);
    }

    public boolean exists() {
        return context.getWiki().exists(userReference, context);
    }

    public DocumentReference getUserReference(){
        return userReference;
    }

    public String getFieldValue(String field) throws XWikiException {
        return getUserProperty(userObj, field);
    }

    public void setFieldValue(String field, String newValue) {
        userObj.set(field, newValue, context);
    }

    private String getUserProperty(BaseObject userObj, String field) throws XWikiException {
        final BaseProperty<?> prop = (BaseProperty<?>) userObj.get(field);
        return (prop == null || prop.getValue() == null) ? null : prop.getValue().toString();
    }

    public void save() throws XWikiException {
        context.getWiki().saveDocument(userDoc, context);
    }

    public void createUserWithAttributes(Map<String, String> xwikiAttributes)
            throws XWikiException {
        final Map<String, String> newXwikiAttributes = new HashMap<>(xwikiAttributes);
        newXwikiAttributes.put("active", "1");

        final String content = "{{include document=\"XWiki.XWikiUserSheet\"/}}";

        final int result = context.getWiki().createUser(
                userReference.getName(),
                newXwikiAttributes,
                PROFILE_PARENT,
                content,
                Syntax.XWIKI_2_1,
                "edit",
                context);

        if (result < 0)
            throw new XWikiException(
                    MODULE_XWIKI_PLUGINS,
                    ERROR_XWIKI_USER_CREATE,
                    "XWiki failed to create user [" + nameID + "]. Error code [" + result + "]");

        associateSamlUserWithXwikiUser();
    }

    private void associateSamlUserWithXwikiUser() throws XWikiException {
        final BaseObject samlIdObject = userDoc.newXObject(SAML_XCLASS, context);
        @SuppressWarnings("rawtypes")
        final BaseProperty samlIdProp = new StringClass().fromString(nameID);
        samlIdProp.setOwnerDocument(samlIdObject.getOwnerDocument());
        samlIdObject.safeput(XWikiUserManager.SAML_ID_XPROPERTY_NAME, samlIdProp);

        save();
    }
}
