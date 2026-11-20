package com.n0hana.echoes_server.infra.mqtt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import com.n0hana.echoes_server.device.DeviceRegistrationResult;

import tools.jackson.databind.ObjectMapper;

/**
 * O MqttClient é criado internamente; usamos mockConstruction (thread-local) e
 * chamamos os métodos privados na thread do teste, sem broker nem rede.
 */
class DeviceRegistrationMqttClientCoverageTests {

  private MqttProperties properties;
  private DeviceRegistrationMessageHandler handler;
  private DeviceRegistrationMqttClient mqtt;
  private ScheduledExecutorService executor;
  private MockedConstruction<MqttClient> construction;

  @BeforeEach
  void setUp() throws Exception {
    properties = new MqttProperties();
    properties.setReconnectDelaySeconds(7);
    handler = mock(DeviceRegistrationMessageHandler.class);
    mqtt = new DeviceRegistrationMqttClient(properties, handler, new ObjectMapper());
    executor = mock(ScheduledExecutorService.class);
  }

  @AfterEach
  void tearDown() {
    if (construction != null)
      construction.close();
    mqtt.stop();
  }

  // --- helpers de reflection ---

  private void set(String name, Object value) throws Exception {
    Field f = DeviceRegistrationMqttClient.class.getDeclaredField(name);
    f.setAccessible(true);
    f.set(mqtt, value);
  }

  private Object invoke(String name) throws Exception {
    Method m = DeviceRegistrationMqttClient.class.getDeclaredMethod(name);
    m.setAccessible(true);
    return m.invoke(mqtt);
  }

  private void markRunning() throws Exception {
    set("running", true);
    set("executor", executor);
  }

  private MqttClient connectedClient() throws Exception {
    MqttClient client = mock(MqttClient.class);
    when(client.isConnected()).thenReturn(true);
    set("client", client);
    return client;
  }

  private MqttCallbackExtended callback() throws Exception {
    return (MqttCallbackExtended) invoke("callback");
  }

  // --- lifecycle ---

  @Test
  @DisplayName("Propriedades do SmartLifecycle")
  void lifecycleMetadata() {
    assertTrue(mqtt.isAutoStartup());
    assertEquals(Integer.MAX_VALUE, mqtt.getPhase());
    assertFalse(mqtt.isRunning());
  }

  @Test
  @DisplayName("start() com MQTT desligado não inicia nada")
  void startDisabled() {
    properties.setEnabled(false);

    mqtt.start();

    assertFalse(mqtt.isRunning());
  }

  @Test
  @DisplayName("start() marca running, é idempotente e stop() encerra (broker URL inválida: sem rede)")
  void startIsIdempotentAndStopStops() {
    properties.setBrokerUrl("invalida");
    properties.setReconnectDelaySeconds(3600);

    mqtt.start();
    assertTrue(mqtt.isRunning());
    mqtt.start();
    assertTrue(mqtt.isRunning());

    mqtt.stop();
    assertFalse(mqtt.isRunning());
  }

  @Test
  @DisplayName("stop(Runnable) para e executa o callback")
  void stopWithCallback() throws Exception {
    boolean[] called = { false };
    markRunning();

    mqtt.stop(() -> called[0] = true);

    assertTrue(called[0]);
    assertFalse(mqtt.isRunning());
  }

  @Test
  @DisplayName("stop() encerra o executor e desconecta um cliente conectado")
  void stopDisconnectsConnectedClient() throws Exception {
    markRunning();
    MqttClient client = connectedClient();

    mqtt.stop();

    verify(executor).shutdownNow();
    verify(client).disconnect(1000);
    verify(client).close(true);
  }

  @Test
  @DisplayName("stop() força a desconexão de um cliente não conectado")
  void stopForcesDisconnectWhenNotConnected() throws Exception {
    MqttClient client = mock(MqttClient.class);
    when(client.isConnected()).thenReturn(false);
    set("client", client);

    mqtt.stop();

    verify(client).disconnectForcibly(0, 1000, false);
    verify(client, never()).disconnect(anyLong());
    verify(client).close(true);
  }

