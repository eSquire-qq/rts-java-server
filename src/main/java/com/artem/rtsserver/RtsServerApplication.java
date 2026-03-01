package com.artem.rtsserver;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.artem.rtsserver.net.server.TcpServer;

@SpringBootApplication
public class RtsServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RtsServerApplication.class, args);
    }

    @Bean
    CommandLineRunner startTcpServer(TcpServer tcpServer) {
        return args -> {
            int port = 7777;
            tcpServer.start(port);
        };
    }
}