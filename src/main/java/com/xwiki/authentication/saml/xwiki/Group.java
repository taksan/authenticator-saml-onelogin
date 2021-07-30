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
package com.xwiki.authentication.saml.xwiki;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.syntax.Syntax;
import static java.util.Collections.singletonMap;

public class Group {
    private static final String XWIKI_GROUP_MEMBERFIELD = "member";
    private static final EntityReference GROUP_PARENT = new EntityReference("XWikiGroups", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));
    private final XWikiDocument groupDoc;
    private final XWikiContext context;
    private final DocumentReferenceResolver<String> groupResolver;

    public Group(String groupName, XWikiContext context, DocumentReferenceResolver<String> groupResolver) throws XWikiException {
        this.groupResolver = groupResolver;
        this.groupDoc = context.getWiki().getDocument(getGroupReferenceForName(groupName), context);
        this.context = context;
    }

    public boolean hasMember(String xwikiUserName) throws XWikiException {
        return getGroupMembers(groupDoc).stream()
                .anyMatch(groupMember -> Objects.equals(getMemberName(groupMember), xwikiUserName));
    }

    private String getMemberName(BaseObject groupMember) {
        return groupMember.getStringValue(XWIKI_GROUP_MEMBERFIELD);
    }

    private List<BaseObject> getGroupMembers(XWikiDocument groupDoc) throws XWikiException {
        final List<BaseObject> groupMembers = groupDoc.getXObjects(getGroupClass().getDocumentReference());
        if (groupMembers == null)
            return Collections.emptyList();

        return groupMembers.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    public String groupName() {
        return groupDoc.getDocumentReference().getName();
    }

    public void addMember(String xwikiUserName) throws XWikiException {
        addMemberToGroup(xwikiUserName);
    }

    private void addMemberToGroup(String xwikiUserName) throws XWikiException {
        BaseObject memberObj = groupDoc.newXObject(getGroupClass().getDocumentReference(), context);
        getGroupClass().fromMap(singletonMap(XWIKI_GROUP_MEMBERFIELD, xwikiUserName), memberObj);
    }

    private BaseClass getGroupClass() throws XWikiException {
        return context.getWiki().getGroupClass(context);
    }

    public boolean isNew() {
        return groupDoc.isNew();
    }

    void setupNewGroupDocument() {
        groupDoc.setSyntax(Syntax.XWIKI_2_0);
        groupDoc.setContent("{{include reference='XWiki.XWikiGroupSheet' /}}");
    }

    public void save() throws XWikiException {
        context.getWiki().saveDocument(groupDoc, context);
    }

    public void removeUser(String xwikiUserName) throws XWikiException {
        getUserMembership(xwikiUserName).map(groupDoc::removeXObject);
    }

    private Optional<BaseObject> getUserMembership(String xwikiUserName) throws XWikiException {
        return Optional.ofNullable(groupDoc.getXObject(getGroupClass().getDocumentReference(), XWIKI_GROUP_MEMBERFIELD, xwikiUserName));
    }

    private DocumentReference getGroupReferenceForName(String validGroupName) {
        return groupResolver.resolve(validGroupName, GROUP_PARENT);
    }
}
