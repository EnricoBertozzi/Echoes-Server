package com.n0hana.echoes_server.notifier;

public interface InstitutionNotifier {
    void notifyPendingVerification(InstitutionNotificationData data, String reason);
    void notifyVerificationCompleted(InstitutionNotificationData data);
    void notifyVerificationRejected(InstitutionNotificationData data, String reason);
}
