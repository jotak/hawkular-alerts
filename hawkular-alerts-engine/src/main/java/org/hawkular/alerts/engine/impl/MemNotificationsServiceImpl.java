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
package org.hawkular.alerts.engine.impl;

import org.hawkular.alerts.api.model.notification.Notification;
import org.hawkular.alerts.api.services.NotificationsService;
import org.hawkular.alerts.api.services.NotifierListener;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.jboss.logging.Logger;

import javax.ejb.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A memory implementation of {@link org.hawkular.alerts.api.services.NotificationsService}.
 * It is intended only for early prototype phases.
 * It will be replaced for a proper implementation based on a persistence repository.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Singleton
public class MemNotificationsServiceImpl implements NotificationsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(MemNotificationsServiceImpl.class);

    Queue<Notification> pending = new ConcurrentLinkedDeque<Notification>();
    List<NotifierListener> listeners = new CopyOnWriteArrayList<NotifierListener>();

    public MemNotificationsServiceImpl() {
        log.debugf("Creating instance.");
    }

    @Override
    public void send(Notification notification) {
        if (notification == null || notification.getNotifierId() == null || notification.getNotifierId().isEmpty()) {
            throw new IllegalArgumentException("Notification must be not null");
        }
        pending.add(notification);

        /*
            In this implementation we invoke listeners as soon as we receive an event.
            This can be modified per implementation basis adding asynchronously behaviour at this level.
         */
        for (NotifierListener listener : listeners) {
            listener.process(notification);
        }
    }

    @Override
    public void deregister(String notifierId) {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }

        /*
            In this implementation we invoke listeners as soon as we receive an event.
            This can be modified per implementation basis adding asynchronously behaviour at this level.
         */
        for (NotifierListener listener : listeners) {
            listener.unregister(notifierId);
        }

    }

    @Override
    public void register(String notifierId, Map<String, String> properties) {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (!properties.containsValue("NotifierType")) {
            throw new IllegalArgumentException("Properties must contain a NotifierType property");
        }

        /*
            In this implementation we invoke listeners as soon as we receive an event.
            This can be modified per implementation basis adding asynchronously behaviour at this level.
         */
        for (NotifierListener listener : listeners) {
            listener.register(notifierId, properties);
        }
    }

    @Override
    public void addListener(NotifierListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("NotifierListener must not be null");
        }
        listeners.add(listener);
        msgLog.infoNotifierListenerRegistered(listener.toString());
    }
}
