package com.xwiki.authentication.saml;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.objects.meta.StringMetaClass;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;

import javax.servlet.http.HttpSession;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class XWikiGroupManagerTest {
    @Test
    public void addUserToGroup_ShouldAddUserToGivenGroup() {
        given()
            .user("ArthurDent")
            .fromGroup("StarshipTroopers")
        .whenAddedToGroup()
        .then()
            .user("ArthurDent")
            .isInGroup("StarshipTroopers");
    }

    @Test
    public void removeUserFromGroup_ShouldRemoveUserFromGivenGroup() {
        given()
            .user("ArthurDent")
            .fromGroup("StarshipTroopers")
            .userIsAlreadyInGroup("ArthurDent","StarshipTroopers")
        .whenRemoveFromGroup()
        .then()
            .user("ArthurDent")
            .isntInGroup("StarshipTroopers");
    }

    @Test
    public void userAlreadyMemberOfGroup_ShouldIgnoreTheNewMembership() {
        given()
            .user("ArthurDent")
            .fromGroup("StarshipTroopers")
            .userIsAlreadyInGroup("ArthurDent","StarshipTroopers")
        .whenAddedToGroup()
        .then()
            .user("ArthurDent")
            .isInGroup("StarshipTroopers");
    }

    @Test
    public void removeUserFromGroupWhereHeIsnt_ShouldNotChangeGroupMembership() {
        given()
            .user("ArthurDent")
            .fromGroup("StarshipTroopers")
        .whenRemoveFromGroup()
        .then()
            .user("ArthurDent")
            .isntInGroup("StarshipTroopers");
    }

    private DSL given(){
        return new DSL();
    }

    private static class DSL {
        private final XWikiContext context = new XWikiContext();
        private XWikiGroupManager groupManager;

        private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver =
                (docRef, parameters) -> new DocumentReference("XWiki", "XWikiGroups", docRef);

        private String userName;
        private String groupName;

        final XWikiMock xwiki;

        public DSL() {
            context.setWikiId("XWIKI");
            context.setMainXWiki("MAIN-XWIKI");
            context.setAction("");
            context.setResponse(mock(XWikiResponse.class));
            XWikiRequest request = mock(XWikiRequest.class);
            HttpSession httpSession = mock(HttpSession.class);
            when(request.getSession(true)).thenReturn(httpSession);
            when(request.getSession()).thenReturn(httpSession);

            context.setRequest(request);
            xwiki = new XWikiMock(context);
            context.setWiki(xwiki);
        }

        public DSL user(String userName){
            this.userName = userName;
            return this;
        }
        public DSL fromGroup(String groupName){
            this.groupName = groupName;
            return this;
        }
        public ThenDSL whenAddedToGroup(){
            groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            groupManager.addUserToXWikiGroup(userName,groupName,context);
            return new ThenDSL();
        }
        public ThenDSL whenRemoveFromGroup(){
            groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            groupManager.removeUserFromXWikiGroup(userName,groupName,context);
            return new ThenDSL();
        }

        public DSL userIsAlreadyInGroup(String userName, String groupName) {
            groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            groupManager.addUserToXWikiGroup(userName,groupName,context);
            return this;
        }

        public class ThenDSL {
            String userName;

            public ThenDSL then() {
                return this;
            }

            public ThenDSL user(String userName) {
                this.userName = userName;
                return this;
            }

            public void isInGroup(String groupName) {
                String fullGroupName = "XWikiGroups."+groupName;
                XWikiDocument groupDoc =
                        xwiki.getSavedDocuments().stream().filter(doc -> doc.toString().equals(fullGroupName)).findFirst()
                                .orElseGet(() -> Assertions.fail("Group " + groupName + " doesn't exist"));

                BaseClass ref = xwiki.getGroupClass(context);
                BaseObject obj = groupDoc.getXObject(ref.getDocumentReference(), "member", this.userName);
                assertNotNull(obj, "User is not in the group");
            }

            public void isntInGroup(String groupName) {
                String fullGroupName = "XWikiGroups."+groupName;
                XWikiDocument groupDoc = xwiki.getSavedDocuments().stream().filter(doc -> doc.toString().equals(fullGroupName)).findFirst()
                                                     .orElseGet(() -> Assertions.fail("Group " + groupName + " doesn't exist"));

                BaseClass ref = xwiki.getGroupClass(context);
                BaseObject obj =  groupDoc.getXObject(ref.getDocumentReference(), "member", this.userName);
                assertNull(obj);
            }
        }
    }

    private static class  XWikiMock extends XWiki {
        final Map<String, List<BaseObject>> createdObjectsByEntity = new LinkedHashMap<>();
        public Set<XWikiDocument> savedDocuments = new HashSet<>();
        private final BaseClass newClass;
        private final Map<DocumentReference, XWikiDocument> docByReference = new HashMap<>();
        public XWikiMock(XWikiContext context) {
            newClass = new BaseClass() {
                public DocumentReference getDocumentReference(){
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
                        ((StringProperty)obj.safeget(key)).getValue().equals(value)
                    ).findFirst().orElse(null);
                }

                @Override
                public boolean removeXObject(BaseObject object) {
                    createdObjectsByEntity.get(object.getXClassReference().toString()).remove(object);
                    return true;
                }
            };
        }

        public void saveDocument(XWikiDocument doc, XWikiContext context) {
            savedDocuments.add(doc);
        }
        public Set<XWikiDocument> getSavedDocuments(){
            return savedDocuments;
        }

        public BaseClass getGroupClass(XWikiContext context) {
            return newClass;
        }
    }

}