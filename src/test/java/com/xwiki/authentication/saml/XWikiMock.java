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

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.objects.meta.StringMetaClass;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.syntax.Syntax;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class XWikiMock extends XWiki {
    public final Map<String, List<BaseObject>> createdObjectsByEntity = new LinkedHashMap<>();
    public final Set<XWikiDocument> savedDocuments = new LinkedHashSet<>();
    private final BaseClass newClass;
    private final Map<DocumentReference, XWikiDocument> docByReference = new LinkedHashMap<>();

    public final BaseObject baseObjectMock = mock(BaseObject.class);
    private final Map<String, String> savedUserAttributes = new LinkedHashMap<>();

    public XWikiMock(XWikiContext context) {
        newClass = new BaseClass() {
            public DocumentReference getDocumentReference() {
                return new DocumentReference(context.getWikiId(), "XWiki:", "XWikiGroups.StarshipTroopers");
            }

            @SuppressWarnings("rawtypes")
            @Override
            public Collection getFieldList() {
                final Map<String, Object> fields = new LinkedHashMap<>();
                final StringMetaClass metaClass = new StringMetaClass();
                final StringClass prop = new StringClass("member", "XWikiGroups", metaClass);
                fields.put("member", prop);

                return fields.values();
            }
        };

        doAnswer(invocation -> {
            savedUserAttributes.put(invocation.getArgument(0).toString(), invocation.getArgument(1).toString());
            return null;
        }).when(baseObjectMock).set(any(), any(), any());
    }

    int createUserResult = 0;
    @Override
    public int createUser(String userName,
                          Map<String, ?> map,
                          EntityReference parentReference,
                          String content,
                          Syntax syntax,
                          String userRights,
                          XWikiContext context) {
        map.forEach((key, value) -> this.savedUserAttributes.put(key + "", value + ""));
        return createUserResult;
    }

    public void makeCreateUserReturnError(){
        createUserResult = -3;
    }

    public String getSaveAttributeValue(String attributeName) {
        return savedUserAttributes.get(attributeName);
    }

    public void hasSaved(String document) {
        assertTrue(this.savedDocuments.stream().map(XWikiDocument::toString).anyMatch(s -> s.equals(document)));
    }

    public XWikiDocument getDocument(DocumentReference reference, XWikiContext context) {
        docByReference.putIfAbsent(reference, createDocumentForReference(reference));
        return docByReference.get(reference);
    }

    private XWikiDocument createDocumentForReference(DocumentReference reference) {
        return new XWikiDocument(new DocumentReference(reference, (Locale) null), reference.getLocale()) {
            @Override
            public BaseObject newXObject(EntityReference classReference, XWikiContext context) {
                createdObjectsByEntity.putIfAbsent(classReference.toString(), new ArrayList<>());
                final BaseObject obj = new BaseObject() {
                    public DocumentReference getXClassReference() {
                        return (DocumentReference) classReference;
                    }
                };
                createdObjectsByEntity.get(classReference.toString()).add(obj);
                return obj;
            }

            public List<BaseObject> getXObjects(DocumentReference classReference) {
                createdObjectsByEntity.putIfAbsent(classReference.toString(), new ArrayList<>());

                return createdObjectsByEntity.get(classReference.toString());
            }

            public BaseObject getXObject(DocumentReference classReference, String key, String value) {
                createdObjectsByEntity.putIfAbsent(classReference.toString(), new ArrayList<>());

                return createdObjectsByEntity.get(classReference.toString()).stream().filter(obj ->
                        ((StringProperty) obj.safeget(key)).getValue().equals(value)
                ).findFirst().orElse(null);
            }

            @Override
            public boolean removeXObject(BaseObject object) {
                createdObjectsByEntity.get(object.getXClassReference().toString()).remove(object);
                return true;
            }

            public BaseObject getXObject(EntityReference reference) {
                return baseObjectMock;
            }
        };
    }

    public void saveDocument(XWikiDocument doc, XWikiContext context) {
        savedDocuments.add(doc);
    }

    public Set<XWikiDocument> getSavedDocuments() {
        return savedDocuments;
    }

    public BaseClass getGroupClass(XWikiContext context) {
        return newClass;
    }
}
