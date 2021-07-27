package com.xwiki.authentication.saml;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.user.api.XWikiUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import static com.xwiki.authentication.saml.SamlAuthenticator.PROFILE_PARENT;

public class AuthenticatedUserHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticatedUserHandler.class);
    private XWikiContext context;
    private DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver;

    public AuthenticatedUserHandler(XWikiContext context, DocumentReferenceResolver<String> currentMixedDocumentReferenceResolver) {
        this.context = context;
        this.currentMixedDocumentReferenceResolver = currentMixedDocumentReferenceResolver;
    }

    XWikiUser handle(String samlUserName) {
        LOG.debug("User [{}] already logged in the session.", samlUserName);
        return new XWikiUser(getUserReferenceForName(samlUserName), context.isMainWiki());
    }

    private DocumentReference getUserReferenceForName(String validUserName) {
        return this.currentMixedDocumentReferenceResolver.resolve(validUserName, PROFILE_PARENT);
    }
}
