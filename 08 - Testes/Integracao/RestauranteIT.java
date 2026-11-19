import atlas.cliente.Client;
import atlas.impressao.adaptadores.SimulatedPrinter;
import atlas.impressao.servicos.Printing;
import com.google.gson.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import static atlas.cliente.Client.text;

public final class RestauranteIT {
    private static final String URL = System.getenv("ATLAS_IT_URL");
    private static final String BROKER = System.getenv("ATLAS_IT_BROKER");
    private static final String SECRET = System.getenv("ATLAS_IT_PASSWORD");
    private static final Path OUTPUT = Path.of(System.getenv("ATLAS_IT_OUTPUT"));
    private static Client admin, waiter, bar, kitchen, cashier;
    private static long drink, food;
    private static int boxes;

    public static void main(String[] args) throws Exception {
        admin = Client.login(URL,"gerencia",SECRET);
        employee("garcom","GARCOM"); employee("bar","BAR"); employee("cozinha","COZINHA"); employee("caixa","CAIXA");
        waiter = Client.login(URL,"garcom",SECRET);
        bar = Client.login(URL,"bar",SECRET);
        kitchen = Client.login(URL,"cozinha",SECRET);
        cashier = Client.login(URL,"caixa",SECRET);
        denied(401,() -> Client.login(URL,"garcom","outra-senha-incorreta"));
        denied(403,() -> waiter.post("products",Map.of()));
        denied(403,() -> bar.get("queue/COZINHA"));
        denied(403,() -> waiter.get("payments"));
        long assignedTable = table();
        long waiterId = waiter.get("me").getAsJsonObject().get("id").getAsLong();
        denied(400,() -> admin.post("tabs/" + assignedTable + "/open",Map.of()));
        admin.post("tabs/" + assignedTable + "/open",Map.of("waiterId",waiterId));
        check(waiter.get("tabs/" + assignedTable).getAsJsonObject().has("tab"),"Comanda atribuída ao garçom");
        admin.post("tabs/" + assignedTable + "/cancel",Map.of());
        drink = product("Refrigerante","Bebidas","BAR",7);
        food = product("Hambúrguer","Pratos principais","COZINHA",30);
        denied(400,() -> admin.post("products",Map.of("name","Inválido","category","Teste","station","BAR","price",-3)));
        check(waiter.get("products").getAsJsonArray().size()==2,"Cardápio publicado");
        try (Device mobile = new Device("garcom","atlas/v1/users/garcom/CONFIRMAREPEDIDO");
             Device drinks = new Device("bar","atlas/v1/stations/BAR/ENVIABAR");
             Device meals = new Device("cozinha","atlas/v1/stations/COZINHA/ENVIACOZINHA")) {
            scenario(mobile,drinks,meals,null,"OK","11","CONFIRMADO");
            scenario(mobile,drinks,meals,"OK",null,"11","CONFIRMADO");
            scenario(mobile,drinks,meals,"OK","OK","11","CONFIRMADO");
            scenario(mobile,drinks,meals,"ERRO","OK","01","PARCIAL");
            scenario(mobile,drinks,meals,"OK","ERRO","10","PARCIAL");
            scenario(mobile,drinks,meals,"ERRO","ERRO","00","ERRO");
            printerUnavailable(mobile,meals);
            recovery(mobile,meals);
            malformedAndRejected(mobile);
        }
        check(admin.get("history").getAsJsonArray().size() > 30,"Histórico de produção e fechamento");
        check(admin.get("reports").getAsJsonObject().getAsJsonObject("summary").get("payments").getAsInt()==boxes,
                "Relatório baseado nos pagamentos persistidos");
        check(cashier.post("cash-closings",Map.of()).getAsJsonObject().get("payment_count").getAsInt()==boxes,
                "Fechamento do caixa");
        denied(409,() -> cashier.post("cash-closings",Map.of()));
        System.out.println("PASSOU: A–H, REST, permissões, idempotência, rejeição, relatório e caixa.");
    }

    private static void employee(String name,String role) throws Exception {
        admin.post("employees",Map.of("username",name,"name",name,"role",role,"password",SECRET));
    }

    private static long product(String name,String category,String station,int price) throws Exception {
        return admin.post("products",Map.of("name",name,"category",category,"station",station,"price",price))
                .getAsJsonObject().get("id").getAsLong();
    }

    private static long table() throws Exception {
        return admin.post("tables",Map.of("label","Mesa " + UUID.randomUUID().toString().substring(0,8),"area","Salão"))
                .getAsJsonObject().get("id").getAsLong();
    }

