package atlas.modelos;

public final class Problem extends RuntimeException {
    private final int status;

    public Problem(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
    public static Problem invalid(String message) { return new Problem(400, message); }
    public static Problem missing(String message) { return new Problem(404, message); }
    public static Problem conflict(String message) { return new Problem(409, message); }
}
