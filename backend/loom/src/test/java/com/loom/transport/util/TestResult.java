package com.loom.transport.util;

import lombok.Data;

/**
 * Result object to track client test outcomes
 */
@Data
public class TestResult {
    final int clientId;
    boolean connected = false;
    boolean receivedMessage = false;
    String message = null;
    Exception error = null;

    public TestResult(int clientId) {
        this.clientId = clientId;
    }
}