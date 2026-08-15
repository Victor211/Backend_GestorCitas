package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.shared.entity.Business;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerIdentityResolverTest {

    @Mock
    private CustomerRepository customerRepository;

    private CustomerIdentityResolver resolver;
    private Business business;

    @BeforeEach
    void setUp() {
        resolver = new CustomerIdentityResolver(customerRepository);

        business = new Business();
        business.setId(1L);
        business.setName("Peluquería Elegance");
    }

    @Test
    void findActiveDelegatesToRepositoryByBusinessAndPhone() {
        Customer existing = new Customer();
        existing.setId(9L);
        when(customerRepository.findByBusinessIdAndPhoneAndActiveTrue(1L, "+595981000000"))
                .thenReturn(Optional.of(existing));

        Optional<Customer> found = resolver.findActive(1L, "+595981000000");

        assertThat(found).contains(existing);
    }

    @Test
    void createFromNameSplitsFirstAndLastNameAndUsesSenderPhone() {
        when(customerRepository.findByBusinessIdAndPhone(1L, "+595981000000")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = resolver.createFromName(business, "+595981000000", "María López");

        assertThat(created.getFirstName()).isEqualTo("María");
        assertThat(created.getLastName()).isEqualTo("López");
        assertThat(created.getPhone()).isEqualTo("+595981000000");
        assertThat(created.getBusiness()).isEqualTo(business);
        assertThat(created.getActive()).isTrue();
    }

    @Test
    void createFromNameAcceptsASingleTokenNameWithoutAskingForLastName() {
        when(customerRepository.findByBusinessIdAndPhone(1L, "+595981000000")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = resolver.createFromName(business, "+595981000000", "María");

        assertThat(created.getFirstName()).isEqualTo("María");
        assertThat(created.getLastName()).isNull();
    }

    @Test
    void createFromNameStripsCommonLeadInPhrases() {
        when(customerRepository.findByBusinessIdAndPhone(1L, "+595981000000")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Customer created = resolver.createFromName(business, "+595981000000", "Me llamo Cristian Benitez");

        assertThat(created.getFirstName()).isEqualTo("Cristian");
        assertThat(created.getLastName()).isEqualTo("Benitez");
    }

    @Test
    void createFromNameReactivatesAnInactiveCustomerWithTheSamePhoneInsteadOfDuplicating() {
        Customer inactive = new Customer();
        inactive.setId(5L);
        inactive.setActive(false);
        when(customerRepository.findByBusinessIdAndPhone(1L, "+595981000000")).thenReturn(Optional.of(inactive));
        ArgumentCaptor<Customer> captor = ArgumentCaptor.forClass(Customer.class);
        when(customerRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Customer result = resolver.createFromName(business, "+595981000000", "Ana Gómez");

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(captor.getValue().getActive()).isTrue();
    }

}