    private static void scenario(Device mobile,Device drinks,Device meals,String kitchenState,String barState,
                                 String code,String expected) throws Exception {
        long table = table();
        String id = UUID.randomUUID().toString();
        List<Map<String,Object>> items = new ArrayList<>();
        if (kitchenState != null) items.add(item(food,2));
        if (barState != null) items.add(item(drink,3));
        JsonObject command = order(id,table,items);
        mobile.publish("NOVOPEDIDO",command);
        mobile.publish("NOVOPEDIDO",command);
        JsonObject accepted = mobile.await(id,row -> text(row,"status").equals("PENDENTE"));
        long sequence = accepted.get("sequence").getAsLong();
        if (kitchenState != null) {
            JsonObject dispatch = meals.await(id,row -> true);
            check(dispatch.getAsJsonArray("items").size()==1,"Somente itens de cozinha");
            check("Hambúrguer".equals(text(dispatch.getAsJsonArray("items").get(0).getAsJsonObject(),"name")),"Item da cozinha");
            String job = text(dispatch,"printId");
            runPrint(job,false);
            denied(409,() -> kitchen.post("prints/" + job + "/claim",Map.of()));
            confirm(meals,kitchen,id,sequence,kitchenState);
            confirm(meals,kitchen,id,sequence,"PREPARANDO"); // atrasada
        }
        if (barState != null) {
            var dispatch = drinks.await(id,row -> true);
            check("Refrigerante".equals(text(dispatch.getAsJsonArray("items").get(0).getAsJsonObject(),"name")),"Somente itens do bar");
            confirm(drinks,bar,id,sequence,barState);
            confirm(drinks,bar,id,sequence,barState); // duplicada
        }
        JsonObject result = mobile.await(id,row -> expected.equals(text(row,"status")));
        check(code.equals(text(result,"code")),"Código consolidado " + code);
        var persisted = waiter.get("orders/" + id).getAsJsonObject();
        check(code.equals(text(persisted,"code")),"Resultado persistido");
        check(persisted.getAsJsonArray("items").size()==items.size(),"Pedido idempotente");
        check(persisted.getAsJsonArray("stations").size()==items.size(),"Estações realmente envolvidas");
        waiter.post("tabs/" + table + "/request",Map.of());
        int total = (kitchenState != null && kitchenState.equals("OK") ? 60 : 0) +
                (barState != null && barState.equals("OK") ? 21 : 0);
        var tab = cashier.get("tabs/" + table).getAsJsonObject();
        check(tab.get("total").getAsInt()==total,"Consumo confirmado");
        denied(403,() -> waiter.post("tabs/" + table + "/pay",Map.of("method","PIX")));
        denied(409,() -> cashier.post("tabs/" + table + "/pay",Map.of("method","PIX","expectedTotal",total+1)));
        cashier.post("tabs/" + table + "/pay",Map.of("method","PIX","expectedTotal",total));
        check(!admin.get("tabs/" + table).getAsJsonObject().has("tab"),"Mesa liberada");
        boxes++;
        System.out.println("PASSOU: cozinha=" + kitchenState + " bar=" + barState + " resultado=" + code);
    }

    private static void printerUnavailable(Device mobile,Device meals) throws Exception {
        long table = table();
        String id = UUID.randomUUID().toString();
        mobile.publish("NOVOPEDIDO",order(id,table,List.of(item(food,1))));
        var dispatch = meals.await(id,row -> true);
        String job = text(dispatch,"printId");
        runPrint(job,true);
        kitchen.post("prints/" + job + "/retry",Map.of());
        runPrint(job,false);
        String copy = UUID.randomUUID().toString();
        kitchen.post("prints/" + job + "/reprint",Map.of("copyId",copy));
        kitchen.post("prints/" + job + "/reprint",Map.of("copyId",copy));
        runPrint(copy,false);
        check(Files.readString(OUTPUT.resolve(copy + ".txt")).startsWith("REIMPRESSÃO"),"Reimpressão identificada");
        confirm(meals,kitchen,id,dispatch.get("sequence").getAsLong(),"OK");
        mobile.await(id,row -> "CONFIRMADO".equals(text(row,"status")));
        System.out.println("PASSOU: impressão indisponível, retentativa e cópia idempotente");
    }

    private static void recovery(Device mobile,Device meals) throws Exception {
        long table = table();
        String id = UUID.randomUUID().toString();
        meals.disconnect();
        mobile.publish("NOVOPEDIDO",order(id,table,List.of(item(food,1))));
        var accepted = mobile.await(id,row -> "PENDENTE".equals(text(row,"status")));
        long sequence = accepted.get("sequence").getAsLong();
        check(kitchen.get("queue/COZINHA").getAsJsonArray().size() > 0,"Fila recuperável via REST");
        waiter.post("tabs/" + table + "/request",Map.of());
        denied(409,() -> cashier.post("tabs/" + table + "/pay",Map.of("method","PIX")));
        meals.connect();
        var delivered = meals.await(id,row -> true);
        runPrint(text(delivered,"printId"),false);
        confirm(meals,kitchen,id,sequence,"OK");
        mobile.await(id,row -> "CONFIRMADO".equals(text(row,"status")));
        System.out.println("PASSOU: terminal desconectado e entrega MQTT recuperada");
    }

