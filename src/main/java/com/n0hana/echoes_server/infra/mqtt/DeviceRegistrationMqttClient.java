package com.n0hana.echoes_server.infra.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import com.n0hana.echoes_server.device.DeviceRegistrationResult;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente MQTT responsável por receber o autocadastro dos dispositivos.
 *
 * <p>
 * O cliente se inscreve no tópico {@code register}. Ao receber um payload, ele
 * delega a validação para {@link DeviceRegistrationMessageHandler} e publica o
 * resultado em {@code register/ack/{hardware_id}}. Se o broker estiver indisponível na
 * inicialização, a aplicação continua subindo e o cliente tenta reconectar em
 * segundo plano.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class DeviceRegistrationMqttClient implements SmartLifecycle {

  private final MqttProperties properties;
  private final DeviceRegistrationMessageHandler messageHandler;
  private final ObjectMapper objectMapper;

  private final Object lifecycleMonitor = new Object();

  private volatile MqttClient client;
  private volatile ScheduledExecutorService executor;
  private volatile boolean running;

  @Override
  public void start() {
    if (!properties.isEnabled())
      return;

    synchronized (lifecycleMonitor) {
      if (running)
        return;

      running = true;
      executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mqtt-device-registration");
        thread.setDaemon(true);
        return thread;
      });
    }

    // A conexão não bloqueia o startup da aplicação.
    executor.execute(this::connectWithRetry);
  }

  private void connectWithRetry() {
    if (!running)
      return;

    try {
      connect();
    } catch (Exception exception) {
      scheduleReconnect();
    }
  }

  private synchronized void connect() throws MqttException {
    if (!running)
      return;

    if (client == null) {
      client = new MqttClient(
          properties.getBrokerUrl(),
          properties.getClientId(),
          new MemoryPersistence());
      client.setCallback(callback());
    }

    if (!client.isConnected())
      client.connect(connectOptions());
  }

  private MqttConnectOptions connectOptions() {
    MqttConnectOptions options = new MqttConnectOptions();

    options.setAutomaticReconnect(true);
    options.setCleanSession(properties.isCleanSession());
    options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
    options.setKeepAliveInterval(properties.getKeepAliveSeconds());

    if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
      options.setUserName(properties.getUsername());
      options.setPassword(properties.getPassword() != null
          ? properties.getPassword().toCharArray()
          : new char[0]);
    }

    return options;
  }

  private MqttCallbackExtended callback() {
    return new MqttCallbackExtended() {

      @Override
      public void connectComplete(boolean reconnect, String serverURI) {
        subscribe();
      }

      @Override
      public void connectionLost(Throwable cause) {
      }

      @Override
      public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);

        DeviceRegistrationResult result = messageHandler.handle(payload);
        publishAck(result);
      }

      @Override
      public void deliveryComplete(IMqttDeliveryToken token) {
        // Sem ação: as respostas de autocadastro não são reenviadas.
      }
    };
  }

  private void subscribe() {
    MqttClient current = client;

    if (!running || current == null || !current.isConnected())
      return;

    try {
      current.subscribe(properties.getRegistrationTopic(), properties.getQos());
    } catch (MqttException exception) {
      scheduleSubscribeRetry();
    }
  }

  private void publishAck(DeviceRegistrationResult result) {
    MqttClient current = client;

    if (!running || current == null || !current.isConnected())
      return;

    try {
      String topic = ackTopic(result.hardwareId());
      MqttMessage message = new MqttMessage(objectMapper.writeValueAsBytes(result));

      message.setQos(properties.getQos());
      message.setRetained(false);

      current.publish(topic, message);
    } catch (Exception exception) {
    }
  }

  private String ackTopic(String hardwareId) {
    String template = properties.getAckTopic();

    if (template == null || template.isBlank())
      return "register/ack/" + topicSegment(hardwareId);

    String segment = topicSegment(hardwareId);
    // {mac} continua aceito para configurações antigas
    return template
        .replace("{hardware_id}", segment)
        .replace("{hardwareId}", segment)
        .replace("{mac}", segment);
  }

  private String topicSegment(String hardwareId) {
    if (hardwareId == null || hardwareId.isBlank())
      return "unknown";

    // Evita que um hardware_id malformado crie níveis/tópicos arbitrários.
    return hardwareId.trim().replaceAll("[^A-Za-z0-9:_-]", "_");
  }

  private void scheduleReconnect() {
    ScheduledExecutorService current = executor;

    if (!running || current == null || current.isShutdown())
      return;

    current.schedule(
        this::connectWithRetry,
        properties.getReconnectDelaySeconds(),
        TimeUnit.SECONDS);
  }

  private void scheduleSubscribeRetry() {
    ScheduledExecutorService current = executor;

    if (!running || current == null || current.isShutdown())
      return;

    current.schedule(
        this::subscribe,
        properties.getReconnectDelaySeconds(),
        TimeUnit.SECONDS);
  }

  private String rootMessage(Throwable throwable) {
    if (throwable == null)
      return "erro desconhecido";

    Throwable root = throwable;
    while (root.getCause() != null && root.getCause() != root)
      root = root.getCause();

    return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
  }

  @Override
  public void stop() {
    synchronized (lifecycleMonitor) {
      running = false;

      if (executor != null) {
        executor.shutdownNow();
        executor = null;
      }
    }

    MqttClient current = client;
    client = null;

    if (current == null)
      return;

    try {
      if (current.isConnected())
        current.disconnect(1000);
    } catch (MqttException exception) {
    }

    try {
      current.close();
    } catch (MqttException exception) {
    }
  }

  @Override
  public void stop(Runnable callback) {
    stop();
    callback.run();
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  @Override
  public boolean isAutoStartup() {
    return true;
  }

  @Override
  public int getPhase() {
    return Integer.MAX_VALUE;
  }
}
