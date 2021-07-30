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
package com.xwiki.authentication.saml.testsupport;

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
