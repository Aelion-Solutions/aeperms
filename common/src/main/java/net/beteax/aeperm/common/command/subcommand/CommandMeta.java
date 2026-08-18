package net.beteax.aeperm.common.command.subcommand;

public record CommandMeta(
        String version,
        String author,
        String website,
        boolean networkMode
) {
    public static CommandMeta of(String version, boolean networkMode) {
        return new CommandMeta(
                version == null || version.isBlank() ? "unknown" : version,
                "Variiuz",
                "beteax.net",
                networkMode
        );
    }
}
