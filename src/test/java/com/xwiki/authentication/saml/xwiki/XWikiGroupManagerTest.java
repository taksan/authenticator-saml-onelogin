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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import com.xwiki.authentication.saml.testsupport.XWikiMock;
import javax.servlet.http.HttpSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class XWikiGroupManagerTest {
    @Test
    public void whenAddUserToGroup_ShouldAddUserToGivenGroup() throws XWikiException {
        given()
            .user("ArthurDent")
                .isNotInGroup("StarshipTroopers")
        .when()
            .user("ArthurDent").isAddedToGroup("StarshipTroopers")
        .then()
            .user("ArthurDent")
                .isInGroup("StarshipTroopers");
    }

    @Test
    public void whenRemoveUserFromGroup_ShouldRemoveUserFromGivenGroup() throws XWikiException {
        given()
            .user("ArthurDent")
                .isInGroups("StarshipTroopers")
        .when()
            .user("ArthurDent").isRemovedFromGroup("StarshipTroopers")
        .then()
            .user("ArthurDent")
                .isntInGroup("StarshipTroopers");
    }

    @Test
    public void whenUserAlreadyMemberOfGroup_ShouldIgnoreTheNewMembership() throws XWikiException {
        given()
            .user("ArthurDent")
                .isInGroups("StarshipTroopers")
        .when()
            .user("ArthurDent").isAddedToGroup("StarshipTroopers")
        .then()
            .user("ArthurDent")
                .isInGroup("StarshipTroopers");
    }

    @Test
    public void whenRemoveUserFromGroupWhereHeIsnt_ShouldNotChangeGroupMembership() throws XWikiException {
        given()
            .user("ArthurDent")
                .isNotInGroup("StarshipTroopers")
        .when()
            .user("ArthurDent").isRemovedFromGroup("StarshipTroopers")
        .then()
            .user("ArthurDent")
                .isntInGroup("StarshipTroopers");
    }

    @Test
    public void whenUserAlreadyMemberOfGroupAndReceiveAnewGroup_ShouldBeInBothGroups() throws XWikiException {
        given()
            .user("ArthurDent")
                .isInGroups("BattlestarGalactica")
        .when()
            .user("ArthurDent").isAddedToGroup("StarshipTroopers")
        .then()
            .user("ArthurDent")
                .isInGroup("StarshipTroopers")
                .isInGroup("BattlestarGalactica");
    }

    @Test
    public void whenUserBelongsToSeveralGroupsAndIsRemovedFromOneGroup_ShouldStillBePartOfOtherGroups() throws XWikiException {
        given()
            .user("ArthurDent")
                .isInGroups("BattlestarGalactica", "StarshipTroopers")
        .when()
            .user("ArthurDent").isRemovedFromGroup("StarshipTroopers")
        .then()
            .user("ArthurDent")
                .isntInGroup("StarshipTroopers")
                .isInGroup("BattlestarGalactica");
    }

    @Test
    public void whenAddUserToGroupWithEmptyName_ShouldNotCreateGroup() {
        given()
            .user("ArthurDent")
                .exists()
        .when()
            .user("ArthurDent").isAddedToGroup("")
        .then()
            .group("")
                .doesntExist();
    }

    @Test
    public void whenRemoveUserFromGroupWithEmptyName_ShouldNotCreateGroup() throws XWikiException {
        given()
            .user("ArthurDent")
                .exists()
        .when()
            .user("ArthurDent").isRemovedFromGroup("")
        .then()
            .group("")
                .doesntExist();
    }

    @Test
    public void whenAddUserToGroupAndFails_ShouldThrowException() {
        given()
            .theresAFailureInGettingDocuments(new XWikiException("Failed to load document", new Throwable()))
        .when()
            .user("ArthurDent").isAddedToGroup("StarshipTroopers")
        .then()
            .thrownException(new XWikiException("Failed to load document", new Throwable()));
    }

    @Test
    public void whenRemoveUserFromGroupAndFails_ShouldThrowException() {
        given()
            .theresAFailureInGettingDocuments(new XWikiException("Failed to load document", new Throwable()))
        .when()
            .user("ArthurDent").isRemovedFromGroup("StarshipTroopers")
        .then()
            .thrownException(new XWikiException("Failed to load document", new Throwable()));
    }


    private GivenDSL given() {
        return new GivenDSL();
    }

    private static class GivenDSL {
        private final XWikiContext context = new XWikiContext();
        private XWikiGroupManager groupManager;

        private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver =
                (docRef, parameters) -> new DocumentReference("XWiki", "XWikiGroups", docRef);

        final XWikiMock xwiki;

        public GivenDSL() {
            final Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            root.setLevel(Level.OFF);

            XWikiRequest request = mock(XWikiRequest.class);
            HttpSession httpSession = mock(HttpSession.class);
            Mockito.when(request.getSession(true)).thenReturn(httpSession);
            Mockito.when(request.getSession()).thenReturn(httpSession);

            context.setWikiId("XWIKI");
            context.setMainXWiki("MAIN-XWIKI");
            context.setAction("");
            context.setResponse(mock(XWikiResponse.class));
            context.setRequest(request);

            xwiki = new XWikiMock(context);
            context.setWiki(xwiki);
        }

        public UserConfigDSL user(String userName) {
            return new UserConfigDSL(userName);
        }

        public WhenDSL when() {
            return new WhenDSL();
        }

        public GivenDSL theresAFailureInGettingDocuments(XWikiException exception) {
            context.setWiki(new XWiki() {
                @Override
                public XWikiDocument getDocument(DocumentReference reference, XWikiContext context) throws XWikiException {
                    throw exception;
                }
            });
            return this;
        }

        class UserConfigDSL {
            private final String userName;

            public UserConfigDSL(String userName) {
                this.userName = userName;
                groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
            }

            public GivenDSL isInGroups(String... groupNames) throws XWikiException {
                for(String groupName: groupNames) {
                    groupManager.addUserToGroup(userName, groupName, context);
                 }
                return GivenDSL.this;
            }

            public GivenDSL isNotInGroup(String groupName) throws XWikiException {
                groupManager.removeUserFromGroup(userName, groupName, context);
                return GivenDSL.this;
            }

            public GivenDSL exists() {
                return GivenDSL.this;
            }
        }

        public class WhenDSL {

            private String userName;

            public WhenDSL user(String userName) {
                this.userName = userName;
                return this;
            }

            public ThenDSL isRemovedFromGroup(String groupName) {
                try {
                    groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
                    groupManager.removeUserFromGroup(userName, groupName, context);
                    return new ThenDSL(null);
                }catch (XWikiException e) {
                    return new ThenDSL(e);
                }
            }
            public ThenDSL isAddedToGroup(String groupName) {
                try{
                    groupManager = new XWikiGroupManager(currentMixedDocumentReferenceResolver);
                    groupManager.addUserToGroup(userName, groupName, context);
                    return new ThenDSL(null);
                }catch (XWikiException e) {
                    return new ThenDSL(e);
                }
            }
        }

        public class ThenDSL {
            String userName;
            private final XWikiException exception;

            public ThenDSL(XWikiException exception) {
                this.exception = exception;
            }

            public ThenDSL then() {
                return this;
            }

            public GroupConfigDSL group(String groupName) {
                return new GroupConfigDSL(groupName);
            }

            public ThenDSL user(String userName) {
                this.userName = userName;
                return this;
            }

            public void thrownException(XWikiException expectedException) {
                assertEquals(expectedException.toString(), exception.toString());
            }

            public ThenDSL isInGroup(String groupName) {
                final BaseObject obj = findUser(groupName);
                assertNotNull(obj, "User " + userName + " is not in the group " + groupName);
                return this;
            }

            public ThenDSL isntInGroup(String groupName) {
                BaseObject obj = findUser(groupName);
                assertNull(obj);

                return this;
            }

            private BaseObject findUser(String groupName) {
                String fullGroupName = "XWikiGroups." + groupName;
                XWikiDocument groupDoc =
                        xwiki.getSavedDocuments().stream().filter(doc -> doc.toString().equals(fullGroupName)).findFirst()
                                .orElseGet(() -> Assertions.fail("Group " + groupName + " doesn't exist"));

                BaseClass ref = xwiki.getGroupClass(context);
                return groupDoc.getXObject(ref.getDocumentReference(), "member", userName);
            }

            class GroupConfigDSL {
                private final String groupName;

                public GroupConfigDSL(String groupName) {
                    this.groupName = groupName;
                }

                public void doesntExist() {
                    String fullGroupName = "XWikiGroups."+groupName;
                    if(xwiki.getSavedDocuments().stream().anyMatch(doc -> doc.toString().equals(fullGroupName)))
                        Assertions.fail("Group " + groupName + " exist");
                }
            }
        }
    }
}
