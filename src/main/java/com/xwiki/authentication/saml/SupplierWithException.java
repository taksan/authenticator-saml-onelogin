package com.xwiki.authentication.saml;

@FunctionalInterface
public interface SupplierWithException <T, E extends Exception> {
    T execute() throws E;
}
