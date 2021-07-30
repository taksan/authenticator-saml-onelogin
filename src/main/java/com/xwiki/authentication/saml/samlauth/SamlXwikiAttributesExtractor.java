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
package com.xwiki.authentication.saml.samlauth;

import com.onelogin.saml2.Auth;
import com.xwiki.authentication.saml.SamlAuthenticationHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.apache.commons.compress.utils.Sets.newHashSet;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

public class SamlXwikiAttributesExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(SamlAuthenticationHandler.class);

    private final SamlAuthConfig authConfig;

    public SamlXwikiAttributesExtractor(SamlAuthConfig authConfig) {
        this.authConfig = authConfig;
    }

    public Saml2XwikiAttributes extractXWikiAttributesFromSaml(Auth auth) {
        return new SamlXwikiAttributesExtractorWithAuth(auth).extractXWikiAttributesFromSaml();
    }

    class SamlXwikiAttributesExtractorWithAuth {
        private final Auth auth;

        public SamlXwikiAttributesExtractorWithAuth(Auth auth) {
            this.auth = auth;
        }

        Saml2XwikiAttributes extractXWikiAttributesFromSaml() {
            final Map<String, String> samlAttributes = extractSamlAttributes();

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

        private Map<String, String> extractSamlAttributes() {
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
            final Map<String, String> userPropertiesMapping = new HashMap<>();
            for (String fieldMapping : authConfig.fieldMapping) {
                final String[] fieldAndValue = fieldMapping.split("=");
                if (fieldAndValue.length != 2) {
                    LOG.error("Error parsing SAML fields_mapping attribute in xwiki.cfg: [{}]", fieldMapping);
                    continue;
                }
                final String xwikiPropertyName = fieldAndValue[0].trim();
                final String samlAttributeName = fieldAndValue[1].trim();

                userPropertiesMapping.put(samlAttributeName, xwikiPropertyName);
            }

            return userPropertiesMapping;
        }
    }
}
