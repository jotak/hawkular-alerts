/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.bus.api;

import java.util.Map;
import java.util.Set;

import org.hawkular.bus.common.AbstractMessage;

/**
 * Message sent from the action plugins architect to the alerts engine through the bus
 *
 * @author Lucas Ponce
 */
public class BusRegistrationMessage extends AbstractMessage {

    String actionPlugin;
    Set<String> propertyNames;
    Map<String, String> defaultProperties;

    public BusRegistrationMessage() {
    }

    public BusRegistrationMessage(String actionPlugin, Set<String> propertyNames,
                                  Map<String, String> defaultProperties) {
        this.actionPlugin = actionPlugin;
        this.propertyNames = propertyNames;
        this.defaultProperties = defaultProperties;
    }

    public String getActionPlugin() {
        return actionPlugin;
    }

    public void setActionPlugin(String actionPlugin) {
        this.actionPlugin = actionPlugin;
    }

    public Set<String> getPropertyNames() {
        return propertyNames;
    }

    public void setPropertyNames(Set<String> propertyNames) {
        this.propertyNames = propertyNames;
    }

    public Map<String, String> getDefaultProperties() {
        return defaultProperties;
    }

    public void setDefaultProperties(Map<String, String> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }
}
