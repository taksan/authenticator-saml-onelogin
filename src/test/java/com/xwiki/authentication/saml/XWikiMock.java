package com.xwiki.authentication.saml;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.objects.meta.StringMetaClass;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rendering.syntax.Syntax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class XWikiMock extends XWiki {
    final Map<String, List<BaseObject>> createdObjectsByEntity = new LinkedHashMap<>();
    public Set<XWikiDocument> savedDocuments = new HashSet<>();
    private final BaseClass newClass;
    private final Map<DocumentReference, XWikiDocument> docByReference = new HashMap<>();

    final BaseObject baseObjectMock = mock(BaseObject.class);
    private final Map<String, String> savedUserAttributes = new LinkedHashMap<>();

    public XWikiMock(XWikiContext context) {
        newClass = new BaseClass() {
            public DocumentReference getDocumentReference() {
                return new DocumentReference(context.getWikiId(), "XWiki:", "XWikiGroups.StarshipTroopers");
            }

            @SuppressWarnings("rawtypes")
            @Override
            public Collection getFieldList() {
                Map<String, Object> fields = new LinkedHashMap<>();

                StringMetaClass metaClass = new StringMetaClass();
                StringClass prop = new StringClass("member", "XWikiGroups", metaClass);
                fields.put("member", prop);

                return fields.values();
            }
        };

        doAnswer(invocation -> {
            savedUserAttributes.put(invocation.getArguments()[0].toString(), invocation.getArguments()[1].toString());
            return null;
        }).when(baseObjectMock).set(any(), any(), any());
    }

    @Override
    public int createUser(String userName,
                          Map<String, ?> map,
                          EntityReference parentReference,
                          String content,
                          Syntax syntax,
                          String userRights,
                          XWikiContext context) {
        map.forEach((key, value) -> this.savedUserAttributes.put(key + "", value + ""));
        return 0;
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
                BaseObject obj = new BaseObject() {
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
