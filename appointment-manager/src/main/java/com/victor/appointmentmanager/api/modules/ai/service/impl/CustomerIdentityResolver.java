package com.victor.appointmentmanager.api.modules.ai.service.impl;

import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import com.victor.appointmentmanager.api.modules.customers.repository.CustomerRepository;
import com.victor.appointmentmanager.api.shared.entity.Business;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resuelve la identidad del Customer a partir del {@code sender_phone} que ya entrega el
 * webhook de WhatsApp (o el request del panel autenticado). El teléfono es siempre el dato
 * confiable: nunca se le vuelve a preguntar al cliente. Cuando no existe un Customer activo con
 * ese teléfono, este componente crea uno nuevo (o reactiva uno inactivo con el mismo teléfono,
 * para no violar el unique constraint business_id+phone) a partir del texto de nombre que el
 * cliente respondió — sin depender de la IA para reconocerlo.
 */
@Component
@RequiredArgsConstructor
public class CustomerIdentityResolver {

    private static final Pattern NAME_LEAD_IN = Pattern.compile("(?i)^(soy|me llamo|mi nombre es)\\s+");

    private final CustomerRepository customerRepository;

    public Optional<Customer> findActive(Long businessId, String phone) {
        return customerRepository.findByBusinessIdAndPhoneAndActiveTrue(businessId, phone);
    }

    @Transactional
    public Customer createFromName(Business business, String phone, String rawNameText) {
        String[] parts = splitName(stripLeadIn(rawNameText));

        Customer customer = customerRepository.findByBusinessIdAndPhone(business.getId(), phone)
                .orElseGet(Customer::new);
        customer.setBusiness(business);
        customer.setPhone(phone);
        customer.setFirstName(parts[0]);
        customer.setLastName(parts[1]);
        customer.setActive(true);

        return customerRepository.save(customer);
    }

    private String stripLeadIn(String rawNameText) {
        String trimmed = rawNameText == null ? "" : rawNameText.trim();
        Matcher matcher = NAME_LEAD_IN.matcher(trimmed);
        if (!matcher.find()) {
            return trimmed;
        }
        String remainder = trimmed.substring(matcher.end()).trim();
        return remainder.isEmpty() ? trimmed : remainder;
    }

    private String[] splitName(String cleaned) {
        String[] tokens = cleaned.split("\\s+", 2);
        String firstName = tokens[0];
        String lastName = tokens.length > 1 ? tokens[1] : null;
        return new String[] {firstName, lastName};
    }

}