  @Test
  @DisplayName("stop() tolera falhas ao desconectar e ao fechar")
  void stopToleratesFailures() throws Exception {
    MqttClient client = connectedClient();
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION)).when(client).disconnect(anyLong());
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION)).when(client).close(anyBoolean());

    mqtt.stop();

    verify(client).close(true);
  }

  // --- connect ---

  @Test
  @DisplayName("Conecta criando o cliente com id único e opções sem credenciais")
  void connectsWithOptions() throws Exception {
    construction = mockConstruction(MqttClient.class, (mock, ctx) -> when(mock.isConnected()).thenReturn(false));
    markRunning();
    properties.setCleanSession(false);
    properties.setConnectionTimeoutSeconds(4);
    properties.setKeepAliveSeconds(30);

    invoke("connectWithRetry");

    assertEquals(1, construction.constructed().size());
    MqttClient created = construction.constructed().get(0);
    ArgumentCaptor<MqttConnectOptions> options = ArgumentCaptor.forClass(MqttConnectOptions.class);
    verify(created).connect(options.capture());
    verify(created).setCallback(any(MqttCallbackExtended.class));
    assertFalse(options.getValue().isAutomaticReconnect());
    assertFalse(options.getValue().isCleanSession());
    assertEquals(4, options.getValue().getConnectionTimeout());
    assertEquals(30, options.getValue().getKeepAliveInterval());
    assertEquals(null, options.getValue().getUserName());
  }

  @Test
  @DisplayName("Usa usuário e senha quando configurados")
  void connectsWithCredentials() throws Exception {
    construction = mockConstruction(MqttClient.class);
    markRunning();
    properties.setUsername("user");
    properties.setPassword("segredo-ficticio");

    invoke("connectWithRetry");

    ArgumentCaptor<MqttConnectOptions> options = ArgumentCaptor.forClass(MqttConnectOptions.class);
    verify(construction.constructed().get(0)).connect(options.capture());
    assertEquals("user", options.getValue().getUserName());
    assertArrayEquals("segredo-ficticio".toCharArray(), options.getValue().getPassword());
  }

  @Test
  @DisplayName("Usuário sem senha usa senha vazia")
  void connectsWithUserAndNullPassword() throws Exception {
    construction = mockConstruction(MqttClient.class);
    markRunning();
    properties.setUsername("user");
    properties.setPassword(null);

    invoke("connectWithRetry");

    ArgumentCaptor<MqttConnectOptions> options = ArgumentCaptor.forClass(MqttConnectOptions.class);
    verify(construction.constructed().get(0)).connect(options.capture());
    assertEquals(0, options.getValue().getPassword().length);
  }

  @Test
  @DisplayName("Usuário em branco é ignorado")
  void blankUserIgnored() throws Exception {
    construction = mockConstruction(MqttClient.class);
    markRunning();
    properties.setUsername("  ");

    invoke("connectWithRetry");

    ArgumentCaptor<MqttConnectOptions> options = ArgumentCaptor.forClass(MqttConnectOptions.class);
    verify(construction.constructed().get(0)).connect(options.capture());
    assertEquals(null, options.getValue().getUserName());
  }

  @Test
  @DisplayName("Não reconecta quando já conectado e reaproveita o cliente")
  void doesNotReconnectWhenAlreadyConnected() throws Exception {
    construction = mockConstruction(MqttClient.class, (mock, ctx) -> when(mock.isConnected()).thenReturn(true));
    markRunning();

    invoke("connectWithRetry");
    invoke("connectWithRetry");

    assertEquals(1, construction.constructed().size());
    verify(construction.constructed().get(0), never()).connect(any(MqttConnectOptions.class));
  }

  @Test
  @DisplayName("Não conecta quando parado")
  void doesNotConnectWhenStopped() throws Exception {
    construction = mockConstruction(MqttClient.class);

    invoke("connectWithRetry");

    assertTrue(construction.constructed().isEmpty());
  }

  @Test
  @DisplayName("MqttException ao conectar agenda nova tentativa")
  void mqttExceptionSchedulesRetry() throws Exception {
    construction = mockConstruction(MqttClient.class, (mock, ctx) -> doThrow(
        new MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR)).when(mock).connect(any(MqttConnectOptions.class)));
    markRunning();

    invoke("connectWithRetry");

    verify(executor).schedule(any(Runnable.class), eq(7L), eq(TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("Exceção genérica ao conectar agenda nova tentativa")
  void genericExceptionSchedulesRetry() throws Exception {
    construction = mockConstruction(MqttClient.class, (mock, ctx) -> doThrow(new IllegalStateException("boom"))
        .when(mock).connect(any(MqttConnectOptions.class)));
    markRunning();

    invoke("connectWithRetry");

    verify(executor).schedule(any(Runnable.class), eq(7L), eq(TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("Não agenda nova tentativa quando parado ou com executor encerrado")
  void noRescheduleWhenStoppedOrShutdown() throws Exception {
    construction = mockConstruction(MqttClient.class, (mock, ctx) -> doThrow(new IllegalStateException("x"))
        .when(mock).connect(any(MqttConnectOptions.class)));
    markRunning();
    when(executor.isShutdown()).thenReturn(true);

    invoke("connectWithRetry");

    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

    // sem executor
    set("executor", null);
    callback().connectionLost(new RuntimeException("x"));
    verify(executor, never()).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
  }

  // --- callback ---

  @Test
  @DisplayName("connectComplete inscreve no tópico de registro (conectado e reconectado)")
  void connectCompleteSubscribes() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    properties.setRegistrationTopic("register");
    properties.setQos(2);

    callback().connectComplete(false, "tcp://x");
    callback().connectComplete(true, "tcp://x");

    verify(client, org.mockito.Mockito.times(2)).subscribe("register", 2);
  }

  @Test
  @DisplayName("Falha ao inscrever agenda nova tentativa de inscrição")
  void subscribeFailureSchedulesRetry() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION)).when(client).subscribe(anyString(), anyInt());

    callback().connectComplete(false, "tcp://x");

    ArgumentCaptor<Runnable> retry = ArgumentCaptor.forClass(Runnable.class);
    verify(executor).schedule(retry.capture(), eq(7L), eq(TimeUnit.SECONDS));

    // a nova tentativa chama subscribe de novo
    retry.getValue().run();
    verify(client, org.mockito.Mockito.times(2)).subscribe(anyString(), anyInt());
  }

  @Test
  @DisplayName("Falha de inscrição sem executor não agenda nada")
  void subscribeFailureWithoutExecutor() throws Exception {
    set("running", true);
    MqttClient client = connectedClient();
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION)).when(client).subscribe(anyString(), anyInt());

    callback().connectComplete(false, "tcp://x");

    verify(client).subscribe(anyString(), anyInt());
  }

  @Test
  @DisplayName("Não se inscreve parado, sem cliente ou desconectado")
  void subscribeSkipped() throws Exception {
    MqttCallbackExtended cb = callback();

    cb.connectComplete(false, "x"); // parado

    set("running", true);
    cb.connectComplete(false, "x"); // sem cliente

    MqttClient client = mock(MqttClient.class);
    when(client.isConnected()).thenReturn(false);
    set("client", client);
    cb.connectComplete(false, "x"); // desconectado

    verify(client, never()).subscribe(anyString(), anyInt());
  }

  @Test
  @DisplayName("connectionLost agenda reconexão para MqttException e outras causas (inclusive nula)")
  void connectionLostSchedulesReconnect() throws Exception {
    markRunning();
    MqttCallbackExtended cb = callback();

    cb.connectionLost(new MqttException(MqttException.REASON_CODE_CONNECTION_LOST));
    cb.connectionLost(new RuntimeException("falha", new IllegalStateException("causa raiz")));
    cb.connectionLost(new RuntimeException((String) null));
    cb.connectionLost(null);

    verify(executor, org.mockito.Mockito.times(4)).schedule(any(Runnable.class), eq(7L), eq(TimeUnit.SECONDS));
  }

  @Test
  @DisplayName("deliveryComplete não faz nada")
  void deliveryCompleteNoop() throws Exception {
    callback().deliveryComplete(null);
  }

  @Test
  @DisplayName("messageArrived delega ao handler e publica o ack no tópico do dispositivo")
  void messageArrivedPublishesAck() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    properties.setQos(1);
    DeviceRegistrationResult result = DeviceRegistrationResult.rejected("AA:BB/CC", "inválido");
    when(handler.handle("{\"a\":1}")).thenReturn(result);

    callback().messageArrived("register", new MqttMessage("{\"a\":1}".getBytes(StandardCharsets.UTF_8)));

    ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
    verify(client).publish(eq("register/ack/AA:BB_CC"), message.capture());
    assertEquals(1, message.getValue().getQos());
    assertFalse(message.getValue().isRetained());
    String json = new String(message.getValue().getPayload(), StandardCharsets.UTF_8);
    assertTrue(json.contains("\"hardware_id\":\"AA:BB/CC\""));
    assertTrue(json.contains("\"status\":\"REJECTED\""));
  }

  @Test
  @DisplayName("messageArrived não publica o ack quando parado, sem cliente ou desconectado")
  void messageArrivedSkipsAckWhenUnavailable() throws Exception {
    DeviceRegistrationResult result = DeviceRegistrationResult.rejected("X", "m");
    when(handler.handle(anyString())).thenReturn(result);
    MqttCallbackExtended cb = callback();
    MqttMessage msg = new MqttMessage("p".getBytes(StandardCharsets.UTF_8));

    cb.messageArrived("register", msg); // parado

    set("running", true);
    cb.messageArrived("register", msg); // sem cliente

    MqttClient client = mock(MqttClient.class);
    when(client.isConnected()).thenReturn(false);
    set("client", client);
    cb.messageArrived("register", msg); // desconectado

    verify(client, never()).publish(anyString(), any(MqttMessage.class));
  }

  @Test
  @DisplayName("Falha ao publicar o ack não propaga exceção")
  void ackPublishFailureIsSwallowed() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION)).when(client)
        .publish(anyString(), any(MqttMessage.class));
    when(handler.handle(anyString())).thenReturn(DeviceRegistrationResult.rejected(null, "m"));

    callback().messageArrived("register", new MqttMessage("p".getBytes(StandardCharsets.UTF_8)));

    verify(client).publish(eq("register/ack/unknown"), any(MqttMessage.class));
  }

  @Test
  @DisplayName("Template de ack aceita {hardwareId}, {mac} e fallback quando vazio")
  void ackTopicTemplates() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    when(handler.handle(anyString())).thenReturn(DeviceRegistrationResult.rejected("AA", "m"));
    MqttMessage msg = new MqttMessage("p".getBytes(StandardCharsets.UTF_8));
    MqttCallbackExtended cb = callback();

    properties.setAckTopic("ack/{hardwareId}/{mac}/{hardware_id}");
    cb.messageArrived("register", msg);
    verify(client).publish(eq("ack/AA/AA/AA"), any(MqttMessage.class));

    properties.setAckTopic("  ");
    cb.messageArrived("register", msg);
    properties.setAckTopic(null);
    cb.messageArrived("register", msg);
    verify(client, org.mockito.Mockito.times(2)).publish(eq("register/ack/AA"), any(MqttMessage.class));
  }

  // --- publish ---

  @Test
  @DisplayName("publish envia mensagem com qos e retained e retorna true")
  void publishSuccess() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    properties.setQos(2);

    boolean sent = mqtt.publish("t", new byte[] { 1, 2 }, true);

    assertTrue(sent);
    ArgumentCaptor<MqttMessage> message = ArgumentCaptor.forClass(MqttMessage.class);
    verify(client).publish(eq("t"), message.capture());
    assertEquals(2, message.getValue().getQos());
    assertTrue(message.getValue().isRetained());
    assertArrayEquals(new byte[] { 1, 2 }, message.getValue().getPayload());
  }

  @Test
  @DisplayName("publish retorna false parado, sem cliente ou desconectado")
  void publishUnavailable() throws Exception {
    assertFalse(mqtt.publish("t", new byte[0], false)); // parado

    set("running", true);
    assertFalse(mqtt.publish("t", new byte[0], false)); // sem cliente

    MqttClient client = mock(MqttClient.class);
    when(client.isConnected()).thenReturn(false);
    set("client", client);
    assertFalse(mqtt.publish("t", new byte[0], false)); // desconectado
  }

  @Test
  @DisplayName("publish retorna false quando o broker falha")
  void publishFailure() throws Exception {
    markRunning();
    MqttClient client = connectedClient();
    doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION)).when(client)
        .publish(anyString(), any(MqttMessage.class));

    assertFalse(mqtt.publish("t", new byte[0], false));
  }

  // --- helpers estáticos ---

  @Test
  @DisplayName("topicSegment: nulo/vazio vira unknown e espaços são aparados")
  void topicSegmentEdgeCases() {
    assertEquals("unknown", DeviceRegistrationMqttClient.topicSegment(null));
    assertEquals("unknown", DeviceRegistrationMqttClient.topicSegment("  "));
    assertEquals("AA:BB", DeviceRegistrationMqttClient.topicSegment("  AA:BB "));
  }

  @Test
  @DisplayName("instanceClientId: nulo usa padrão e valor configurado é aparado")
  void instanceClientIdEdgeCases() {
    assertTrue(DeviceRegistrationMqttClient.instanceClientId(null).startsWith("echoes-server-"));
    assertTrue(DeviceRegistrationMqttClient.instanceClientId(" meu ").startsWith("meu-"));
  }
}
