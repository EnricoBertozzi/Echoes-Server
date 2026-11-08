package com.n0hana.echoes_server.infra.notifier;

import org.springframework.stereotype.Service;

import com.n0hana.echoes_server.infra.notifier.data.InstituitionVerificationCompletedData;
import com.n0hana.echoes_server.infra.notifier.data.InstitutionPendingVerificationData;
import com.n0hana.echoes_server.infra.notifier.data.InstitutionVerificationRejectedData;
import com.n0hana.echoes_server.infra.notifier.data.MfaCodeData;
import com.n0hana.echoes_server.infra.notifier.data.PasswordResetData;
import com.n0hana.echoes_server.infra.notifier.data.RegisterInviteData;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionPendingVerificationEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionVerificationCompletedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.InstitutionVerificationRejectedEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.MfaCodeEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.PasswordResetEmailMessage;
import com.n0hana.echoes_server.infra.notifier.message.RegisterInviteMessage;

/**
 * Serviço para notificação de usuários utilizando canais de notificação
 * {@link Notifier}
 * 
 * @author Enrico Bertozzi
 */
@Service
public class NotifierService {

    private final EmailNotifier emailNotifier;

    public NotifierService(EmailNotifier emailNotifier) {
        this.emailNotifier = emailNotifier;
    }

    /**
     * Envia notificação de verificação de dois fatores
     * 
     * @param to   e-mail do destinatário
     * @param code código de verificação
     */
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
    public void sendRegisterInvite(String to, String code, String email) {
        RegisterInviteMessage message = new RegisterInviteMessage();
        message.setData(new RegisterInviteData(code, email));

        emailNotifier.send(to, message);
    }

    /**
     * Envia notificação com código de redefinição de senha
     * 
     * @param to   e-mail do destinatário
     * @param code código de redefinição
     */
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
    public void sendInstitutionRejected(String to, String name, String cnpj, String reason) {
        InstitutionVerificationRejectedEmailMessage message = new InstitutionVerificationRejectedEmailMessage();
        message.setData(new InstitutionVerificationRejectedData(reason, name, cnpj));

        emailNotifier.send(to, message);
    }
}
