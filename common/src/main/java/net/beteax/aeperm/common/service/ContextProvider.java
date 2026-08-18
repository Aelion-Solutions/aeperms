package net.beteax.aeperm.common.service;

import net.beteax.aeperm.api.ContextSet;

import java.util.UUID;

public interface ContextProvider {

    ContextSet current(UUID uuid);

    default ContextSet empty() {
        return ContextSet.empty();
    }
}
