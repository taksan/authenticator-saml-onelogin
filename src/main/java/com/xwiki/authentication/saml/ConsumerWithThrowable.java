package com.xwiki.authentication.saml;

@FunctionalInterface
public interface ConsumerWithThrowable<T, E extends Throwable> {
    void accept(T t) throws E;
}