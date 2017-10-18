// Copyright (c) [2017] Dell Inc. or its subsidiaries. All Rights Reserved.
package com.emc.ocopea.cfpsb;

import com.emc.microservice.Context;
import com.emc.microservice.singleton.ServiceLifecycle;
import reactor.core.Cancellation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FluxCancellationsManager implements ServiceLifecycle {

    private final Map<String, Cancellation> cancellations = new ConcurrentHashMap<>();

    @Override
    public void init(Context context) {
    }

    @Override
    public void shutDown() {
        cancellations.keySet().forEach(this::cancel);
    }

    public void add(String id, Cancellation cancellation) {
        cancellations.put(id, cancellation);
    }

    public void cancel(String id) {
        Cancellation cancellation = cancellations.remove(id);
        if (cancellation != null) {
            cancellation.dispose();
        }
    }
}
