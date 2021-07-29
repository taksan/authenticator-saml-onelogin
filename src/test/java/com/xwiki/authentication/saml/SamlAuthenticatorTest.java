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
import com.beust.jcommander.internal.Maps;
import com.onelogin.saml2.Auth;
import com.onelogin.saml2.exception.SettingsException;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class SamlAuthenticatorTest {
    @Test
    public void whenAnonymousUserAccess_ShouldStartSamlAuthentication() throws XWikiException, IOException, SettingsException, ComponentLookupException {
        given()
            .userIsAnonymous()
            .currentRequestUrlIs("https://happy")
        .whenAuthenticationIsVerified()
        .then()
            .shouldRedirectToIdentityProviderAndThenReturnTo("https://happy");
    }

    @Test
    public void whenLoggedUserInByCookieAccess_ShouldReturnTheSameLoggedInUser() throws XWikiException, ComponentLookupException {
        given()
            .userIsLoggedInByCookie("ArthurDent")
        .whenAuthenticationIsVerified()
        .then()
            .authenticatedUserIs("ArthurDent");
    }

    @Test
    public void whenUserIsAlreadyLoggedInTheSession_ShouldReturnSameLoggedUser() throws Exception {
        given()
            .userIsLoggedInTheSession("ArthurDent")
        .whenAuthenticationIsVerified()
        .then()
            .authenticatedUser("ArthurDent");
    }

    @Test
    public void whenAnonymousUserAccessWithLoginAction_ShouldBeHandledByDefaultHandler() throws XWikiException, ComponentLookupException {
        given()
             .userIsAnonymous()
             .accessWithGivenAction("login")
        .whenUserAccess()
        .then()
             .verifyDefaultHandlerIsInvoked();
    }

    @Test
    public void whenAnonymousUserAccessWithSkinAction_ShouldBeHandledByDefaultHandler() throws XWikiException, ComponentLookupException {
        given()
                .userIsAnonymous()
                .accessWithGivenAction("skin")
        .whenUserAccess()
        .then()
                .verifyDefaultHandlerIsInvoked();
    }

    @Test
    public void whenAnonymousUserAccessWithSsxAction_ShouldBeHandledByDefaultHandler() throws XWikiException, ComponentLookupException {
        given()
            .userIsAnonymous()
            .accessWithGivenAction("ssx")
        .whenUserAccess()
        .then()
            .verifyDefaultHandlerIsInvoked();
    }

    @Test
    public void whenAnonymousUserAccessWithLogoutAction_ShouldBeHandledByDefaultHandler() throws XWikiException, ComponentLookupException {
        given()
            .userIsAnonymous()
            .accessWithGivenAction("logout")
        .whenUserAccess()
        .then()
            .verifyDefaultHandlerIsInvoked();
    }

    @Test
    public void whenAnonymousUserAccessWithLoginSubmit_ShouldBeHandledByDefaultHandler() throws XWikiException, ComponentLookupException {
        given()
            .userIsAnonymous()
            .accessWithGivenAction("loginsubmit")
        .whenUserAccess()
        .then()
            .verifyDefaultHandlerIsInvoked();
    }

    @Test
    public void whenSamlAuthPresentAndSamlAcceptsAuthenticationAndUserExists_ShouldReturnExistingUser() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "Arthur";
                user.lastName = "Dent";
            })
            .defaultGroupForNewUsers("UserGroup")
            .xwiki(users ->
                users.userExists("ArthurDent")
            )
        .whenAuthenticationIsVerified()
        .then()
            .samlAuthenticationHasBeenProcessed()
            .xwiki(users ->
                users.user("ArthurDent")
                    .hasBeenSaved()
                    .isInGroup("UserGroup")
                    .attributes()
                        .firstName("Arthur")
                        .lastName("Dent")
                        .email("arthur.dent@dontpanic.com")
            )
            .authenticatedUser("ArthurDent");
    }

    @Test
    public void whenSamlAuthPresentAndSamlAcceptsAuthenticationAndUserDoesntExist_ShouldCreateNewUser() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "Arthur";
                user.lastName = "Dent";
            })
            .defaultGroupForNewUsers("UserGroup")
            .xwiki(users ->
                users.noUsersExist()
            )
        .whenAuthenticationIsVerified()
        .then()
            .samlAuthenticationHasBeenProcessed()
            .xwiki(users ->
                users.user("ArthurDent")
                     .hasBeenSaved()
                     .isInGroup("UserGroup")
                     .attributes()
                        .firstName("Arthur")
                        .lastName("Dent")
                        .email("arthur.dent@dontpanic.com")
            )
            .authenticatedUser("ArthurDent");
    }

    @Test
    public void whenUserExistsAndThereAreGroupsInSamlResponse_ShouldAddUserToSamlGroups() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "Arthur";
                user.lastName = "Dent";
                user.newGroup = "samlGroup";
            })
            .xwiki(users ->
                users.userExists("ArthurDent")
            )
       .whenAuthenticationIsVerified()
       .then()
           .samlAuthenticationHasBeenProcessed()
           .xwiki(users ->
                users.user("ArthurDent")
                     .isInGroup("samlGroup")
            );
    }

    @Test
    public void whenUserExistsAndUserHasGroupsAndThereArentGroupsInSamlResponse_ShouldRemoveUserFromGroups() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "Arthur";
                user.lastName = "Dent";
            })
            .xwiki(users ->
                users.userExists("ArthurDent")
                     .userHasGroup("samlGroup")
            )
        .whenAuthenticationIsVerified()
        .then()
            .samlAuthenticationHasBeenProcessed()
            .xwiki(users ->
                users.user("ArthurDent")
                     .isntInGroup("samlGroup")
            );
    }

    @Test
    public void whenUserDoesntExistsAndUserHasNoGroupsAndThereAreGroupsInSamlResponse_ShouldCreateUserAndAddUserInSamlGroups() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "arthur";
                user.lastName = "Dent";
                user.newGroup = "samlGroup";
            })
            .xwiki(users ->
                users.noUsersExist()
            )
        .whenAuthenticationIsVerified()
        .then()
            .samlAuthenticationHasBeenProcessed()
            .xwiki(users ->
                users.user("ArthurDent")
                     .isInGroup("samlGroup")
            );
    }

    @Test
    public void whenUserFirstNameIsInLowerCaseAndCapitalizeIsNotEnabled_ShouldKeepNameWithoutCapitalization() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "arthur";
            })
            .shouldCapitalize(false)
        .whenAuthenticationIsVerified()
        .then()
            .samlAuthenticationHasBeenProcessed()
            .xwiki(users ->
                users.user("arthur")
            );
    }

    @Test
    public void whenUserNameIsNotCapitalizedAndCapitalizationIsEnabled_ShouldCapitalize() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "arthur";
            })
            .shouldCapitalize(true)
        .whenAuthenticationIsVerified()
        .then()
            .samlAuthenticationHasBeenProcessed()
            .xwiki(users ->
                users.user("Arthur")
            );
    }

    @Test
    public void whenFailsToGenerateUserName_ShouldThrowException() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "";
                user.lastName = "";
            })
        .whenAuthenticationIsVerified()
        .then()
            .throwsUserCreationFailedException("Could not generate a username for user arthur.dent@dontpanic.com");
    }

    @Test
    public void whenFailsToCreateUser_ShouldThrowException() throws Exception {
        given()
            .identityProviderAuthenticatedUser(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName = "Arthur";
                user.lastName ="Dent";
            })
            .userCreationFails()
        .whenAuthenticationIsVerified()
        .then()
            .throwsUserCreationFailedException("XWiki failed to create user [arthur.dent@dontpanic.com]. Error code [-3]");
    }



    private DSL given() throws XWikiException, ComponentLookupException {
        return new DSL();
    }

    private static class DSL {
        final Properties props = new Properties();
        final ComponentManager globalCm = mock(ComponentManager.class);
        final ComponentManager contextCm = mock(ComponentManager.class);
        final XWikiStoreInterface xwikiStore;
        final SamlAuthenticator subject;
        final XWikiRequest request;
        final XWikiGroupManager groupManager;
        XWikiMock xwiki;
        XWikiUser loggedWikiUser = null;
        ConfigurationSourceWithProperties cfg = new ConfigurationSourceWithProperties();
        Auth samlAuth = mock(Auth.class);
        XWikiContext context = new XWikiContext();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

        DSL() throws XWikiException, ComponentLookupException {
            root.setLevel(Level.OFF);
            props.putAll(
                    Maps.newHashMap(
                            "xwiki.authentication.saml2.sp.entityid", "",
                            "xwiki.authentication.saml2.sp.assertion_consumer_service.url", "",
                            "xwiki.authentication.saml2.idp.entityid", "",
                            "xwiki.authentication.saml2.idp.single_sign_on_service.url", "",
                            "xwiki.authentication.saml2.idp.x509cert", "",
                            "xwiki.authentication.saml2.default_group_for_new_users", "XWiki.SamlUsers"
                    ));
            cfg.setFromProperties(props);

            XwikiAuthConfig authConfig = XwikiAuthConfig.from(cfg);
            DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver =
                    (docRef, parameters) -> new DocumentReference( "XWiki", "Users", docRef);

            EntityReferenceSerializer<String> compactStringEntityReferenceSerializer =
                    (reference, parameters) -> null;

            OneLoginAuth oneLoginAuth = (settings, request, response) -> samlAuth;
            context.setWikiId("XWIKI");
            context.setMainXWiki("MAIN-XWIKI");
            context.setAction("");
            context.setResponse(mock(XWikiResponse.class));
            request = mock(XWikiRequest.class);
            HttpSession httpSession = mock(HttpSession.class);
            when(request.getSession(true)).thenReturn(httpSession);
            when(request.getSession()).thenReturn(httpSession);

            context.setRequest(request);

            xwiki = new XWikiMock(context);
            groupManager = mock(XWikiGroupManager.class);
            Utils.setComponentManager(globalCm);

            when(globalCm.getInstance(ComponentManager.class, "context")).thenReturn(contextCm);

            @SuppressWarnings("rawtypes") final EntityReferenceSerializer local = (reference, parameters) -> reference + "";
            when(contextCm.getInstance(EntityReferenceSerializer.TYPE_STRING, "local")).thenReturn(local);

            when(contextCm.getInstance(DocumentReferenceResolver.TYPE_STRING, "currentmixed"))
                    .thenReturn(currentMixedDocumentReferenceResolver);
            subject = new SamlAuthenticator(authConfig,
                    currentMixedDocumentReferenceResolver,
                    compactStringEntityReferenceSerializer,
                    oneLoginAuth,
                    groupManager);

            context.setWiki(xwiki);
            xwikiStore = mock(XWikiStoreInterface.class);
            xwiki.setStore(xwikiStore);

            when(xwikiStore.loadXWikiDoc(any(), any())).thenAnswer(
                    (Answer<XWikiDocument>) invocation -> (XWikiDocument) invocation.getArguments()[0]);

        }

        public DSL defaultGroupForNewUsers(String userGroup) {
            props.setProperty("xwiki.authentication.saml2.default_group_for_new_users",userGroup);

            return this;
        }

        public DSL xwiki(ConsumerWithThrowable<XWikiUsersDSL,Exception> configuration) throws Exception {
            configuration.accept(new XWikiUsersDSL());
            return this;
        }

        public DSL userIsAnonymous() {
            when(samlAuth.isAuthenticated()).thenReturn(false);
            when(samlAuth.getNameId()).thenReturn(null);
            return this;
        }

        public DSL userIsLoggedInByCookie(String loggedUserName){
            loggedWikiUser = mock(XWikiUser.class);
            when(loggedWikiUser.getFullName()).thenReturn(loggedUserName);
            when(request.getCookie("username")).thenReturn(mock(Cookie.class));
            return this;
        }

        public DSL userIsLoggedInTheSession(String loggedUserName){
             when(context.getRequest().getSession(true).getAttribute(any())).thenReturn(loggedUserName);
            return this;
        }

        public DSL currentRequestUrlIs(String currentRequestUrl){
            when(request.getRequestURL()).thenReturn(new StringBuffer(currentRequestUrl));
            return this;
        }

        public DSL identityProviderAuthenticatedUser(Consumer<IdpUserData> userConfiguration) {
            when(request.getParameter("SAMLResponse")).thenReturn("Some SAML Response");
            when(samlAuth.isAuthenticated()).thenReturn(true);
            IdpUserData idpUserData = new IdpUserData();
            userConfiguration.accept(idpUserData);
            when(samlAuth.getNameId()).thenReturn(idpUserData.id);

            final Map<String, List<String>> samlAttributes = new LinkedHashMap<>();
            samlAttributes.put("firstName", singletonList(idpUserData.firstName));
            samlAttributes.put("lastName", singletonList(idpUserData.lastName));
            samlAttributes.put("email", singletonList(idpUserData.id));
            samlAttributes.put("XWikiGroups", singletonList(idpUserData.newGroup));

            when(samlAuth.getAttributesName()).thenReturn(new ArrayList<>(samlAttributes.keySet()));
            when(samlAuth.getAttributes()).thenReturn(samlAttributes);

            return DSL.this;
        }

        public DSL shouldCapitalize(boolean shouldCapitalize) {
            props.setProperty("xwiki.authentication.saml2.xwiki_user_rule_capitalize", shouldCapitalize+"");
            return this;
        }

        public ThenDSLForDefaultHandler whenUserAccess() {
            final AtomicBoolean handled = new AtomicBoolean(false);
            try {
                subject.checkAuth(context, () -> {
                        handled.set(true);
                        return null;
                    }
                );
                return new ThenDSLForDefaultHandler(handled.get());
            }catch (XWikiException e){
                throw new IllegalStateException("checkAuth default verification failed", e);
            }
        }

        public DSL accessWithGivenAction(String actionToBeHandled) {
            context.setAction(actionToBeHandled);
            return this;
        }

        public DSL userCreationFails() {
            xwiki.makeCreateUserReturnError();
            return this;
        }

        public class XWikiUsersDSL {
            public void noUsersExist() {
                try {
                    when(xwikiStore.exists(any(), any())).thenReturn(false);
                    when(xwikiStore.search(anyString(), anyInt(), anyInt(), any(List.class), any()))
                            .thenReturn(emptyList());
                }catch (XWikiException e){
                    throw new RuntimeException(e);
                }
            }
            public XWikiUsersDSL userExists(String userName) {
                try {
                    when(xwikiStore.exists(any(), any())).thenReturn(true);
                    when(xwikiStore.search(anyString(), anyInt(), anyInt(), any(List.class), any()))
                            .thenReturn(singletonList(userName));
                }catch (XWikiException e){
                    throw new RuntimeException(e);
                }
                return this;
            }
            public void userHasGroup(String groupName) throws XWikiException {
                when(xwiki.baseObjectMock.get("SamlManagedGroups")).thenReturn(new StringClass().fromString(groupName));
            }
        }

        public ThenDSL whenAuthenticationIsVerified() {
            ConfigurationSourceWithProperties cfg = new ConfigurationSourceWithProperties();
            cfg.setFromProperties(props);
            XwikiAuthConfig authConfig = XwikiAuthConfig.from(cfg);
            DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver =
                    (docRef, parameters) -> new DocumentReference( "XWiki", "Users", docRef);
            EntityReferenceSerializer<String> compactStringEntityReferenceSerializer =
                    (reference, parameters) -> null;
            OneLoginAuth oneLoginAuth = (settings, request, response) -> samlAuth;

            SamlAuthenticator subject = new SamlAuthenticator(authConfig,
                    currentMixedDocumentReferenceResolver,
                    compactStringEntityReferenceSerializer,
                    oneLoginAuth,
                    groupManager);

            try {
                final XWikiUser xWikiUser = subject.checkAuth(context, () -> loggedWikiUser);
                return new ThenDSL(xWikiUser, null);
            }catch (XWikiException xWikiException){
                return new ThenDSL(null, xWikiException);
            }
        }

        public class ThenDSL {
            private final XWikiUser xWikiUser;
            private final XWikiException xWikiException;

            public ThenDSL(XWikiUser xWikiUser, XWikiException xWikiException) {
                this.xWikiUser = xWikiUser;
                this.xWikiException = xWikiException;
            }

            public ThenDSL xwiki(ConsumerWithThrowable<AssertionUsersDSL,Exception> assertionUsers) throws Exception {
                assertionUsers.accept(new AssertionUsersDSL());
                return this;
            }

            public void authenticatedUser(String user) {
                assertEquals("XWiki:Users." + user, xWikiUser.getFullName());
            }

            public ThenDSL then() {
                return this;
            }

            public ThenDSL samlAuthenticationHasBeenProcessed() throws Exception {
                verify(samlAuth).processResponse();
                return this;
            }

            public void shouldRedirectToIdentityProviderAndThenReturnTo(String returnUrl) throws IOException, SettingsException {
                Mockito.verify(samlAuth).login(returnUrl);
            }

            public void authenticatedUserIs(String expectedUserName){
                assertEquals(expectedUserName, xWikiUser.getFullName());
            }

            public void throwsUserCreationFailedException(String expectedMessage) {
                assertTrue(xWikiException.getMessage().contains(expectedMessage));
            }

            public class AssertionUsersDSL {

                public AssertionUserDSL user(String userName) {
                    return new AssertionUserDSL(userName);
                }

                public class AssertionUserDSL {
                    private final String userName;

                    public AssertionUserDSL(String userName) {
                        this.userName = userName;
                    }

                    public AssertionUserDSL hasBeenSaved() {
                        xwiki.hasSaved("Users."+userName);
                        return this;
                    }

                    public AssertionAttributesDSL attributes() {
                        return new AssertionAttributesDSL();
                    }

                    public AssertionUserDSL isInGroup(String userGroup) throws XWikiException {
                        String userName = xWikiUser.getFullName().replace("XWiki:Users.", "");
                        verify(groupManager).addUserToGroup(userName,userGroup, context);
                        return this;
                    }

                    public void isntInGroup(String userGroup) throws XWikiException {
                        String userName = xWikiUser.getFullName().replace("XWiki:Users.", "");
                        verify(groupManager).removeUserFromGroup(userName,userGroup, context);
                    }

                    class AssertionAttributesDSL {

                        public AssertionAttributesDSL firstName(String firstName) {
                            assertEquals(firstName, xwiki.getSaveAttributeValue("first_name"));
                            return this;
                        }

                        public AssertionAttributesDSL lastName(String lastName) {
                            assertEquals(lastName, xwiki.getSaveAttributeValue("last_name"));
                            return this;
                        }

                        public void email(String email) {
                            assertEquals(email, xwiki.getSaveAttributeValue("email"));
                        }
                    }
                }
            }
        }
    }

    static class ThenDSLForDefaultHandler {
        private final boolean defaultHandlerInvoked;

        public ThenDSLForDefaultHandler(boolean defaultHandlerInvoked) {
            this.defaultHandlerInvoked = defaultHandlerInvoked;
        }

        public void verifyDefaultHandlerIsInvoked(){
            assertTrue(this.defaultHandlerInvoked, "Default handler not invoked");
        }

        public ThenDSLForDefaultHandler then() {
            return this;
        }
    }
    
    private static class IdpUserData {
        public String id;
        public String firstName;
        public String lastName;
        public String newGroup;
    }
}
