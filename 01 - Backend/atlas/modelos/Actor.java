package atlas.modelos;

public record Actor(long id, String username, String name, String role) {
    public void require(String... roles) {
        for (String allowed : roles) {
            if (role.equals(allowed)) return;
        }
        throw new Problem(403, "Acesso negado para o perfil " + role);
    }
}
