package sh.aelion.aeperm.common.history;

public record Actor(String name, String source) {

    public static final Actor API = new Actor("api", "api");

    public static Actor command(String name) {
        return new Actor(name == null || name.isBlank() ? "console" : name, "command");
    }

    public static Actor api(String name) {
        return new Actor(name == null || name.isBlank() ? "api" : name, "api");
    }
}
