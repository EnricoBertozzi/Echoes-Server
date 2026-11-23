package com.n0hana.echoes_server.infra.notifier;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.notifier.data.ClassroomTeacherAddedData;
import com.n0hana.echoes_server.infra.notifier.data.InstituitionVerificationCompletedData;
import com.n0hana.echoes_server.infra.notifier.data.InstitutionPendingVerificationData;
import com.n0hana.echoes_server.infra.notifier.data.InstitutionVerificationRejectedData;
import com.n0hana.echoes_server.infra.notifier.data.MfaCodeData;
import com.n0hana.echoes_server.infra.notifier.data.PasswordResetData;
import com.n0hana.echoes_server.infra.notifier.data.RegisterInviteData;
import com.n0hana.echoes_server.infra.notifier.message.ClassroomTeacherAddedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionPendingVerificationEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionVerificationCompletedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionVerificationRejectedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.data.AccountRestoredData;
import com.n0hana.echoes_server.infra.notifier.data.JaversStorageAlertData;
import com.n0hana.echoes_server.infra.notifier.message.AccountRestoredEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.JaversStorageAlertEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.MfaCodeEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.PasswordResetEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.RegisterInviteMessage;
import com.n0hana.echoes_server.infra.notifier.message.NewDeviceLoginEmailMessage;

/**
 * Serviço para notificação de usuários utilizando canais de notificação
 * {@link Notifier}
 * 
 * @author Enrico Bertozzi
 */
@Service
public class NotifierService {

    private final EmailNotifier emailNotifier;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl = "http://localhost:5173";

    public NotifierService(EmailNotifier emailNotifier) {
        this.emailNotifier = emailNotifier;
    }

    /**
     * Envia notificação de verificação de dois fatores
     * 
     * @param to   e-mail do destinatário
     * @param code código de verificação
     */
    @Async("taskExecutor")
    public void sendMfaCode(String to, String code) {
        MfaCodeEmailMessage message = new MfaCodeEmailMessage();
        message.setData(new MfaCodeData(code));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação de convite de novo usuário
     * 
     * @param to    e-mail do destinatário
     * @param code  código de convite
     * @param email e-mail do destinário
     */
    @Async("taskExecutor")
    public void sendRegisterInvite(String to, String code, String email) {
        RegisterInviteMessage message = new RegisterInviteMessage();
        message.setFrontendUrl(frontendUrl);
        message.setData(new RegisterInviteData(code, email));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação com código de redefinição de senha
     * 
     * @param to   e-mail do destinatário
     * @param code código de redefinição
     */
    @Async("taskExecutor")
    public void sendNewDeviceLogin(String to, String ip, String userAgent, java.time.Instant when) {
        NewDeviceLoginEmailMessage message = new NewDeviceLoginEmailMessage();
        message.setData(new com.n0hana.echoes_server.infra.notifier.data.NewDeviceLoginData(ip, userAgent, when));

        emailNotifier.send(to, message);
    }

    /**
     * Alerta o administrador de que o histórico do Javers passou do limite de armazenamento
     * 
     * @param to          e-mail do destinatário
     * @param bytes       tamanho atual da tabela de snapshots
     * @param thresholdMb limite configurado, em MB
     */
    @Async("taskExecutor")
    public void sendJaversStorageAlert(String to, long bytes, long thresholdMb) {
        JaversStorageAlertEmailMessage message = new JaversStorageAlertEmailMessage();
        message.setData(new JaversStorageAlertData(bytes, thresholdMb));

        emailNotifier.send(to, message);
    }

    /**
     * Avisa o titular que a conta foi restaurada por um administrador
     * 
     * @param to   e-mail do destinatário
     * @param name nome do usuário
     */
    @Async("taskExecutor")
    public void sendAccountRestored(String to, String name) {
        AccountRestoredEmailMessage message = new AccountRestoredEmailMessage();
        message.setData(new AccountRestoredData(name));

        emailNotifier.send(to, message);
    }

    /**
     * Envia o código de redefinição de senha
     * 
     * @param to   e-mail do destinatário
     * @param code código de verificação
     */
    @Async("taskExecutor")
    public void sendPasswordReset(String to, String code) {
        PasswordResetEmailMessage message = new PasswordResetEmailMessage();
        message.setData(new PasswordResetData(code));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação de verificação de instituição em andamento
     * 
     * @param to     e-mail do destinatário
     * @param name   nome da instituição
     * @param cnpj   cnpj da instituição
     * @param reason motivo da
     */

    @Async("taskExecutor")
    public void sendInstitutionPending(String to, String name, String cnpj, String reason) {
        InstitutionPendingVerificationEmailMessage message = new InstitutionPendingVerificationEmailMessage();
        message.setData(new InstitutionPendingVerificationData(reason, name, cnpj));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação de verficação de instituição concluída
     * 
     * @param to   e-mail do destinário
     * @param name nome da instituição
     * @param cnpj cnpj da instituição
     */
    @Async("taskExecutor")
    public void sendInstituitionCompleted(String to, String name, String cnpj) {
        InstitutionVerificationCompletedEmailMessage message = new InstitutionVerificationCompletedEmailMessage();
        message.setData(new InstituitionVerificationCompletedData(name, cnpj));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação de falha ao realizar a verificação da instituição
     * 
     * @param to     e-mail do destinatário
     * @param name   nome da instituição
     * @param cnpj   cnpj da instituição
     * @param reason razão do erro de verrificação
     */
    @Async("taskExecutor")
    public void sendInstitutionRejected(String to, String name, String cnpj, String reason) {
        InstitutionVerificationRejectedEmailMessage message = new InstitutionVerificationRejectedEmailMessage();
        message.setData(new InstitutionVerificationRejectedData(reason, name, cnpj));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação de professor adicionado a uma turma
     * 
     * @param to             e-mail do professor
     * @param classroomTitle título da turma
     * @param period         período da turma no formato AAAA/SS
     */
    @Async("taskExecutor")
    public void sendClassroomTeacherAdded(String to, String classroomTitle, String period) {
        ClassroomTeacherAddedEmailMessage message = new ClassroomTeacherAddedEmailMessage();
        message.setData(new ClassroomTeacherAddedData(classroomTitle, period));

        emailNotifier.send(to, message);
    }
}
