package com.xwiki.authentication.saml;

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;
import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.BaseProperty;
import com.xpn.xwiki.objects.StringProperty;
import com.xpn.xwiki.objects.classes.StringClass;
import com.xpn.xwiki.user.api.XWikiUser;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.syntax.Syntax;
import static com.xpn.xwiki.XWikiException.*;
import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_USER_CREATE;
import static com.xwiki.authentication.saml.SamlAuthenticator.*;
import static java.util.Arrays.asList;
import static org.apache.commons.compress.utils.Sets.newHashSet;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

public class SamlAuthenticationResponseHandler {
    public static final EntityReference SAML_XCLASS = new EntityReference("SAMLAuthClass", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));
    private static final Logger LOG = LoggerFactory.getLogger(SamlAuthenticationResponseHandler.class);
    public static final String PROPERTY_TO_STORE_SAML_MANAGED_GROUPS = "SamlManagedGroups";
    public static final EntityReference USER_XCLASS = PROFILE_PARENT;
    public static final String SAML_ID_XPROPERTY_NAME = "nameid";

    private final Saml2Settings samlSettings;
    private final XwikiAuthConfig authConfig;
    private final XWikiGroupManager groupManager;
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer;
    private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver;
    private final OneLoginAuth loginAuthFactory;
    private final XWikiContext context;
    private Map<String, String> userPropertiesMapping;


    public SamlAuthenticationResponseHandler(XWikiContext context,
                                             OneLoginAuth loginAuthFactory,
                                             Saml2Settings samlSettings,
                                             XwikiAuthConfig authConfig,
                                             XWikiGroupManager groupManager,
                                             EntityReferenceSerializer<String> compactStringEntityReferenceSerializer,
                                             DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver) {
        this.context = context;
        this.loginAuthFactory = loginAuthFactory;
        this.samlSettings = samlSettings;
        this.authConfig = authConfig;
        this.groupManager = groupManager;
        this.compactStringEntityReferenceSerializer = compactStringEntityReferenceSerializer;
        this.currentMixedDocumentReferenceResolver = currentMixedDocumentReferenceResolver;
    }

    public XWikiUser handle() throws XWikiException {
        try {
            final Auth auth = loginAuthFactory.produce(samlSettings, context.getRequest(), context.getResponse());
            auth.processResponse();

            if (auth.isAuthenticated()) {
                final Saml2XwikiAttributes attributes = extractXWikiAttributesFromSaml(auth);
                return setupAuthenticatedUser(attributes);
            }

            LOG.info("Saml authentication failed {}", auth.getLastErrorReason(), auth.getLastValidationException());
            return null;
        } catch (com.onelogin.saml2.exception.Error e) {
            LOG.error("Saml authentication failed due to configuration issues", e);
            throw new XWikiException(MODULE_XWIKI_PLUGINS, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        } catch (Exception e) {
            LOG.error("Saml authentication failed due to unexpected exception", e);
            throw new XWikiException(MODULE_XWIKI_PLUGINS, ERROR_XWIKI_UNKNOWN, e.getMessage(), e);
        }
    }

    private Saml2XwikiAttributes extractXWikiAttributesFromSaml(Auth auth) {
        final Map<String, String> samlAttributes = extractingSamlAttributes(auth);

        final Set<String> groupsFromSaml = newHashSet((defaultIfNull(samlAttributes.get("XWikiGroups"), "")).split(","));
        groupsFromSaml.add(authConfig.defaultGroupForNewUsers);

        final Map<String, String> xwikiAttributes = mapToXwikiAttributes(samlAttributes);
        final String nameID = auth.getNameId();
        if (LOG.isDebugEnabled()) {
            LOG.debug("SAML ID is [{}]", nameID);
            LOG.debug("SAML samlAttributes are [{}]", samlAttributes);
            LOG.debug("SAML user data are [{}]", xwikiAttributes);
        }
        return new Saml2XwikiAttributes(nameID, xwikiAttributes, groupsFromSaml);
    }

    private Map<String, String> extractingSamlAttributes(Auth auth) {
        final Map<String, String> samlAttributes = new HashMap<>();

        try {
            LOG.debug("Reading authentication response");
            auth.getAttributesName().forEach(attributeName ->
                    samlAttributes.put(attributeName, String.join(",", auth.getAttributes().get(attributeName))));

        } catch (Exception e1) {
            LOG.error("Failed reading authentication response", e1);
            throw e1;
        }
        return samlAttributes;
    }

    private Map<String, String> mapToXwikiAttributes(Map<String, String> samlAttributes) {
        final Map<String, String> extInfos = new HashMap<>();
        for (Map.Entry<String, String> mapping : getSamlToXwikiMapping().entrySet()) {
            final String samlAttributeValue = samlAttributes.get(mapping.getKey());

            if (samlAttributeValue != null)
                extInfos.put(mapping.getValue(), samlAttributeValue);
        }
        return extInfos;
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

    private XWikiUser setupAuthenticatedUser(Saml2XwikiAttributes attributes) throws IOException, XWikiException {
        final DocumentReference userReference = getOrCreateUserIfNeeded(attributes);

        syncUserGroups(attributes.groupsFromSaml, userReference);

        addUserToTheSession(userReference);

        redirectToOriginalRequestedUrl();

        LOG.info("User [{}] authentication complete", attributes.nameID);
        return new XWikiUser(userReference, context.isMainWiki());
    }

    private void syncUserGroups(Set<String> groupsFromSaml, DocumentReference userReference) throws XWikiException {
        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final BaseObject userObj = userDoc.getXObject(USER_XCLASS);

        removeUserFromGroupsMissingFromSamlGroups(groupsFromSaml, userReference, userObj);

        addUserToGroupsInSamlGroups(groupsFromSaml, userReference);

        saveUserWithUpdatedGroups(groupsFromSaml, userDoc, userObj);
    }

    private void removeUserFromGroupsMissingFromSamlGroups(Set<String> groupsFromSaml,
                                                           DocumentReference userReference,
                                                           BaseObject userObj)
            throws XWikiException {

        final Optional<StringProperty> samlManagedGroupsProp = Optional.ofNullable((StringProperty) userObj.get(PROPERTY_TO_STORE_SAML_MANAGED_GROUPS));
        final Set<String> previousManagedGroups = newHashSet(samlManagedGroupsProp.map(StringProperty::getValue).orElse("").split(","));
        previousManagedGroups.removeAll(groupsFromSaml);
        previousManagedGroups.forEach(group -> groupManager.removeUserFromXWikiGroup(userReference.getName(), group, context));
    }

    private void addUserToGroupsInSamlGroups(Set<String> groupsFromSaml, DocumentReference userReference) {
        groupsFromSaml.forEach(group -> groupManager.addUserToXWikiGroup(userReference.getName(), group, context));
    }

    private void saveUserWithUpdatedGroups(Set<String> groupsFromSaml, XWikiDocument userDoc, BaseObject userObj) throws XWikiException {
        userObj.put(PROPERTY_TO_STORE_SAML_MANAGED_GROUPS, new StringClass().fromString(String.join(",", groupsFromSaml)));
        context.getWiki().saveDocument(userDoc, context);
    }

    private void addUserToTheSession(DocumentReference userReference) {
        LOG.debug("Setting authentication in session for user [{}]", userReference);
        context.getRequest().getSession().setAttribute(authConfig.authFieldName,
                this.compactStringEntityReferenceSerializer.serialize(userReference));
    }

    private void redirectToOriginalRequestedUrl() throws IOException {
        final String originalSourceUrl = (String) context.getRequest().getSession().getAttribute(ORIGINAL_URL_SESSION_KEY);
        LOG.debug("Will redirect to [{}] after successful authentication", originalSourceUrl);
        context.getResponse().sendRedirect(originalSourceUrl);
        context.setFinished(true);
    }

    private DocumentReference getOrCreateUserIfNeeded(Saml2XwikiAttributes attributes) throws XWikiException {
        final DocumentReference userReference = getXWikiUsername(attributes.nameID, attributes.xwikiAttributes);

        final String wikiId = context.getWikiId();
        try {
            // Switch to main wiki to force users to be global users
            context.setWikiId(context.getMainXWiki());

            if (context.getWiki().exists(userReference, context))
                return syncUserFields(attributes.xwikiAttributes, userReference);

            return createUser(attributes.xwikiAttributes, attributes.nameID, userReference);
        } finally {
            context.setWikiId(wikiId);
        }
    }

    private DocumentReference getXWikiUsername(String nameID,
                                               Map<String, String> xwikiAttributes)
            throws XWikiException
    {
        final Optional<String> validUserName = findUser(nameID);

        if (validUserName.isPresent()) {
            LOG.debug("Found XWiki User [{}]", validUserName.get());
            return getUserReferenceForName(validUserName.get());
        }
        final String generatedUserName = generateValidUserName(nameID, xwikiAttributes);
        LOG.debug("Generated XWiki User [{}]", generatedUserName);

        return getUserReferenceForName(generatedUserName);
    }

    private Optional<String> findUser(String nameID) throws XWikiException {
        final String sql =
                "SELECT DISTINCT " +
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
        if (list.isEmpty())
            return Optional.empty();

        final String userName = list.get(0);
        if (userName == null)
            throw new XWikiException(
                    MODULE_XWIKI_PLUGINS,
                    ERROR_XWIKI_APP_VALIDATE_USER,
                    "Unexpected error, user found, but with name is null");
        return Optional.of(userName);
    }

    private DocumentReference getUserReferenceForName(String validUserName) {
        return this.currentMixedDocumentReferenceResolver.resolve(validUserName, PROFILE_PARENT);
    }

    private String generateValidUserName(String nameID, Map<String, String> xwikiAttributes) throws XWikiException {
        final String validUserName;
        LOG.debug("Did not find XWiki User. Generating it.");
        final String userName = generateXWikiUsername(xwikiAttributes);
        if (userName.isEmpty())
            throw new XWikiException(
                    MODULE_XWIKI_PLUGINS,
                    ERROR_XWIKI_USER_CREATE,
                    "Could not generate a username for user " + nameID);

        validUserName = context.getWiki().getUniquePageName("XWiki", userName, context);
        LOG.debug("Generated XWiki User Name [{}]", validUserName);
        return validUserName;
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

    private DocumentReference syncUserFields(Map<String, String> xwikiAttributes,
                                             DocumentReference userReference)
            throws XWikiException {

        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final BaseObject userObj = userDoc.getXObject(USER_XCLASS);
        boolean updated = false;

        for (Map.Entry<String, String> entry : xwikiAttributes.entrySet()) {
            final String field = entry.getKey();
            final String newValue = entry.getValue();
            final String currentValue = getUserProperty(userObj, field);

            if (Objects.equals(newValue, currentValue))
                continue;

            userObj.set(field, newValue, context);
            updated = true;
        }

        if (updated) {
            context.getWiki().saveDocument(userDoc, context);
            LOG.info("User [{}] has been successfully updated", userReference);
        }
        return userReference;
    }

    private String getUserProperty(BaseObject userObj, String field) throws XWikiException {
        final BaseProperty<?> prop = (BaseProperty<?>) userObj.get(field);
        return (prop == null || prop.getValue() == null) ? null : prop.getValue().toString();
    }

    private DocumentReference createUser(Map<String, String> xwikiAttributes,
                                         String nameID,
                                         DocumentReference userReference)
            throws XWikiException {
        LOG.info("Will create new user [{}]", userReference);
        final Map<String, String> newXwikiAttributes = new HashMap<>(xwikiAttributes);
        newXwikiAttributes.put("active", "1");

        final String content = "{{include document=\"XWiki.XWikiUserSheet\"/}}";

        final int result = context.getWiki().createUser(
                userReference.getName(),
                newXwikiAttributes,
                PROFILE_PARENT,
                content,
                Syntax.XWIKI_2_1,
                "edit",
                context);

        if (result < 0) {
            LOG.error("Failed to create user [{}] with code [{}]", userReference, result);
            throw new XWikiException(
                    MODULE_XWIKI_PLUGINS,
                    ERROR_XWIKI_USER_CREATE,
                    "XWiki failed to create user [" + nameID + "]. Error code [" + result + "]");
        }

        associateSamlUserWithXwikiUser(nameID, userReference);

        LOG.info("User [{}] has been successfully created", userReference);
        return userReference;
    }

    private void associateSamlUserWithXwikiUser(String nameID, DocumentReference userReference) throws XWikiException {
        final XWikiDocument userDoc = context.getWiki().getDocument(userReference, context);
        final BaseObject samlIdObject = userDoc.newXObject(SAML_XCLASS, context);
        @SuppressWarnings("rawtypes")
        final BaseProperty samlIdProp = new StringClass().fromString(nameID);
        samlIdProp.setOwnerDocument(samlIdObject.getOwnerDocument());
        samlIdObject.safeput(SAML_ID_XPROPERTY_NAME, samlIdProp);
        context.getWiki().saveDocument(userDoc, context);
    }
}
