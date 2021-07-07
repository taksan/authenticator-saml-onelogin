package com.xwiki.authentication.saml;

import com.beust.jcommander.internal.Maps;
import com.onelogin.saml2.Auth;
import com.onelogin.saml2.exception.SettingsException;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SamlAuthenticatorTest {

    private XWikiContext context;
    private SamlAuthenticator subject;
    private XWikiRequest request;
    private Auth samlAuth;
    private XWikiGroupManager groupManager;

    @Before
    public void setup() throws ComponentLookupException {
        ConfigurationSourceWithProperties cfg = new ConfigurationSourceWithProperties();
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);

        final Properties props = new Properties();
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

        samlAuth = mock(Auth.class);
        OneLoginAuth oneLoginAuth = (settings, request, response) -> samlAuth;

        context = new XWikiContext();
        context.setWikiId("XWIKI");
        context.setMainXWiki("MAIN-XWIKI");
        context.setAction("");
        context.setResponse(mock(XWikiResponse.class));
        request = mock(XWikiRequest.class);
        HttpSession httpSession = mock(HttpSession.class);
        when(request.getSession(true)).thenReturn(httpSession);
        when(request.getSession()).thenReturn(httpSession);

        context.setRequest(request);

        groupManager = mock(XWikiGroupManager.class);
        subject = new SamlAuthenticator(authConfig,
                currentMixedDocumentReferenceResolver,
                compactStringEntityReferenceSerializer,
                oneLoginAuth,
                groupManager);

        final ComponentManager globalCm = mock(ComponentManager.class);
        Utils.setComponentManager(globalCm);

        final ComponentManager contextCm = mock(ComponentManager.class);
        when(globalCm.getInstance(ComponentManager.class, "context")).thenReturn(contextCm);

        @SuppressWarnings("rawtypes") final EntityReferenceSerializer local = (reference, parameters) -> reference + "";
        when(contextCm.getInstance(EntityReferenceSerializer.TYPE_STRING, "local")).thenReturn(local);

        when(contextCm.getInstance(DocumentReferenceResolver.TYPE_STRING, "currentmixed"))
                .thenReturn(currentMixedDocumentReferenceResolver);
    }
    @Test
    public void whenTheRequestHasNotBeenTriggeredByASamlResponseAndUserIsntLoggedIn_ShouldStartSamlAuthentication() throws XWikiException, IOException, SettingsException {
        given()
            .userIsAnonymous()
            .currentRequestUrlIs("https://happy")
        .whenAuthenticationIsVerified()
        .then()
            .shouldRedirectToIdentityProviderAndThenReturnTo("https://happy");
    }

    @Test
    public void whenTheRequestHasNotBeenTriggeredByASamlResponseAndUserIsLoggedIn_ShouldDelegateToDefaultAuthHandler() throws XWikiException {
        given()
            .userIsLogged("ArthurDent")
        .whenAuthenticationIsVerified()
        .then()
            .authenticatedUserIs("ArthurDent");
    }

    @Test
    public void whenTheRequestHasNotBeenTriggeredByASamlResponseAndLoggedUserWithLoginAction_ShouldExecuteDefaultHandler() throws XWikiException {
        verifyDefaultHandlerIsInvoked("login");
    }

    @Test
    public void whenNoSamlAuthAndLoggedUserWithSkinAction_ShouldExecuteDefaultHandler() throws XWikiException {
        verifyDefaultHandlerIsInvoked("skin");
    }

    @Test
    public void whenTheRequestHasNotBeenTriggeredByASamlResponseAndLoggedUserWithSsxAction_ShouldExecuteDefaultHandler() throws XWikiException {
        verifyDefaultHandlerIsInvoked("ssx");
    }

    @Test
    public void whenTheRequestHasNotBeenTriggeredByASamlResponseAndLoggedUserWithLogoutAction_ShouldExecuteDefaultHandler() throws XWikiException {
        verifyDefaultHandlerIsInvoked("logout");
    }

    @Test
    public void whenNoSamlAuthAndLoggedUserWithLoginSubmitAction_ShouldExecuteDefaultHandler() throws XWikiException {
        verifyDefaultHandlerIsInvoked("loginsubmit");
    }

    @Test
    public void whenSamlAuthPresentAndSamlAcceptsAuthenticationAndUserExists_ShouldReturnExistingUser() throws Exception {
        given()
            .isIdentityProviderAuthentication(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName="Arthur";
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
                     .isInGroups("UserGroup")
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
            .isIdentityProviderAuthentication(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName="Arthur";
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
                     .isInGroups("UserGroup")
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
            .isIdentityProviderAuthentication(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName="Arthur";
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
                     .isInGroups("samlGroup")
            );
    }

    @Test
    public void whenUserExistsAndUserHasGroupsAndThereArentGroupsInSamlResponse_ShouldRemoveUserFromGroups() throws Exception {
        given()
            .isIdentityProviderAuthentication(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName="Arthur";
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
                     .isntInGroups("samlGroup")
            );
    }

    @Test
    public void whenUserDoesntExistsAndUserHasNoGroupsAndThereAreGroupsInSamlResponse_ShouldCreateUserAndAddUserInSamlGroups() throws Exception {
        given()
            .isIdentityProviderAuthentication(user -> {
                user.id = "arthur.dent@dontpanic.com";
                user.firstName="arthur";
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
                     .isInGroups("samlGroup")
            );
    }

    @Test
    public void whenUserFirstNameIsInLowerCaseAndCapitalizeIsNotEnabled_ShouldKeepNameWithoutCapitalization() throws Exception {
        given()
                .isIdentityProviderAuthentication(user -> {
                    user.id = "arthur.dent@dontpanic.com";
                    user.firstName="arthur";
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
    public void whenUserIsAlreadyLogged_ShouldBeAuthenticated() throws Exception {
        given()
            .userIsLoggedInTheSession("ArthurDent")
        .whenAuthenticationIsVerified()
        .then()
            .authenticatedUser("ArthurDent");
    }

    private DSL given() throws XWikiException {
        return new DSL();
    }

    private class DSL {
        final Properties props = new Properties();
        final XWikiStoreInterface xwikiStore;
        final XWikiMock xwiki = new XWikiMock();
        XWikiUser loggedWikiUser = null;

        DSL() throws XWikiException {
            context.setWiki(xwiki);
            props.putAll(
                    Maps.newHashMap(
                            "xwiki.authentication.saml2.sp.entityid", "",
                            "xwiki.authentication.saml2.sp.assertion_consumer_service.url", "",
                            "xwiki.authentication.saml2.idp.entityid", "",
                            "xwiki.authentication.saml2.idp.single_sign_on_service.url", "",
                            "xwiki.authentication.saml2.idp.x509cert", "",
                            "xwiki.authentication.saml2.default_group_for_new_users", "XWiki.SamlUsers"
                    ));

            xwikiStore = mock(XWikiStoreInterface.class);
            xwiki.setStore(xwikiStore);

            when(xwikiStore.loadXWikiDoc(any(), any())).thenAnswer(
                    (Answer<XWikiDocument>) invocation -> (XWikiDocument) invocation.getArguments()[0]);

        }

        public DSL defaultGroupForNewUsers(String userGroup) {
            props.setProperty("xwiki.authentication.saml2.default_group_for_new_users",userGroup);

            return this;
        }

        public DSL xwiki(Consumer<XWikiUsersDSL> configuration) {
            configuration.accept(new XWikiUsersDSL());
            return this;
        }

        public DSL userIsAnonymous() {
            when(samlAuth.isAuthenticated()).thenReturn(false);
            when(samlAuth.getNameId()).thenReturn(null);
            return this;
        }

        public DSL userIsLogged(String loggedUserName){
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

        public DSL isIdentityProviderAuthentication(Consumer<IdpUserData> userConfiguration) {
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
            public XWikiUsersDSL userExists(String userName){
                try {
                    when(xwikiStore.exists(any(), any())).thenReturn(true);
                    when(xwikiStore.search(anyString(), anyInt(), anyInt(), any(List.class), any()))
                            .thenReturn(singletonList(userName));
                }catch (XWikiException e){
                    throw new RuntimeException(e);
                }
                return this;
            }
            public void userHasGroup(String groupName) {
                try{
                    when(xwiki.baseObjectMock.get("SamlManagedGroups")).thenReturn(new StringClass().fromString(groupName));
                }catch (XWikiException e){
                    throw new RuntimeException(e);
                }
            }
        }

        public ThenDSL whenAuthenticationIsVerified() throws XWikiException {
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

            final XWikiUser xWikiUser = subject.checkAuth(context, () -> loggedWikiUser);
            return new ThenDSL(xWikiUser);
        }

        public class ThenDSL {
            private final XWikiUser xWikiUser;

            public ThenDSL(XWikiUser xWikiUser) {
                this.xWikiUser = xWikiUser;
            }

            public ThenDSL xwiki(Consumer<AssertionUsersDSL> assertionUsers) {
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

                    public AssertionUserDSL isInGroups(String userGroup) {
                        String userName = xWikiUser.getFullName().replace("XWiki:Users.", "");
                        verify(groupManager).addUserToXWikiGroup(userName,userGroup, context);
                        return this;
                    }

                    public void isntInGroups(String userGroup) {
                        String userName = xWikiUser.getFullName().replace("XWiki:Users.", "");
                        verify(groupManager).removeUserFromXWikiGroup(userName,userGroup, context);
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
    
    private static class IdpUserData {
        public String id;
        public String firstName;
        public String lastName;
        public String newGroup;
    }

    private static class  XWikiMock extends XWiki{
        final BaseClass baseClassMock = new BaseClass();
        final BaseObject baseObjectMock = mock(BaseObject.class);
        final Map<String, BaseObject> createdObjectsByEntity = new LinkedHashMap<>();
        public List<XWikiDocument> savedDocuments = new LinkedList<>();
        private final Map<String, String> savedUserAttributes = new LinkedHashMap<>();

        public XWikiMock() {
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

        public XWikiDocument getDocument(DocumentReference reference, XWikiContext context) throws XWikiException
        {
            XWikiDocument doc = new XWikiDocument(new DocumentReference(reference, (Locale) null), reference.getLocale()) {
                @Override
                public BaseObject newXObject(EntityReference classReference, XWikiContext context)  {
                    createdObjectsByEntity.putIfAbsent(classReference.toString(), new BaseObject());
                    return createdObjectsByEntity.get(classReference.toString());
                }
                public BaseObject getXObject(EntityReference reference) {
                    return baseObjectMock;
                }
            };

            return getDocument(doc, context);
        }
        public void saveDocument(XWikiDocument doc, XWikiContext context) {
            savedDocuments.add(doc);
        }

        public BaseClass getGroupClass(XWikiContext context) {
            return  baseClassMock;
        }

        public void hasSaved(String document) {
            assertTrue(this.savedDocuments.stream().map(XWikiDocument::toString).anyMatch(s -> s.equals(document)));
        }
    }

    private void verifyDefaultHandlerIsInvoked(String actionToBeHandled) throws XWikiException {
        context.setAction(actionToBeHandled);

        final AtomicBoolean handled = new AtomicBoolean();
        subject.checkAuth(context, () -> {
            handled.set(true);
            return null;
        });
        assertTrue(handled.get());
    }
}
