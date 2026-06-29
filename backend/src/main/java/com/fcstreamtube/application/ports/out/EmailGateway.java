package com.fcstreamtube.application.ports.out;

public interface EmailGateway {
    void sendConfirmationEmail(String to, String confirmationUrl);
    void sendPasswordResetEmail(String to, String resetUrl);
}
