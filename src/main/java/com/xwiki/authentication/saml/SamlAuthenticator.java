package com.xwiki.authentication.saml;

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.XWikiRequest;
import com.xpn.xwiki.web.XWikiResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;

import static java.util.Arrays.asList;
import static org.apache.commons.compress.utils.Sets.newHashSet;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.*;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SamlAuthenticator {
    public static final String ORIGINAL_URL_SESSION_KEY = "saml20_url";
    private static final EntityReference SAML_XCLASS = new EntityReference("SAMLAuthClass", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));

    private static final EntityReference PROFILE_PARENT = new EntityReference("XWikiUsers", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));
    private static final EntityReference USER_XCLASS = PROFILE_PARENT;
    private static final String SAML_ID_XPROPERTY_NAME = "nameid";

    private static final Logger LOG = LoggerFactory.getLogger(SamlAuthenticator.class);
    public static final String PROPERTY_TO_STORE_SAML_MANAGED_GROUPS = "SamlManagedGroups";

    private final Saml2Settings samlSettings;
    private final XwikiAuthConfig authConfig;
    private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver;
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer;
    private final OneLoginAuth loginAuthFactory;
    private Map<String, String> userPropertiesMapping;

    private final XWikiGroupManager groupManager;

    public SamlAuthenticator(XwikiAuthConfig authConfig,
                             DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver,
                             EntityReferenceSerializer<String> compactStringEntityReferenceSerializer,
                             OneLoginAuth loginAuthFactory,
                             XWikiGroupManager groupManager) {
        this.groupManager = groupManager;
        this.authConfig = authConfig;
        this.currentMixedDocumentReferenceResolver = currentMixedDocumentReferenceResolver;
        this.compactStringEntityReferenceSerializer = compactStringEntityReferenceSerializer;
        this.loginAuthFactory = loginAuthFactory;
        this.samlSettings = buildSamlSettings();
    }

    private Saml2Settings buildSamlSettings() {
        final java.util.Properties settings = new java.util.Properties();
        settings.put("onelogin.saml2.strict", true);
        settings.put("onelogin.saml2.sp.entityid", authConfig.entityId);
        settings.put("onelogin.saml2.sp.assertion_consumer_service.url", authConfig.assertionConsumerServiceUrl);
        settings.put("onelogin.saml2.sp.nameidformat", authConfig.nameIdFormat);
        settings.put("onelogin.saml2.idp.entityid", authConfig.idpEntityId);
        settings.put("onelogin.saml2.idp.single_sign_on_service.url", authConfig.idpSingleSignOnUrl);
        settings.put("onelogin.saml2.idp.x509cert", authConfig.x509Certificate);
        return new SettingsBuilder().fromProperties(settings).build();
    }

    public XWikiUser checkAuth(XWikiContext context,
                               SupplierWithException<XWikiUser, XWikiException> defaultAuthHandler)
            throws XWikiException {
        final XWikiRequest request = context.getRequest();
        final XWikiResponse response = context.getResponse();

        final String samlUserName = getSAMLAuthenticatedUserFromSession(context);
        if (samlUserName != null) {
            LOG.debug("User [{}] already logged in the session.", samlUserName);
            return new XWikiUser(getUserReferenceForName(samlUserName), context.isMainWiki());
        }
        final String samlResponse = request.getParameter("SAMLResponse");
        final boolean hasSamlResponse = samlResponse != null;
        if (!hasSamlResponse) {
            final String action = context.getAction();
            if (actionsAllowedByUnauthenticatedUsers().contains(action))
                return defaultAuthHandler.execute();

            if (context.getRequest().getCookie("username") != null)
                return defaultAuthHandler.execute();
        }

        try {
            LOG.debug("SAML 2.0 Authentication redirection started");

            final Auth auth = loginAuthFactory.produce(this.samlSettings, request, response);

            if (!hasSamlResponse) {
                auth.login(XWiki.getRequestURL(request).toString());
                return null;
            }

            auth.processResponse();

            if (auth.isAuthenticated()) {
                final DocumentReference userReference = findOrCreateUser(context, auth);
                if (userReference != null) {
                    // Mark in the current session that we have authenticated the user
                    LOG.debug("Setting authentication in session for user [{}]", userReference);
                    context.getRequest().getSession().setAttribute(authConfig.authFieldName,
                            this.compactStringEntityReferenceSerializer.serialize(userReference));

                    // Successfully logged in, redirect to the originally requested URL
                    final String sourceUrl = (String) request.getSession().getAttribute(ORIGINAL_URL_SESSION_KEY);
                    LOG.debug("Redirecting after valid authentication to [{}]", sourceUrl);
                    context.getResponse().sendRedirect(sourceUrl);
                    context.setFinished(true);
                    LOG.debug("Found authentication of user [{}]", auth.getNameId());
                    return new XWikiUser(userReference, context.isMainWiki());
                }
            }
            LOG.info("Saml authentication failed {}", auth.getLastErrorReason(), auth.getLastValidationException());
        } catch (com.onelogin.saml2.exception.Error e) {
            LOG.error("Saml authentication failed due to configuration issues", e);
            throw new XWikiException(e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Unexpected exception occurred", e);
            throw new XWikiException(e.getMessage(), e);
        }

        return null;
    }

    private List<String> actionsAllowedByUnauthenticatedUsers() {
        return asList("login", "skin", "ssx", "logout", "loginsubmit");
    }

    public String getSAMLAuthenticatedUserFromSession(XWikiContext context) {
        return (String) context.getRequest().getSession(true).getAttribute(authConfig.authFieldName);
    }

    private DocumentReference findOrCreateUser(XWikiContext context, Auth auth) throws XWikiException {
        final Map<String, String> samlAttributes = new HashMap<>();

        try {
            LOG.debug("Reading authentication response");
            auth.getAttributesName().forEach(attributeName ->
                    samlAttributes.put(attributeName, String.join(",", auth.getAttributes().get(attributeName))));

        } catch (Exception e1) {
            LOG.error("Failed reading authentication response", e1);
            throw e1;
        }

        final Map<String, String> xwikiAttributes = mapToXwikiAttributes(samlAttributes);
        final String nameID = auth.getNameId();
        if (LOG.isDebugEnabled()) {
            LOG.debug("SAML ID is [{}]", nameID);
            LOG.debug("SAML samlAttributes are [{}]", samlAttributes);
            LOG.debug("SAML user data are [{}]", xwikiAttributes);
        }
        final DocumentReference userReference = getLocalUsername(nameID, xwikiAttributes, context);
        if (userReference == null)
            return null;

        // we found a user or generated a unique user name
        // check if we need to create/update a user page
        final String database = context.getWikiId();
        try {
            // Switch to main wiki to force users to be global users
            context.setWikiId(context.getMainXWiki());

            final XWiki xwiki = context.getWiki();
            // test if user already exists
            if (xwiki.exists(userReference, context))
                syncUserFields(context, xwikiAttributes, userReference);
            else
            if (!createUser(context, xwikiAttributes, nameID, userReference)) {
                LOG.error("Failed to create user [{}]", userReference);
                return null;
            }
        } finally {
            context.setWikiId(database);
        }
        syncUserGroups(context, samlAttributes, userReference);
        return userReference;
    }

    private void syncUserGroups(XWikiContext context, Map<String, String> attributes, DocumentReference userReference) throws XWikiException {
        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final Set<String> groupsFromSaml = newHashSet((defaultIfNull(attributes.get("XWikiGroups"), "")).split(","));
        groupsFromSaml.add(authConfig.defaultGroupForNewUsers);

        final BaseObject userObj = userDoc.getXObject(USER_XCLASS);
        final Optional<StringProperty> samlManagedGroupsProp = Optional.ofNullable((StringProperty) userObj.get(PROPERTY_TO_STORE_SAML_MANAGED_GROUPS));

        final Set<String> previousManagedGroups = newHashSet(samlManagedGroupsProp.map(StringProperty::getValue).orElse("").split(","));
        previousManagedGroups.removeAll(groupsFromSaml);

        groupsFromSaml.forEach(group -> groupManager.addUserToXWikiGroup(userReference.getName(), group, context));
        previousManagedGroups.forEach(group -> groupManager.removeUserFromXWikiGroup(userReference.getName(), group, context));

        userObj.put("SamlManagedGroups", new StringClass().fromString(String.join(",", groupsFromSaml)));
        context.getWiki().saveDocument(userDoc, context);
    }

    private void syncUserFields(XWikiContext context, Map<String, String> userData, DocumentReference userReference) throws XWikiException {
        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final BaseObject userObj = userDoc.getXObject(USER_XCLASS);
        boolean updated = false;

        for (Entry<String, String> entry : userData.entrySet()) {
            final String field = entry.getKey();
            final String value = entry.getValue();
            final BaseProperty<?> prop = (BaseProperty<?>) userObj.get(field);
            final String currentValue = (prop == null || prop.getValue() == null) ? null : prop.getValue().toString();
            if (Objects.equals(value,currentValue))
                continue;
            userObj.set(field, value, context);
            updated = true;
        }

        if (updated) {
            context.getWiki().saveDocument(userDoc, context);
            LOG.info("User [{}] has been successfully updated", userReference);
        }
    }

    private boolean createUser(XWikiContext context,
                               Map<String, String> xwikiAttributes,
                               String nameID,
                               DocumentReference userReference)
            throws XWikiException {
        LOG.info("Will create new user [{}]", userReference);

        xwikiAttributes.put("active", "1");

        String content = "{{include document=\"XWiki.XWikiUserSheet\"/}}";
        Syntax syntax = Syntax.XWIKI_2_1;

        int result = context.getWiki().createUser(userReference.getName(), xwikiAttributes, PROFILE_PARENT,
                content, syntax, "edit", context);
        if (result < 0) {
            LOG.error("Failed to create user [{}] with code [{}]", userReference, result);
            return false;
        }

        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final BaseObject samlIdObject = userDoc.newXObject(SAML_XCLASS, context);
        @SuppressWarnings("rawtypes")
        final BaseProperty samlIdProp = new StringClass().fromString(nameID);
        samlIdProp.setOwnerDocument(samlIdObject.getOwnerDocument());
        samlIdObject.safeput(SAML_ID_XPROPERTY_NAME, samlIdProp);
        context.getWiki().saveDocument(userDoc, context);

        LOG.info("User [{}] has been successfully created", userReference);
        return true;
    }

    private DocumentReference getLocalUsername(String nameID,
                                               Map<String, String> xwikiAttributes,
                                               XWikiContext context)
            throws XWikiException
    {
        final String sql = "SELECT DISTINCT " +
                "   obj.name " +
                "FROM " +
                "   BaseObject AS obj, StringProperty AS nameidprop " +
                "WHERE " +
                "   obj.className = ?1 " +
                "AND " +
                "   obj.id = nameidprop.id.id " +
                "AND " +
                "   nameidprop.id.name = ?2 " +
                "AND " +
                "   nameidprop.value = ?3";

        final List<String> list = context.getWiki().getStore().search(sql, 1, 0,
                asList(this.compactStringEntityReferenceSerializer.serialize(SAML_XCLASS),
                        SAML_ID_XPROPERTY_NAME, nameID), context);
        final String validUserName;

        if (list.isEmpty()) {
            // User does not exist. Let's generate a unique page name
            LOG.debug("Did not find XWiki User. Generating it.");
            final String userName = generateXWikiUsername(xwikiAttributes);
            if (userName.isEmpty())
                throw new XWikiException(
                        "Could not generate a username for user " + nameID,
                        new IllegalStateException("Could not generate a username for user "));

            validUserName = context.getWiki().getUniquePageName("XWiki", userName, context);
            LOG.debug("Generated XWiki User Name [{}]", validUserName);
        } else {
            validUserName = list.get(0);
            LOG.debug("Found XWiki User [{}]", validUserName);
        }

        if (validUserName != null)
            return getUserReferenceForName(validUserName);
        return null;
    }

    private DocumentReference getUserReferenceForName(String validUserName) {
        return this.currentMixedDocumentReferenceResolver.resolve(validUserName, PROFILE_PARENT);
    }

    private Map<String, String> mapToXwikiAttributes(Map<String, String> samlAttributes) {
        final Map<String, String> extInfos = new HashMap<>();
        for (Entry<String, String> mapping : getSamlToXwikiMapping().entrySet()) {
            final String samlAttributeValue = samlAttributes.get(mapping.getKey());

            if (samlAttributeValue != null)
                extInfos.put(mapping.getValue(), samlAttributeValue);
        }
        return extInfos;
    }

    private String generateXWikiUsername(Map<String, String> userData) {
        final StringBuilder userName = new StringBuilder();

        for (String field : authConfig.userNameRule) {
            String value = StringUtils.trim(userData.get(field));
            if (StringUtils.isBlank(value))
                continue;

            if (authConfig.shouldCapitalizeUserNames)
                value = StringUtils.capitalize(value);

            userName.append(value);
        }
        return userName.toString();
    }

    private Map<String, String> getSamlToXwikiMapping() {
        if (this.userPropertiesMapping != null)
            return this.userPropertiesMapping;

        this.userPropertiesMapping = new HashMap<>();
        for (String fieldMapping : authConfig.fieldMapping) {
            final String[] fieldAndValue = fieldMapping.split("=");
            if (fieldAndValue.length != 2) {
                LOG.error("Error parsing SAML fields_mapping attribute in xwiki.cfg: [{}]", fieldMapping);
                continue;
            }
            final String xwikiPropertyName = fieldAndValue[0].trim();
            final String samlAttributeName = fieldAndValue[1].trim();

            this.userPropertiesMapping.put(samlAttributeName, xwikiPropertyName);
        }

        return this.userPropertiesMapping;
    }
}
