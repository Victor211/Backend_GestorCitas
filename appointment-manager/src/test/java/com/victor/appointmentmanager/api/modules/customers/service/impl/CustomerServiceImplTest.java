package com.victor.appointmentmanager.api.modules.customers.service.impl;

import com.victor.appointmentmanager.api.common.exception.BusinessException;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.UpdateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.response.CustomerResponse;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.exception.CustomerNotFoundException;
import com.victor.appointmentmanager.api.modules.customers.mapper.CustomerMapper;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceImplTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private Business business;
    private Customer customer;

    @BeforeEach
    void setUp() {
        business = new Business();
        business.setId(1L);
        business.setName("Barbería Central");

        customer = new Customer();
        customer.setId(10L);
        customer.setFirstName("Ana");
        customer.setLastName("Gómez");
        customer.setPhone("0981000000");
        customer.setBusiness(business);
        customer.setActive(true);

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
    }

    @Test
    void createsCustomerSuccessfully() {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setFirstName("Ana");
        request.setLastName("Gómez");
        request.setPhone("0981000000");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.existsByBusinessIdAndPhone(1L, "0981000000")).thenReturn(false);
        when(customerMapper.toEntity(request)).thenReturn(customer);
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = new CustomerResponse();
        response.setId(10L);
        when(customerMapper.toDto(customer)).thenReturn(response);

        CustomerResponse result = customerService.create(request);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(customer.getBusiness()).isEqualTo(business);
        verify(customerRepository).save(customer);
    }

    @Test
    void throwsBusinessExceptionWhenPhoneAlreadyExistsInBusinessOnCreate() {
        CreateCustomerRequest request = new CreateCustomerRequest();
        request.setPhone("0981000000");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(customerRepository.existsByBusinessIdAndPhone(1L, "0981000000")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(request))
                .isInstanceOf(BusinessException.class);

        verify(customerRepository, never()).save(any());
    }

    @Test
    void updatesCustomerSuccessfully() {
        UpdateCustomerRequest request = new UpdateCustomerRequest();
        request.setFirstName("Ana María");
        request.setLastName("Gómez");
        request.setPhone("0981000000");
        request.setEmail("ana@example.com");

        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerResponse response = new CustomerResponse();
        response.setFirstName("Ana María");
        when(customerMapper.toDto(customer)).thenReturn(response);

        CustomerResponse result = customerService.update(10L, request);

        assertThat(result.getFirstName()).isEqualTo("Ana María");
        verify(customerMapper).updateEntityFromRequest(request, customer);
        verify(customerRepository, never()).existsByBusinessIdAndPhone(anyLong(), any());
    }

    @Test
    void throwsCustomerNotFoundWhenUpdatingMissingCustomer() {
        UpdateCustomerRequest request = new UpdateCustomerRequest();
        request.setPhone("0981000000");

        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.update(999L, request))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void throwsCustomerNotFoundWhenFindByIdMissing() {
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(999L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void softDeletesCustomer() {
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        customerService.delete(10L);

        assertThat(customer.getActive()).isFalse();
        verify(customerRepository).save(customer);
    }

    @Test
    void findsCustomerByPhone() {
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "0981000000"))
                .thenReturn(Optional.of(customer));

        CustomerResponse response = new CustomerResponse();
        response.setId(10L);
        when(customerMapper.toDto(customer)).thenReturn(response);

        CustomerResponse result = customerService.findByPhone("0981000000");

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    void throwsCustomerNotFoundWhenPhoneNotFound() {
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "0000000000"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findByPhone("0000000000"))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void doesNotExposeCustomerFromAnotherBusinessOnFindById() {
        when(customerRepository.findByIdAndBusinessIdAndActiveTrue(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(10L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

}
