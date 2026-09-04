package com.peoplefirst.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peoplefirst.agent.client.GenAiClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GenAiClientTest {

    private HttpServer stub;
    private String lastPath;
    private String lastBody;
    private int stubStatus = 200;
    private String stubBody = "{\"choices\": [{\"message\": {\"content\": \"hello\"}}]}";
    private GenAiClient client;

    @BeforeEach
    void setUp() throws Exception {
        stub = HttpServer.create(new InetSocketAddress(0), 0);
        stub.createContext("/", exchange -> {
            lastPath = exchange.getRequestURI().getPath();
            lastBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] out = stubBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(stubStatus, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        stub.start();
        client = new GenAiClient(new ObjectMapper());
        client.setEnabled(true);
        client.setApiKey("test-key-not-sk");
        client.setProvider("openai_compatible");
        client.setModel("test-model");
        client.setBaseUrl("http://localhost:" + stub.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        stub.stop(0);
    }

    @Test
    void postsToConfiguredBaseUrlChatCompletions() {
        Optional<String> reply = client.generateContent("sys", "hi");
        assertTrue(reply.isPresent());
        assertEquals("/chat/completions", lastPath);
        assertTrue(lastBody.contains("\"model\":\"test-model\""));

        // Trailing slash on base URL must still post to /chat/completions
        client.setBaseUrl("http://localhost:" + stub.getAddress().getPort() + "/");
        Optional<String> reply2 = client.generateContent("sys", "hi");
        assertTrue(reply2.isPresent());
        assertEquals("/chat/completions", lastPath);
    }
}
