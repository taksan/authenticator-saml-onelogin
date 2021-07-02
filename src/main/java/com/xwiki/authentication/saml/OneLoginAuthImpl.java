package com.xwiki.authentication.saml;

import com.onelogin.saml2.Auth;
import com.onelogin.saml2.exception.SettingsException;
import com.onelogin.saml2.settings.Saml2Settings;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class OneLoginAuthImpl implements OneLoginAuth {
    public Auth produce(Saml2Settings settings, HttpServletRequest request, HttpServletResponse response) throws SettingsException {
        return new Auth(settings, request, response);
    }
}
