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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;

import javax.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class XWikiGroupManagerTest {
    @Test
    public void whenAddUserToGroup_ShouldAddUserToGivenGroup() {
        given()
            .userToAdd("ArthurDent")
            .targetGroup("StarshipTroopers")
        .whenAddedToGroup()
        .then()
            .user("ArthurDent")
            .isInGroup("StarshipTroopers");
    }

    @Test
    public void whenRemoveUserFromGroup_ShouldRemoveUserFromGivenGroup() {
        given()
            .userToAdd("ArthurDent")
            .targetGroup("StarshipTroopers")
            .userIsAlreadyInGroup("ArthurDent","StarshipTroopers")
        .whenRemoveFromGroup()
        .then()
            .user("ArthurDent")
            .isntInGroup("StarshipTroopers");
    }

    @Test
    public void whenUserAlreadyMemberOfGroup_ShouldIgnoreTheNewMembership() {
        given()
            .userToAdd("ArthurDent")
            .targetGroup("StarshipTroopers")
            .userIsAlreadyInGroup("ArthurDent","StarshipTroopers")
        .whenAddedToGroup()
        .then()
            .user("ArthurDent")
            .isInGroup("StarshipTroopers");
    }

    @Test
    public void whenRemoveUserFromGroupWhereHeIsnt_ShouldNotChangeGroupMembership() {
        given()
            .userToAdd("ArthurDent")
            .targetGroup("StarshipTroopers")
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
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        public DSL() {
            root.setLevel(Level.OFF);

            XWikiRequest request = mock(XWikiRequest.class);
            HttpSession httpSession = mock(HttpSession.class);
            when(request.getSession(true)).thenReturn(httpSession);
            when(request.getSession()).thenReturn(httpSession);

            context.setWikiId("XWIKI");
            context.setMainXWiki("MAIN-XWIKI");
            context.setAction("");
            context.setResponse(mock(XWikiResponse.class));
            context.setRequest(request);

            xwiki = new XWikiMock(context);
            context.setWiki(xwiki);
        }

        public DSL userToAdd(String userName){
            this.userName = userName;
            return this;
        }
        public DSL targetGroup(String groupName){
            this.groupName = groupName;
            return this;
        }
        public ThenDSL whenAddedToGroup(){
            groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            groupManager.addUserToGroup(userName,groupName,context);
            return new ThenDSL();
        }
        public ThenDSL whenRemoveFromGroup(){
            groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            groupManager.removeUserFromGroup(userName,groupName,context);
            return new ThenDSL();
        }

        public DSL userIsAlreadyInGroup(String userName, String groupName) {
            groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            groupManager.addUserToGroup(userName,groupName,context);
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
                String fullGroupName = "XWikiGroups." + groupName;
                XWikiDocument groupDoc =
                        xwiki.getSavedDocuments().stream().filter(doc -> doc.toString().equals(fullGroupName)).findFirst()
                                .orElseGet(() -> Assertions.fail("Group " + groupName + " doesn't exist"));

                BaseClass ref = xwiki.getGroupClass(context);
                BaseObject obj = groupDoc.getXObject(ref.getDocumentReference(), "member", this.userName);
                assertNotNull(obj, "User " + this.userName + " is not in the group " + groupName);
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
