/*
 * Copyright 2010-2013 Ning, Inc.
 * Copyright 2014-2016 Groupon, Inc
 * Copyright 2014-2016 The Billing Project, LLC
 *
 * The Billing Project licenses this file to you under the Apache License, version 2.0
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

package org.killbill.commons.locker.memory;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.killbill.commons.locker.GlobalLock;
import org.killbill.commons.locker.GlobalLocker;
import org.killbill.commons.locker.GlobalLockerBase;
import org.killbill.commons.locker.ResetReentrantLockCallback;

public class MemoryGlobalLocker extends GlobalLockerBase implements GlobalLocker {

    private final Map<String, AtomicBoolean> locks = new ConcurrentHashMap<String, AtomicBoolean>();

    public MemoryGlobalLocker() {
        super(null, null, DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized boolean isFree(final String service, final String lockKey) {
        final String lockName = getLockName(service, lockKey);
        return isFree(lockName);
    }

    private synchronized Boolean isFree(final String lockName) {
        final AtomicBoolean lock = locks.get(lockName);
        return lock == null || !lock.get();
    }

    @Override
    protected synchronized GlobalLock doLock(final String lockName) {
        if (!isFree(lockName)) {
            return null;
        }

        if (locks.get(lockName) == null) {
            locks.put(lockName, new AtomicBoolean(true));
        } else {
            locks.get(lockName).set(true);
        }

        final GlobalLock lock = new GlobalLock() {
            @Override
            public void release() {
                if (lockTable.releaseLock(lockName)) {
                    locks.get(lockName).set(false);
                }
            }
        };

        lockTable.createLock(lockName, lock);

        return lock;
    }

    @Override
    protected GlobalLock getGlobalLock(final Connection connection, final String lockName, final ResetReentrantLockCallback resetCb) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected String getLockName(final String service, final String lockKey) {
        return service + "-" + lockKey;
    }
}
