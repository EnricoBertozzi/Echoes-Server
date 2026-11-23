package com.n0hana.echoes_server.infra.logs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.n0hana.echoes_server.institution.InstitutionModel;
import com.n0hana.echoes_server.institution.InstitutionRepository;
import com.n0hana.echoes_server.user.model.Admin;
import com.n0hana.echoes_server.user.model.Manager;
import com.n0hana.echoes_server.user.model.Student;
import com.n0hana.echoes_server.user.model.Teacher;
import com.n0hana.echoes_server.user.model.User;

import jakarta.servlet.http.HttpServletRequest;

@DisplayName("AuditAspect: captura de eventos de auditoria")
class AuditAspectCoverageTests {

    /** métodos apenas para obter instâncias reais de @Auditable */
    static class Fixture {
        @Auditable(action = "CREATE", entity = "Classroom", entityId = "#result.id", entityName = "#nome",
                details = "'senha=abc123 de ' + #nome", institutionId = "#inst")
        void completa(String nome, String inst) {
        }

        @Auditable(action = "CREATE", entity = "Classroom")
        void simples() {
        }

        @Auditable(action = "CREATE", entity = "Outra", entityId = "#naoExiste.foo")
        void naoInstitucional() {
        }

        @Auditable(action = "CREATE", entity = "Classroom", institutionId = "#inst")
        void comInst(String inst) {
        }

        @Auditable(action = "CREATE", entity = "Classroom", entityId = "#nulo")
        void comNulo(String nulo) {
        }
    }

    record Result(String id) {
    }

    private ApplicationEventPublisher publisher;
    private AuditLogService service;
    private InstitutionRepository institutionRepository;
    private HttpServletRequest request;
    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        service = mock(AuditLogService.class);
        institutionRepository = mock(InstitutionRepository.class);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("203.0.113.5");
        request = req;
        aspect = new AuditAspect(publisher, request, service, institutionRepository);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Auditable annotation(String method, Class<?>... params) throws Exception {
        return Fixture.class.getDeclaredMethod(method, params).getAnnotation(Auditable.class);
    }

