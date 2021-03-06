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

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xwiki.authentication.saml.samlauth.SamlAuthConfig;
import com.xwiki.authentication.saml.samlauth.Saml2XWikiAttributes;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_APP_VALIDATE_USER;
import static com.xpn.xwiki.XWikiException.ERROR_XWIKI_USER_CREATE;
import static com.xpn.xwiki.XWikiException.MODULE_XWIKI_PLUGINS;
import static com.xwiki.authentication.saml.samlauth.SamlAuthenticator.PROFILE_PARENT;
import static java.util.Arrays.asList;

public class XWikiUserManager {
    public static final EntityReference SAML_XCLASS = new EntityReference("SAMLAuthClass", EntityType.DOCUMENT,
            new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));
    public static final EntityReference USER_XCLASS = PROFILE_PARENT;
    public static final String SAML_ID_XPROPERTY_NAME = "nameid";
    private static final Logger LOG = LoggerFactory.getLogger(XWikiUserManager.class);
    private final SamlAuthConfig authConfig;
    private final EntityReferenceSerializer<String> compactStringEntityReferenceSerializer;
    private final DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver;

    public XWikiUserManager(SamlAuthConfig authConfig,
                            EntityReferenceSerializer<String> compactStringEntityReferenceSerializer,
                            DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver) {
        this.authConfig = authConfig;
        this.compactStringEntityReferenceSerializer = compactStringEntityReferenceSerializer;
        this.currentMixedDocumentReferenceResolver = currentMixedDocumentReferenceResolver;
    }

    public DocumentReference getOrCreateUserIfNeeded(XWikiContext context, Saml2XWikiAttributes attributes) throws XWikiException {
        return new UserManagerWithContextAndAttributes(context, attributes).getOrCreateUserIfNeeded();
    }

    class UserManagerWithContextAndAttributes {
        private final XWikiContext context;
        private final Saml2XWikiAttributes attributes;

        public UserManagerWithContextAndAttributes(XWikiContext context, Saml2XWikiAttributes attributes) {
            this.context = context;
            this.attributes = attributes;
        }

        public DocumentReference getOrCreateUserIfNeeded() throws XWikiException {
            final User user = getXWikiUserByNameID(attributes.nameID);

            final String wikiId = context.getWikiId();
            try {
                // Switch to main wiki to force users to be global users
                context.setWikiId(context.getMainXWiki());

                if (user.exists()) {
                    syncUserFields(user);
                    return user.getUserReference();
                }

                return createUserWithAttributes(user,attributes);
            } finally {
                context.setWikiId(wikiId);
            }
        }

        private DocumentReference createUserWithAttributes(User user, Saml2XWikiAttributes attributes) throws XWikiException {
            LOG.info("Will create new user [{}]", user);

            user.createUserWithAttributes(attributes.xwikiAttributes);

            LOG.info("User [{}] has been successfully created", user);
            return user.getUserReference();
        }

        private User getXWikiUserByNameID(String nameID)
                throws XWikiException{
            final Optional<String> validUserName = findUser(nameID);

            if (validUserName.isPresent()) {
                LOG.debug("Found XWiki User [{}]", validUserName.get());
                return new User(nameID, getUserReferenceForName(validUserName.get()), context);
            }
            final String generatedUserName = generateValidUserName(nameID);
            LOG.debug("Generated XWiki User [{}]", generatedUserName);

            return new User(nameID, getUserReferenceForName(generatedUserName), context);
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
                    asList(compactStringEntityReferenceSerializer.serialize(SAML_XCLASS),
                            SAML_ID_XPROPERTY_NAME, nameID), context);
            if (list.isEmpty())
                return Optional.empty();

            final String userName = list.get(0);
            if (userName == null)
                throw new XWikiException(
                        MODULE_XWIKI_PLUGINS,
                        ERROR_XWIKI_APP_VALIDATE_USER,
                        "Unexpected error, user found but name is null");
            return Optional.of(userName);
        }

        private DocumentReference getUserReferenceForName(String validUserName) {
            return currentMixedDocumentReferenceResolver.resolve(validUserName, PROFILE_PARENT);
        }

        private String generateValidUserName(String nameID) throws XWikiException {
            final String validUserName;
            LOG.debug("Did not find XWiki User. Generating it.");
            final String userName = generateXWikiUsername();
            if (userName.isEmpty())
                throw new XWikiException(
                        MODULE_XWIKI_PLUGINS,
                        ERROR_XWIKI_USER_CREATE,
                        "Could not generate a username for user " + nameID);

            validUserName = context.getWiki().getUniquePageName("XWiki", userName, context);
            LOG.debug("Generated XWiki User Name [{}]", validUserName);
            return validUserName;
        }

        private String generateXWikiUsername() {
            final StringBuilder userName = new StringBuilder();

            for (String field : authConfig.userNameRule) {
                String value = StringUtils.trim(attributes.xwikiAttributes.get(field));
                if (StringUtils.isBlank(value))
                    continue;

                if (authConfig.shouldCapitalizeUserNames)
                    value = StringUtils.capitalize(value);

                userName.append(value);
            }
            return userName.toString();
        }

        private void syncUserFields(User user)
                throws XWikiException {

            boolean updated = false;

            for (Map.Entry<String, String> entry : attributes.xwikiAttributes.entrySet()) {
                final String field = entry.getKey();
                final String newValue = entry.getValue();
                final String currentValue = user.getFieldValue(field);
                if (Objects.equals(newValue, currentValue))
                    continue;

                user.setFieldValue(field, newValue);
                updated = true;
            }

            if (updated) {
                user.save();
                LOG.info("User [{}] has been successfully updated", user.getUserReference());
            }
        }
    }
}
