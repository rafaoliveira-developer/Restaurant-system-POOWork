package atlas.pedidos;

import java.util.Map;

public record Consolidation(String status, String code) {
    public static Consolidation from(Map<String,String> stations) {
        if (stations.isEmpty() || stations.values().stream().anyMatch(s -> !s.equals("OK") && !s.equals("ERRO")))
            return new Consolidation("PENDENTE",null);
        boolean kitchen = !"ERRO".equals(stations.get("COZINHA"));
        boolean bar = !"ERRO".equals(stations.get("BAR"));
        long successes = stations.values().stream().filter("OK"::equals).count();
        String status = successes == stations.size() ? "CONFIRMADO" : successes == 0 ? "ERRO" : "PARCIAL";
        return new Consolidation(status,(kitchen ? "1" : "0") + (bar ? "1" : "0"));
    }
}
