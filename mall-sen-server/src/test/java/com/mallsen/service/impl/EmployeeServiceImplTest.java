package com.mallsen.service.impl;

import com.github.pagehelper.Page;
import com.mallsen.constant.MessageConstant;
import com.mallsen.constant.PasswordConstant;
import com.mallsen.constant.StatusConstant;
import com.mallsen.dto.EmployeeDTO;
import com.mallsen.dto.EmployeeLoginDTO;
import com.mallsen.dto.EmployeePageQueryDTO;
import com.mallsen.entity.Employee;
import com.mallsen.exception.AccountLockedException;
import com.mallsen.exception.AccountNotFoundException;
import com.mallsen.exception.PasswordErrorException;
import com.mallsen.mapper.EmployeeMapper;
import com.mallsen.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private EmployeeLoginDTO loginDTO;
    private EmployeeDTO employeeDTO;
    private Employee employee;

    @BeforeEach
    void setUp() {
        loginDTO = new EmployeeLoginDTO();
        loginDTO.setUsername("testuser");
        loginDTO.setPassword("123456");

        employeeDTO = new EmployeeDTO();
        employeeDTO.setUsername("newuser");
        employeeDTO.setName("新用户");
        employeeDTO.setPhone("13800138000");
        employeeDTO.setSex("1");
        employeeDTO.setIdNumber("110101199001011234");

        employee = Employee.builder()
                .id(1L)
                .username("testuser")
                .password(new BCryptPasswordEncoder().encode("123456"))
                .name("测试用户")
                .status(StatusConstant.ENABLE)
                .build();
    }

    @Test
    void login_shouldSucceedWhenCredentialsAreValid() {
        when(employeeMapper.getByUsername("testuser")).thenReturn(employee);

        Employee result = employeeService.login(loginDTO);

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    void login_shouldThrowWhenAccountNotFound() {
        when(employeeMapper.getByUsername("testuser")).thenReturn(null);

        AccountNotFoundException ex = assertThrows(AccountNotFoundException.class,
                () -> employeeService.login(loginDTO));
        assertEquals(MessageConstant.ACCOUNT_NOT_FOUND, ex.getMessage());
    }

    @Test
    void login_shouldThrowWhenPasswordIsWrong() {
        Employee emp = Employee.builder()
                .username("testuser")
                .password(new BCryptPasswordEncoder().encode("wrongpassword"))
                .status(StatusConstant.ENABLE)
                .build();
        when(employeeMapper.getByUsername("testuser")).thenReturn(emp);

        PasswordErrorException ex = assertThrows(PasswordErrorException.class,
                () -> employeeService.login(loginDTO));
        assertEquals(MessageConstant.PASSWORD_ERROR, ex.getMessage());
    }

    @Test
    void login_shouldThrowWhenAccountIsLocked() {
        Employee emp = Employee.builder()
                .username("testuser")
                .password(new BCryptPasswordEncoder().encode("123456"))
                .status(StatusConstant.DISABLE)
                .build();
        when(employeeMapper.getByUsername("testuser")).thenReturn(emp);

        AccountLockedException ex = assertThrows(AccountLockedException.class,
                () -> employeeService.login(loginDTO));
        assertEquals(MessageConstant.ACCOUNT_LOCKED, ex.getMessage());
    }

    @Test
    void save_shouldEncodeDefaultPasswordAndInsert() {
        employeeService.save(employeeDTO);

        verify(employeeMapper).insert(argThat(e -> {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            return e.getUsername().equals("newuser") &&
                    e.getStatus().equals(StatusConstant.ENABLE) &&
                    encoder.matches(PasswordConstant.DEFAULT_PASSWORD, e.getPassword());
        }));
    }

    @SuppressWarnings("unchecked")
    @Test
    void pageQuery_shouldReturnPageResult() {
        EmployeePageQueryDTO queryDTO = new EmployeePageQueryDTO();
        queryDTO.setPage(1);
        queryDTO.setPageSize(10);

        Page<Employee> page = new Page<>(1, 10);
        page.add(employee);
        page.setTotal(1L);
        when(employeeMapper.pageQuery(queryDTO)).thenReturn(page);

        PageResult result = employeeService.pageQuery(queryDTO);

        assertEquals(1L, result.getTotal());
        assertEquals(1, result.getRecords().size());
    }

    @Test
    void startOrStop_shouldUpdateStatus() {
        employeeService.startOrStop(StatusConstant.DISABLE, 1L);

        verify(employeeMapper).update(argThat(e ->
                e.getId().equals(1L) &&
                e.getStatus().equals(StatusConstant.DISABLE)
        ));
    }

    @Test
    void getById_shouldMaskPassword() {
        employee.setPassword("secret123");
        when(employeeMapper.getById(1L)).thenReturn(employee);

        Employee result = employeeService.getById(1L);

        assertEquals("****", result.getPassword());
    }

    @Test
    void update_shouldCopyAndUpdate() {
        employeeService.update(employeeDTO);

        verify(employeeMapper).update(argThat(e ->
                e.getUsername().equals("newuser") &&
                e.getName().equals("新用户")
        ));
    }
}
