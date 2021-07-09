package com.xwiki.authentication.saml;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;

import javax.servlet.http.HttpSession;

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

}