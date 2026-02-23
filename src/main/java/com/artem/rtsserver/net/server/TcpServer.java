package com.artem.rtsserver.net.server;

import org.springframework.stereotype.Component;

@Component
public class TcpServer {

    public void start(int port) {
        System.out.println("TCP Server starting on port " + port);
    }
}
