package com.portalcursos.ng02.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.portalcursos.ng02.exception.BusinessException;
import com.portalcursos.ng02.model.Role;
import com.portalcursos.ng02.model.Role.ERole;
import com.portalcursos.ng02.model.StaffMember;
import com.portalcursos.ng02.model.Student;
import com.portalcursos.ng02.model.User;
import com.portalcursos.ng02.repository.RoleRepository;
import com.portalcursos.ng02.repository.StaffMemberRepository;
import com.portalcursos.ng02.repository.StudentRepository;
import com.portalcursos.ng02.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

/**
 * Cobre o fix de {@link UserService#deleteUser}: ao remover a conta de login (hard delete),
 * o StaffMember/Student vinculado deve ser desativado, evitando um registro institucional
 * "fantasma" (ativo, mas sem usuário para autenticar).
 */
@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private RoleResolver roleResolver;

    @Mock
    private StaffMemberRepository staffMemberRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AuthorityHierarchyService authorityService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder encoder;

    @InjectMocks
    private UserService userService;

    private User operator;
    private User target;

    @BeforeEach
    public void setUp() {
        operator = User.builder().id(1L).username("admin").email("admin@test.com").password("x").build();
        target = User.builder().id(2L).username("colaborador").email("colab@test.com").password("x").build();

        lenient().when(authorityService.getCurrentUser()).thenReturn(operator);
        lenient().when(userRepository.findById(2L)).thenReturn(Optional.of(target));
    }

    @Test
    public void deleteUserDesativaStaffMemberVinculado() {
        StaffMember staff = StaffMember.builder().id(2L).fullName("Colaborador").position("X").department("Y").build();
        staff.setActive(true);

        when(staffMemberRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(staff));
        when(studentRepository.findByUserId(2L)).thenReturn(Optional.empty());

        userService.deleteUser(2L);

        assertFalse(staff.isActive(), "StaffMember deve ser desativado antes do hard delete do User");
        verify(staffMemberRepository).save(staff);
        verify(userRepository).deleteById(2L);
    }

    @Test
    public void deleteUserDesativaStudentVinculado() {
        Student student = Student.builder().id(50L).fullName("Aluno").build();
        student.setActive(true);

        when(staffMemberRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.empty());
        when(studentRepository.findByUserId(2L)).thenReturn(Optional.of(student));

        userService.deleteUser(2L);

        assertFalse(student.isActive(), "Student deve ser desativado antes do hard delete do User");
        verify(studentRepository).save(student);
        verify(userRepository).deleteById(2L);
    }

    @Test
    public void deleteUserSemVinculosNaoChamaSaveExtra() {
        when(staffMemberRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.empty());
        when(studentRepository.findByUserId(2L)).thenReturn(Optional.empty());

        userService.deleteUser(2L);

        verify(staffMemberRepository, never()).save(any());
        verify(studentRepository, never()).save(any());
        verify(userRepository).deleteById(2L);
    }

    /**
     * Achado 1.6 da auditoria: createUser usava RoleResolver.resolveLenient, que engolia
     * role desconhecida e atribuia ROLE_ALUNO silenciosamente. Agora usa resolveStrict e
     * traduz o erro em BusinessException (400) — mesmo padrao do AuthController.registerUser.
     */
    @Test
    public void createUserComRoleDesconhecidaLancaBusinessExceptionSemPersistir() {
        User novo = User.builder().username("novo").email("novo@test.com").password("senha123").build();
        Set<String> strRoles = Set.of("papel-inexistente");

        when(encoder.encode("senha123")).thenReturn("hash");
        when(roleResolver.resolveStrict(strRoles))
                .thenThrow(new IllegalArgumentException("Role desconhecida: papel-inexistente"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.createUser(novo, strRoles, null, null, null));

        assertEquals("Role desconhecida: papel-inexistente", ex.getMessage());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    public void createUserComRoleValidaDelegaParaResolveStrict() {
        User novo = User.builder().id(3L).username("novo").email("novo@test.com").password("senha123").build();
        Set<String> strRoles = Set.of("aluno");
        Role roleAluno = Role.builder().name(ERole.ROLE_ALUNO).build();

        when(encoder.encode("senha123")).thenReturn("hash");
        when(roleResolver.resolveStrict(strRoles)).thenReturn(Set.of(roleAluno));
        when(userRepository.saveAndFlush(novo)).thenReturn(novo);

        User salvo = userService.createUser(novo, strRoles, null, null, null);

        assertEquals(Set.of(roleAluno), salvo.getRoles());
        verify(roleResolver).resolveStrict(strRoles);
        verify(staffMemberRepository, never()).findByIdIncludingInactive(any());
    }
}