    private static JoinPoint joinPoint(String method, Class<?>[] types, String[] names, Object... args)
            throws Exception {
        Method m = Fixture.class.getDeclaredMethod(method, types);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        when(sig.getParameterNames()).thenReturn(names);
        JoinPoint jp = mock(JoinPoint.class);
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getArgs()).thenReturn(args);
        return jp;
    }

    private void login(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private AuditLog published() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publishEvent(captor.capture());
        return (AuditLog) captor.getValue();
    }

    private AuditLog recorded() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(service).recordFailure(captor.capture());
        return captor.getValue();
    }

    private InstitutionModel institution(UUID id) {
        InstitutionModel i = new InstitutionModel();
        i.setId(id);
        return i;
    }

    @Test
    @DisplayName("sucesso avalia SpEL, mascara PII e usa o IP do cliente")
    void sucessoComSpel() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Manager manager = new Manager(institution(instId));
        manager.setId(userId);
        manager.setName("Ana");
        login(manager);
        when(institutionRepository.findById(instId)).thenReturn(Optional.of(institution(instId)));
        InstitutionModel named = institution(instId);
        named.setName("Escola Fictícia");
        when(institutionRepository.findById(instId)).thenReturn(Optional.of(named));

        JoinPoint jp = joinPoint("completa", new Class<?>[] { String.class, String.class },
                new String[] { "nome", "inst" }, "Turma A", "inst-9");

        aspect.onSuccess(jp, annotation("completa", String.class, String.class), new Result("r-1"));

        AuditLog log = published();
        assertEquals("SUCCESS", log.getStatus());
        assertEquals(userId.toString(), log.getUserId());
        assertEquals("Ana", log.getActorName());
        assertEquals("MANAGER", log.getActorRole());
        assertEquals("Escola Fictícia", log.getActorInstitutionName());
        assertEquals("r-1", log.getEntityId());
        assertEquals("Turma A", log.getEntityName());
        assertEquals("inst-9", log.getInstitutionId());
        assertEquals("203.0.113.5", log.getIp());
        assertEquals(PiiMasker.mask("senha=abc123 de Turma A", 255), log.getDetails());
        assertTrue(!log.getDetails().contains("abc123"));
    }

    @Test
    @DisplayName("expressão vazia, nula ou com erro resulta em campo nulo, sem interromper")
    void spelComFalha() throws Exception {
        JoinPoint jp = joinPoint("naoInstitucional", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("naoInstitucional"), null);

        AuditLog log = published();
        assertNull(log.getEntityId());
        assertNull(log.getEntityName());
        assertNull(log.getDetails());
        assertNull(log.getInstitutionId());
        assertNull(log.getUserId());
        assertNull(log.getActorName());
    }

    @Test
    @DisplayName("SpEL que avalia para null devolve null; nomes de parâmetros nulos são tolerados")
    void spelNuloEParametrosSemNome() throws Exception {
        JoinPoint comNulo = joinPoint("comNulo", new Class<?>[] { String.class }, new String[] { "nulo" },
                (Object) null);
        aspect.onFailure(comNulo, annotation("comNulo", String.class), new RuntimeException("x"));
        assertNull(recorded().getEntityId());
    }

    @Test
    @DisplayName("nomes de parâmetro ausentes não quebram a avaliação")
    void semNomesDeParametros() throws Exception {
        JoinPoint jp = joinPoint("simples", new Class<?>[0], null);

        aspect.onSuccess(jp, annotation("simples"), null);

        assertEquals("SUCCESS", published().getStatus());
    }

    @Test
    @DisplayName("falha registra FAILED com classe e mensagem da exceção")
    void falha() throws Exception {
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onFailure(jp, annotation("simples"), new IllegalStateException("quebrou"));

        AuditLog log = recorded();
        assertEquals("FAILED", log.getStatus());
        assertEquals("IllegalStateException: quebrou", log.getDetails());
    }

    @Test
    @DisplayName("falha com exceção sem mensagem registra só o nome da classe")
    void falhaSemMensagem() throws Exception {
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onFailure(jp, annotation("simples"), new IllegalStateException());

        assertEquals("IllegalStateException", recorded().getDetails());
    }

    @Test
    @DisplayName("professor e aluno herdam a instituição em entidades institucionais")
    void professorEAluno() throws Exception {
        UUID tid = UUID.randomUUID();
        UUID sid = UUID.randomUUID();
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        Teacher teacher = new Teacher(institution(tid));
        teacher.setId(UUID.randomUUID());
        login(teacher);
        aspect.onSuccess(jp, annotation("simples"), null);
        assertEquals(tid.toString(), published().getInstitutionId());
    }

    @Test
    @DisplayName("aluno herda a instituição")
    void aluno() throws Exception {
        UUID sid = UUID.randomUUID();
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);
        Student student = new Student(institution(sid));
        student.setId(UUID.randomUUID());
        login(student);

        aspect.onFailure(jp, annotation("simples"), new RuntimeException("x"));

        assertEquals(sid.toString(), recorded().getInstitutionId());
    }

    @Test
    @DisplayName("admin não tem instituição; entidade não institucional também não")
    void adminENaoInstitucional() throws Exception {
        Admin admin = new Admin();
        admin.setId(UUID.randomUUID());
        login(admin);
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        AuditLog log = published();
        assertNull(log.getInstitutionId());
        assertNull(log.getActorInstitutionName());
        assertEquals("ADMIN", log.getActorRole());
    }

    @Test
    @DisplayName("entidade não institucional não recebe a instituição do usuário")
    void entidadeGlobal() throws Exception {
        Manager manager = new Manager(institution(UUID.randomUUID()));
        manager.setId(UUID.randomUUID());
        login(manager);
        JoinPoint jp = joinPoint("naoInstitucional", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("naoInstitucional"), null);

        assertNull(published().getInstitutionId());
    }

    @Test
    @DisplayName("gestor com instituição sem id fica sem instituição")
    void instituicaoSemId() throws Exception {
        Manager manager = new Manager(institution(null));
        manager.setId(UUID.randomUUID());
        login(manager);
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        AuditLog log = published();
        assertNull(log.getInstitutionId());
        assertNull(log.getActorInstitutionName());
    }

    @Test
    @DisplayName("instituição não encontrada no repositório deixa o nome nulo")
    void instituicaoNaoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        Manager manager = new Manager(institution(id));
        manager.setId(UUID.randomUUID());
        login(manager);
        when(institutionRepository.findById(id)).thenReturn(Optional.empty());
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        assertNull(published().getActorInstitutionName());
    }

    @Test
    @DisplayName("falha ao ler o ator zera os dados do ator sem interromper")
    void falhaAoResolverAtor() throws Exception {
        UUID id = UUID.randomUUID();
        Manager manager = new Manager(institution(id));
        manager.setId(UUID.randomUUID());
        manager.setName("Ana");
        login(manager);
        when(institutionRepository.findById(any())).thenThrow(new RuntimeException("lazy"));
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        AuditLog log = published();
        assertNull(log.getActorName());
        assertNull(log.getActorRole());
    }

    @Test
    @DisplayName("autenticação não autenticada ou com principal que não é User não identifica ator")
    void principalNaoUser() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        AuditLog log = published();
        assertNull(log.getUserId());
        assertNull(log.getActorName());
    }

    @Test
    @DisplayName("Authentication marcada como não autenticada não identifica ator")
    void naoAutenticado() throws Exception {
        Manager manager = new Manager(institution(UUID.randomUUID()));
        manager.setId(UUID.randomUUID());
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        when(auth.getPrincipal()).thenReturn(manager);
        SecurityContextHolder.getContext().setAuthentication(auth);
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        assertNull(published().getUserId());
    }

    @Test
    @DisplayName("sem requisição HTTP, o evento fica sem IP")
    void semRequisicao() throws Exception {
        HttpServletRequest semContexto = mock(HttpServletRequest.class);
        when(semContexto.getRemoteAddr()).thenThrow(new IllegalStateException("sem requisição"));
        aspect = new AuditAspect(publisher, semContexto, service, institutionRepository);
        JoinPoint jp = joinPoint("simples", new Class<?>[0], new String[0]);

        aspect.onSuccess(jp, annotation("simples"), null);

        assertNull(published().getIp());
    }
}
