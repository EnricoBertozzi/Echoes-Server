package com.n0hana.echoes_server.device.event;

/**
 * Tipos de evento que o dispositivo reporta. Uma "sessão de ausculta" é contada
 * pelos eventos {@link #PLAY}; CONNECTION indica que o ESP ficou online.
 */
public enum DeviceEventType {
  CONNECTION,
  PLAY,
  PAUSE,
  STOP
}
