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
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
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
import static org.junit.jupiter.api.Assertions.*;
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
        when(request.getRequestURL()).thenReturn(new StringBuffer("https://happy"));

        final XWikiUser xWikiUser = subject.checkAuth(context, () -> null);
        assertNull(xWikiUser);
        Mockito.verify(samlAuth).login("https://happy");
    }

    @Test
    public void whenTheRequestHasNotBeenTriggeredByASamlResponseAndUserIsLoggedIn_ShouldDelegateToDefaultAuthHandler() throws XWikiException {
        when(request.getCookie("username")).thenReturn(mock(Cookie.class));

        XWikiUser loggedWikiUser = mock(XWikiUser.class);
        final XWikiUser xWikiUser = subject.checkAuth(context, () -> loggedWikiUser);
        assertEquals(loggedWikiUser, xWikiUser);
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
        when(request.getParameter("SAMLResponse")).thenReturn("Some SAML Response");
        when(samlAuth.isAuthenticated()).thenReturn(true);

        final XWiki wiki = new XWiki() {
            public XWikiDocument getDocument(XWikiDocument doc, XWikiContext context) {
                return mock(XWikiDocument.class);
            }
        };
        context.setWiki(wiki);

        final XWikiStoreInterface xwikiStore = mock(XWikiStoreInterface.class);
        wiki.setStore(xwikiStore);

        when(xwikiStore.exists(any(), any())).thenReturn(true);
        when(xwikiStore.search(anyString(), anyInt(), anyInt(), any(List.class), any()))
            .thenReturn(singletonList("Wiki Peek"));

        final XWikiUser xWikiUser = subject.checkAuth(context, () -> {
            throw new IllegalStateException("should not be invoked");
        });
        verify(samlAuth).processResponse();
        assertEquals("XWiki:Users.Wiki Peek", xWikiUser.getFullName());
    }

    @Test
    public void whenSamlAuthPresentAndSamlAcceptsAuthenticationAndUserDoesntExist_ShouldCreateNewUser() throws Exception {
        given()
            .identityProvider()
                .hasAuthenticationData(user -> {
                    user.id = "arthur.dent@dontpanic.com";
                    user.firstName="Arthur";
                    user.lastName = "Dent";
                })
            .defaultGroupForNewUsers("UserGroup")
            .xwiki(users -> {
                users.noUsersExist();
            })
        .whenAuthenticationIsPerformed()
        .then()
            .samlLoginHasBeenProcessed()    
            .xwiki(users -> {
                users.user("ArthurDent")
                        .hasBeenSaved()
                        .attributes()
                            .firstName("Arthur")
                            .lastName("Dent")
                            .email("arthur.dent@dontpanic.com")
                            .isInGroups("UserGroup");
            })
            .authenticatedUser("ArthurDent");
    }

    private DSL given() throws XWikiException {
        return new DSL();
    }

    private class DSL {
        final Properties props = new Properties();
        final XWikiStoreInterface xwikiStore;
        final XWikiMock xwiki = new XWikiMock();

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

        public IdentityProviderDSL identityProvider() {
            return new IdentityProviderDSL();
        }

        public DSL defaultGroupForNewUsers(String userGroup) {
            props.setProperty("xwiki.authentication.saml2.default_group_for_new_users",userGroup);
            return this;
        }

        public DSL xwiki(Consumer<XWikiUsersDSL> configuration) {
            configuration.accept(new XWikiUsersDSL());
            return this;
        }

        public class IdentityProviderDSL {

            public DSL hasAuthenticationData(Consumer<IdpUserData> userConfiguration) {
                when(request.getParameter("SAMLResponse")).thenReturn("Some SAML Response");
                when(samlAuth.isAuthenticated()).thenReturn(true);
                IdpUserData idpUserData = new IdpUserData();
                userConfiguration.accept(idpUserData);
                when(samlAuth.getNameId()).thenReturn(idpUserData.id);

                final Map<String, List<String>> samlAttributes = new LinkedHashMap<>();
                samlAttributes.put("firstName", singletonList(idpUserData.firstName));
                samlAttributes.put("lastName", singletonList(idpUserData.lastName));
                samlAttributes.put("email", singletonList(idpUserData.id));

                when(samlAuth.getAttributesName()).thenReturn(new ArrayList<>(samlAttributes.keySet()));
                when(samlAuth.getAttributes()).thenReturn(samlAttributes);

                return DSL.this;
            }
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
        }

        public ThenDSL whenAuthenticationIsPerformed() throws XWikiException {
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

            final XWikiUser xWikiUser = subject.checkAuth(context, () -> {
                throw new IllegalStateException("should not be invoked");
            });
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

            public ThenDSL samlLoginHasBeenProcessed() throws Exception {
                verify(samlAuth).processResponse();
                return this;
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

                    class AssertionAttributesDSL {

                        public AssertionAttributesDSL firstName(String firstName) {
                            assertEquals(firstName, xwiki.getSaveAttributeValue("first_name"));
                            return this;
                        }

                        public AssertionAttributesDSL lastName(String lastName) {
                            assertEquals(lastName, xwiki.getSaveAttributeValue("last_name"));
                            return this;
                        }

                        public AssertionAttributesDSL email(String email) {
                            assertEquals(email, xwiki.getSaveAttributeValue("email"));
                            return this;
                        }

                        public void isInGroups(String userGroup) {
                            String userName = xWikiUser.getFullName().replace("XWiki:Users.", "");
                            verify(groupManager).addUserToXWikiGroup(userName,userGroup, context);
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
    }

    /* TODO: testes a serem realizados
        usuario existente:
            nao esta no grupo default
            esta no grupo default
            nao esta em algum grupo que veio do SAML
            esta em grupos que foram removidos do SAML
        usuario nao existente:
            nao esta no grupo default
            esta no grupo default
            nao esta em algum grupo que veio do SAML
            esta em grupos que foram removidos do SAML
     */

    private static class  XWikiMock extends XWiki{
        final BaseClass baseClassMock = new BaseClass();
        final BaseObject baseObjectMock = new BaseObject();
        final Map<String, BaseObject> createdObjectsByEntity = new LinkedHashMap<>();
        public List<XWikiDocument> savedDocuments = new LinkedList<>();
        private Map<String, ?> savedUserAttributes;

        @Override
        public int createUser(String userName,
                              Map<String, ?> map,
                              EntityReference parentReference,
                              String content,
                              Syntax syntax,
                              String userRights,
                              XWikiContext context) {
            this.savedUserAttributes = map;
            return 0;
        }

        public String getSaveAttributeValue(String attributeName) {
            return savedUserAttributes.get(attributeName).toString();
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
