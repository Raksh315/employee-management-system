
package com.shubha.employeemanagementsystem.service.impl;

import com.shubha.employeemanagementsystem.entity.Employee;
import com.shubha.employeemanagementsystem.repository.EmployeeRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeServiceImpl employeeService;

    private Employee createEmployee() {
        Employee employee = new Employee();
        employee.setId(1L);
        employee.setName("Rahul");
        employee.setEmail("rahul@gmail.com");
        employee.setDepartment("IT");
        employee.setSalary(60000);
        return employee;
    }

    @Test
    void createEmployeeTest() {
        Employee employee = createEmployee();

        when(employeeRepository.save(employee)).thenReturn(employee);

        Employee result = employeeService.createEmployee(employee);

        assertNotNull(result);
        assertEquals("Rahul", result.getName());
        verify(employeeRepository, times(1)).save(employee);
    }

    @Test
    void getAllEmployeesTest() {
        Employee employee1 = createEmployee();

        Employee employee2 = new Employee();
        employee2.setId(2L);
        employee2.setName("Priya");
        employee2.setEmail("priya@gmail.com");
        employee2.setDepartment("HR");
        employee2.setSalary(50000);

        when(employeeRepository.findAll())
                .thenReturn(List.of(employee1, employee2));

        List<Employee> result = employeeService.getAllEmployees();

        assertEquals(2, result.size());
        assertEquals("Rahul", result.get(0).getName());
        assertEquals("Priya", result.get(1).getName());

        verify(employeeRepository, times(1)).findAll();
    }

    @Test
    void getEmployeeByIdTest() {
        Employee employee = createEmployee();

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employee));

        Employee result = employeeService.getEmployeeById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Rahul", result.getName());

        verify(employeeRepository, times(1)).findById(1L);
    }

    @Test
    void getEmployeeByIdNotFoundTest() {
        when(employeeRepository.findById(99L))
                .thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> employeeService.getEmployeeById(99L)
        );

        assertEquals(
                "Employee not found with id 99",
                exception.getMessage()
        );

        verify(employeeRepository, times(1)).findById(99L);
    }

    @Test
    void updateEmployeeTest() {
        Employee existingEmployee = createEmployee();

        Employee updatedEmployee = new Employee();
        updatedEmployee.setName("Rahul Kumar");
        updatedEmployee.setEmail("rahulkumar@gmail.com");
        updatedEmployee.setDepartment("Development");
        updatedEmployee.setSalary(70000);

        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(existingEmployee));

        when(employeeRepository.save(existingEmployee))
                .thenReturn(existingEmployee);

        Employee result = employeeService.updateEmployee(
                1L, updatedEmployee
        );

        assertEquals("Rahul Kumar", result.getName());
        assertEquals("rahulkumar@gmail.com", result.getEmail());
        assertEquals("Development", result.getDepartment());
        assertEquals(70000, result.getSalary());

        verify(employeeRepository, times(1)).findById(1L);
        verify(employeeRepository, times(1)).save(existingEmployee);
    }

    @Test
    void deleteEmployeeTest() {
        doNothing().when(employeeRepository).deleteById(1L);

        employeeService.deleteEmployee(1L);

        verify(employeeRepository, times(1)).deleteById(1L);
    }
}
