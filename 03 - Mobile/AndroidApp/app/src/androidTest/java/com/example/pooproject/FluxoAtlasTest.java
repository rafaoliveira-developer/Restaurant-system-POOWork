package com.example.pooproject;

import android.os.Bundle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FluxoAtlasTest {
    private String server;
    private String broker;
    private String password;

    @Test public void createsOrdersFromTheWaiterScreen() throws Exception {
        Bundle settings = InstrumentationRegistry.getArguments();
        server = settings.getString("atlasApi");
        broker = settings.getString("atlasMqtt");
        password = settings.getString("atlasPassword");
        org.junit.Assume.assumeTrue(server != null && broker != null && password != null);

        String suffix = UUID.randomUUID().toString().substring(0,6);
        String table = "Mesa Android " + suffix;
        String product = "Chá Android " + suffix;
        String admin = login("gerencia");
        long tableId = ((JSONObject)api("tables",admin,new JSONObject().put("label",table).put("area","Validação Android"))).getLong("id");
        api("products",admin,new JSONObject().put("name",product).put("category","Bebidas").put("price",8).put("station","BAR"));

        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            enter(R.id.campoCodigo,"garcom");
            enter(R.id.campoSenha,password);
            enter(R.id.campoServidor,server);
            enter(R.id.campoBroker,broker);
            enter(R.id.campoSenhaMqtt,password);
            onView(withId(R.id.botaoEntrar)).perform(scrollTo(),click());
            await(() -> {
                onView(withText(containsString(table))).perform(scrollTo(),click());
                return true;
            });
            await(() -> {
                onView(withId(R.id.botaoCardapio)).check(matches(isEnabled())).perform(click());
                return true;
            });
            onView(withId(R.id.campoPesquisa)).perform(replaceText(product),closeSoftKeyboard());
            await(() -> {
                onView(withContentDescription("Adicionar " + product)).perform(scrollTo(),click());
                return true;
            });
            onView(withId(R.id.botaoRevisar)).check(matches(withText(containsString("8,00")))).perform(click());
            onView(withId(R.id.revisaoEnviar)).perform(click());

            String waiterToken = login("garcom");
            JSONObject order = await(() -> {
                JSONArray rows = (JSONArray)api("orders",waiterToken,null);
                for (int i=0;i<rows.length();i++) if (rows.getJSONObject(i).getLong("table_id")==tableId) return rows.getJSONObject(i);
                return null;
            });
            String id = order.getString("id");
            sendConfirmation(id,order.getLong("sequence"));
            await(() -> {
                onView(withId(R.id.textoSelecao)).check(matches(withText(containsString("Confirmado"))));
                return true;
            });
            onView(withId(R.id.botaoRevisar)).perform(click());
            onView(withText("Concluir")).perform(click());
            onView(withId(R.id.botaoVoltarComanda)).perform(click());
            androidx.test.espresso.Espresso.pressBack();
            onView(withId(R.id.botaoHistorico)).perform(click());
            await(() -> {
                onView(withText(containsString("PED-" + order.getLong("sequence")))).check(matches(isDisplayed()));
                return true;
            });
            assertEquals("11",((JSONObject)api("orders/" + id,waiterToken,null)).getString("code"));
        }
    }

    private void enter(int id,String value) {
        onView(withId(id)).perform(scrollTo(),replaceText(value),closeSoftKeyboard());
    }

    private String login(String user) throws Exception {
        return ((JSONObject)api("login",null,new JSONObject().put("username",user).put("password",password))).getString("token");
    }

    private Object api(String route,String token,JSONObject request) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(server + "/api/v1/" + route).openConnection();
        connection.setConnectTimeout(4_000);
        connection.setReadTimeout(5_000);
        try {
            if (token != null) connection.setRequestProperty("Authorization","Bearer " + token);
            if (request != null) {
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type","application/json");
                try (OutputStream output = connection.getOutputStream()) { output.write(request.toString().getBytes(StandardCharsets.UTF_8)); }
            }
            assertEquals("API " + route,200,connection.getResponseCode());
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream data = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[4096];
                int length;
                while ((length = input.read(bytes)) != -1) data.write(bytes,0,length);
                return new JSONTokener(data.toString("UTF-8")).nextValue();
            }
        } finally { connection.disconnect(); }
    }

    private void sendConfirmation(String id,long sequence) throws Exception {
        MqttClient mqtt = new MqttClient(broker,"atlas-it-mobile-bar-" + UUID.randomUUID(),new MemoryPersistence());
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName("bar");
            options.setPassword(password.toCharArray());
            options.setConnectionTimeout(4);
            mqtt.connect(options);
            JSONObject message = new JSONObject().put("event","CONFIRMABAR").put("version",1)
                    .put("orderId",id).put("sequence",sequence).put("status","OK").put("token",login("bar"));
            mqtt.publish("atlas/v1/commands/bar/CONFIRMABAR",message.toString().getBytes(StandardCharsets.UTF_8),1,false);
        } finally {
            if (mqtt.isConnected()) mqtt.disconnect();
            mqtt.close();
        }
    }

    private static <T> T await(Callable<T> check) throws Exception {
        Throwable last = null;
        for (int retry=0;retry<54;retry++) {
            try {
                T result = check.call();
                if (result != null) return result;
            } catch (AssertionError | androidx.test.espresso.NoMatchingViewException | androidx.test.espresso.PerformException error) { last = error; }
            Thread.sleep(225);
        }
        throw new AssertionError("Fluxo Android não alcançou o estado esperado.",last);
    }
}
