package com.n0hana.echoes_server;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;

import com.n0hana.echoes_server.infra.file.FileStorageProperties;
import com.n0hana.echoes_server.infra.file.FileStorageService;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(FileStorageProperties.class)
public class EchoesServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(EchoesServerApplication.class, args);
  }

  // Inicializa o diretório de uploads
  @Bean
  @Profile("!test")
  CommandLineRunner init(FileStorageService service) {
    return (args) -> {
      service.init();
    };
  }

}