    private static void malformedAndRejected(Device mobile) throws Exception {
        mobile.client.publish("atlas/v1/commands/garcom/NOVOPEDIDO","{quebrado".getBytes(StandardCharsets.UTF_8),1,false);
        long table = table();
        String id = UUID.randomUUID().toString();
        admin.put("products/" + drink,Map.of("name","Novo valor","category","Bebidas","station","BAR","price",9,"available",false));
        mobile.publish("NOVOPEDIDO",order(id,table,List.of(item(drink,1))));
        mobile.await(id,row -> "REJEITADO".equals(text(row,"status")));
        check("REJEITADO".equals(text(waiter.get("orders/" + id).getAsJsonObject(),"status")),"Rejeição persistida");
        check(!waiter.get("tabs/" + table).getAsJsonObject().has("tab"),"Falha reverte abertura da comanda");
        admin.put("products/" + drink,Map.of("name","Novo valor","category","Bebidas","station","BAR","price",9));
        mobile.publish("NOVOPEDIDO",order(id,table,List.of(item(drink,1))));
        mobile.await(id,row -> "REJEITADO".equals(text(row,"status")));
        check(!waiter.get("tabs/" + table).getAsJsonObject().has("tab"),"Redelivery não recria pedido rejeitado");
    }

    private static void runPrint(String id,boolean fail) throws Exception {
        var claim = kitchen.post("prints/" + id + "/claim",Map.of()).getAsJsonObject();
        var output = new Printing(fail ? (uuid,text) -> { throw new java.io.IOException("Impressora indisponível"); }
                : new SimulatedPrinter(OUTPUT));
        var result = output.submit(UUID.fromString(id),text(claim,"content"));
        check(result.status().equals(fail ? "FALHA" : "IMPRESSO"),"Resultado do adaptador");
        kitchen.post("prints/" + id + "/result",Map.of("claimId",text(claim,"claim_id"),
                "status",result.status(),"error",result.error()));
        if (!fail) check(Files.isRegularFile(OUTPUT.resolve(id + ".txt")),"Comprovante simulado");
    }

    private static Map<String,Object> item(long id,int amount) {
        return Map.of("productId",id,"quantity",amount,"notes","Observação de teste");
    }

    private static JsonObject order(String id,long table,List<Map<String,Object>> items) {
        return waiter.command("NOVOPEDIDO",Map.of("orderId",id,"idempotencyKey",id,"tableId",table,"items",items));
    }

    private static void confirm(Device device,Client operator,String id,long sequence,String status) throws Exception {
        String event = operator.role.equals("BAR") ? "CONFIRMABAR" : "CONFIRMACOZINHA";
        device.publish(event,operator.command(event,Map.of("orderId",id,"sequence",sequence,"status",status,
                "error",status.equals("ERRO") ? "Falha operacional de teste" : "")));
    }

    private static void denied(int status,Action operation) throws Exception {
        try { operation.run(); } catch (Client.ApiError ex) {
            if (ex.status == status) return;
            throw ex;
        }
        throw new AssertionError("Resposta esperada HTTP " + status);
    }

    private static void check(boolean condition,String description) {
        if (!condition) throw new AssertionError(description);
    }

    @FunctionalInterface private interface Action { void run() throws Exception; }

    private static final class Device implements AutoCloseable {
        final MqttClient client;
        final MqttConnectOptions options = new MqttConnectOptions();
        final String username;
        final BlockingQueue<JsonObject> messages = new LinkedBlockingQueue<>();

        Device(String username,String topic) throws Exception {
            this.username = username;
            client = new MqttClient(BROKER,"it-" + username + "-" + UUID.randomUUID(),new MemoryPersistence());
            options.setCleanSession(false);
            options.setUserName(username);
            options.setPassword(SECRET.toCharArray());
            client.setCallback(new MqttCallback() {
                public void connectionLost(Throwable cause) {}
                public void deliveryComplete(IMqttDeliveryToken token) {}
                public void messageArrived(String arrived,MqttMessage message) {
                    messages.add(JsonParser.parseString(new String(message.getPayload(),StandardCharsets.UTF_8)).getAsJsonObject());
                }
            });
            connect();
            client.subscribe(topic,1);
        }

        void connect() throws MqttException { client.connect(options); }
        void disconnect() throws MqttException { client.disconnect(); }

        void publish(String event,JsonObject payload) throws MqttException {
            client.publish("atlas/v1/commands/" + username + "/" + event,
                    payload.toString().getBytes(StandardCharsets.UTF_8),1,false);
        }

        JsonObject await(String id,Predicate<JsonObject> match) throws InterruptedException {
            long until = System.nanoTime() + Duration.ofSeconds(14).toNanos();
            while (System.nanoTime() < until) {
                JsonObject next = messages.poll(350,TimeUnit.MILLISECONDS);
                if (next != null && id.equals(text(next,"orderId")) && match.test(next)) return next;
            }
            throw new AssertionError("Mensagem não recebida por " + username + " para " + id);
        }

        @Override public void close() throws MqttException {
            if (client.isConnected()) disconnect();
            client.close();
        }
    }
}
