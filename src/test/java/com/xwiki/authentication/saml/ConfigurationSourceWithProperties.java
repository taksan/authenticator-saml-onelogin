package com.xwiki.authentication.saml;

import org.apache.commons.lang3.NotImplementedException;
import org.xwiki.configuration.ConfigurationSource;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class ConfigurationSourceWithProperties implements ConfigurationSource {
    private Properties properties = new Properties();

    @Override
    public <T> T getProperty(String key, T defaultValue) {
        return (T) properties.getProperty(key, (String) defaultValue);
    }

    @Override
    public <T> T getProperty(String key, Class<T> valueClass) {
        throw new NotImplementedException();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(String key) {
        return (T) properties.getProperty(key);
    }

    @Override
    public List<String> getKeys() {
        throw new NotImplementedException();
    }

    @Override
    public boolean containsKey(String key) {
        return properties.containsKey(key);
    }

    @Override
    public boolean isEmpty() {
        return properties.isEmpty();
    }

    public void setFromProperties(Properties properties) {
        this.properties = properties;
    }
}
