package sh.aelion.aeperm.common.service;

import sh.aelion.aeperm.api.ContextSet;

import java.util.UUID;

public final class StaticContextProvider implements ContextProvider {

    private final ContextSet base;

    public StaticContextProvider(String serverId) {
        this.base = ContextSet.builder().server(serverId).build();
    }

    public StaticContextProvider(ContextSet base) {
        this.base = base;
    }

    @Override
    public ContextSet current(UUID uuid) {
        return base;
    }

    @Override
    public ContextSet empty() {
        return ContextSet.empty();
    }
}
