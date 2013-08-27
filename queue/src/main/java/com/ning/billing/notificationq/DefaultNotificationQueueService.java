/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.notificationq;

import javax.inject.Inject;

import org.skife.jdbi.v2.IDBI;

import com.ning.billing.notificationq.api.NotificationQueue;
import com.ning.billing.notificationq.api.NotificationQueueConfig;
import com.ning.billing.clock.Clock;


public class DefaultNotificationQueueService extends NotificationQueueServiceBase {

    @Inject
    public DefaultNotificationQueueService(final IDBI dbi, final Clock clock, final NotificationQueueConfig config) {
        super(clock, config, dbi);
    }

    @Override
    protected NotificationQueue createNotificationQueueInternal(final String svcName,
                                                                final String queueName,
                                                                final NotificationQueueHandler handler) {
        return new DefaultNotificationQueue(svcName, queueName, handler, dao, this, clock, config);
    }
}