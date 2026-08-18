package sh.aelion.aeperm.common.service;

import sh.aelion.aeperm.api.ContextSet;

import java.util.UUID;

public interface ContextProvider {

    ContextSet current(UUID uuid);

    default ContextSet empty() {
        return ContextSet.empty();
    }
}
