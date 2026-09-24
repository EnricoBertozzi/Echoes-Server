package com.n0hana.echoes_server.institution;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.n0hana.echoes_server.cnpj.CnpjDTO;
import com.n0hana.echoes_server.cnpj.CnpjService;
import com.n0hana.echoes_server.cnpj.exception.CnpjNaoEncontradoException;
import com.n0hana.echoes_server.cnpj.exception.CnpjProviderIndisponivelException;
import com.n0hana.echoes_server.notifier.InstitutionNotificationData;
import com.n0hana.echoes_server.notifier.InstitutionNotifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstitutionVerificationService {

    private final CnpjService cnpjService;
    private final InstitutionRepository repository;
    private final InstitutionNotifier notifier;

    public enum VerificationOutcome {
        VERIFIED, STILL_PENDING, REJECTED, SKIPPED
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public VerificationOutcome tryVerify(UUID institutionId) {
        InstitutionModel inst = repository.findById(institutionId).orElse(null);
        
        if (inst == null || inst.getVerificationStatus() == InstitutionVerificationStatus.VERIFIED) {
            return VerificationOutcome.SKIPPED;
        }
        if (inst.getVerificationStatus() == InstitutionVerificationStatus.REJECTED) {
            return VerificationOutcome.SKIPPED;
        }

        inst.setLastVerificationAttemptAt(LocalDateTime.now());
        inst.setVerificationAttempts(inst.getVerificationAttempts() + 1);

        try {
            CnpjDTO dados = cnpjService.consultar(inst.getCnpj());
            applyDadosReceita(inst, dados);
            inst.setVerificationStatus(InstitutionVerificationStatus.VERIFIED);
            repository.save(inst);
            
            notifier.notifyVerificationCompleted(InstitutionNotificationData.from(inst));
            log.info("Reverificação: instituição {} CNPJ {} VERIFIED", institutionId, inst.getCnpj());
            return VerificationOutcome.VERIFIED;

        } catch (CnpjProviderIndisponivelException e) {
            repository.save(inst);
            log.warn("Reverificação: instituição {} CNPJ {} STILL_PENDING ({})",
                institutionId, inst.getCnpj(), e.getMessage());
            return VerificationOutcome.STILL_PENDING;

        } catch (CnpjNaoEncontradoException e) {
            inst.setVerificationStatus(InstitutionVerificationStatus.REJECTED);
            repository.save(inst);
            
            notifier.notifyVerificationRejected(
                InstitutionNotificationData.from(inst),
                "CNPJ não encontrado na Receita Federal durante reverificação"
            );
            log.warn("Reverificação: instituição {} CNPJ {} REJECTED (não encontrado na Receita)",
                institutionId, inst.getCnpj());
            return VerificationOutcome.REJECTED;
        }
    }
        

    public void applyDadosReceita(InstitutionModel inst, CnpjDTO dados) {
    if (inst.getName() != null && !inst.getName().equals(dados.razaoSocial())) {
        log.info("Razão social da instituição {} alterada: '{}' -> '{}'",
                inst.getId(), inst.getName(), dados.razaoSocial());
    }
    inst.setCnpj(dados.cnpj());
    inst.setName(dados.razaoSocial());
    inst.setNomeFantasia(dados.nomeFantasia());
    inst.setCep(dados.cep());
    inst.setAddress(formatEndereco(dados));

    if (dados.telefone() != null && !dados.telefone().isBlank()) {
        inst.setPhone(dados.telefone());
    }
}


    private String formatEndereco(CnpjDTO d) {
        StringBuilder endereco = new StringBuilder();
        if (d.logradouro() != null && !d.logradouro().isBlank()) endereco.append(d.logradouro());
        if (d.numero() != null && !d.numero().isBlank()) endereco.append(", ").append(d.numero());
        if (d.complemento() != null && !d.complemento().isBlank()) endereco.append(" - ").append(d.complemento());
        if (d.bairro() != null && !d.bairro().isBlank()) endereco.append(", ").append(d.bairro());
        if (d.municipio() != null && !d.municipio().isBlank()) endereco.append(", ").append(d.municipio());
        if (d.uf() != null && !d.uf().isBlank()) endereco.append(" - ").append(d.uf());
        if (d.cep() != null && !d.cep().isBlank()) endereco.append(", CEP: ").append(d.cep());

        String result = endereco.toString().trim();
        return result.isEmpty() ? null : result;
        }
}
