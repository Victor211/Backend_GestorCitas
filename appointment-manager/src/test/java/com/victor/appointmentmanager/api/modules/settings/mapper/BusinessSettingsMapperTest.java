package com.victor.appointmentmanager.api.modules.settings.mapper;

import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.response.BusinessSettingsResponse;
import com.victor.appointmentmanager.api.shared.entity.Business;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessSettingsMapperTest {

    private BusinessSettingsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new BusinessSettingsMapperImpl();
    }

    private Business buildBusiness() {
        Business business = new Business();
        business.setId(1L);
        business.setName("Peluquería Elegance");
        business.setPhone("+595981123456");
        business.setEmail("contacto@peluqueriaelegance.com");
        business.setAddress("Asunción, Paraguay");
        business.setTimezone("America/Asuncion");
        return business;
    }

    // 12. whatsappConfigured false sin IDs.
    @Test
    void whatsappConfiguredIsFalseWithoutIds() {
        Business business = buildBusiness();

        BusinessSettingsResponse response = mapper.toResponse(business);

        assertThat(response.isWhatsappConfigured()).isFalse();
    }

    // 13. whatsappConfigured true con configuración mínima.
    @Test
    void whatsappConfiguredIsTrueWithMinimalConfiguration() {
        Business business = buildBusiness();
        business.setWhatsappPhoneNumberId("123456789");

        BusinessSettingsResponse response = mapper.toResponse(business);

        assertThat(response.isWhatsappConfigured()).isTrue();
    }

    @Test
    void whatsappConfiguredIsFalseWhenPhoneNumberIdIsBlank() {
        Business business = buildBusiness();
        business.setWhatsappPhoneNumberId("   ");

        BusinessSettingsResponse response = mapper.toResponse(business);

        assertThat(response.isWhatsappConfigured()).isFalse();
    }

    @Test
    void toResponseMapsAllEditableFieldsAndId() {
        Business business = buildBusiness();

        BusinessSettingsResponse response = mapper.toResponse(business);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Peluquería Elegance");
        assertThat(response.getPhone()).isEqualTo("+595981123456");
        assertThat(response.getEmail()).isEqualTo("contacto@peluqueriaelegance.com");
        assertThat(response.getAddress()).isEqualTo("Asunción, Paraguay");
        assertThat(response.getTimezone()).isEqualTo("America/Asuncion");
    }

    @Test
    void updateEntityFromRequestOnlyTouchesEditableFields() {
        Business business = buildBusiness();
        business.setWhatsappPhoneNumberId("123456789");
        business.setWhatsappBusinessAccountId("acc-1");
        business.setActive(true);

        UpdateBusinessSettingsRequest request = new UpdateBusinessSettingsRequest();
        request.setName("Nuevo Nombre");
        request.setPhone("+595981000000");
        request.setEmail("nuevo@example.com");
        request.setAddress("Nueva Dirección");
        request.setTimezone("America/Sao_Paulo");

        mapper.updateEntityFromRequest(request, business);

        assertThat(business.getId()).isEqualTo(1L);
        assertThat(business.getName()).isEqualTo("Nuevo Nombre");
        assertThat(business.getPhone()).isEqualTo("+595981000000");
        assertThat(business.getEmail()).isEqualTo("nuevo@example.com");
        assertThat(business.getAddress()).isEqualTo("Nueva Dirección");
        assertThat(business.getActive()).isTrue();
        assertThat(business.getWhatsappPhoneNumberId()).isEqualTo("123456789");
        assertThat(business.getWhatsappBusinessAccountId()).isEqualTo("acc-1");
        // timezone is intentionally not mapped here; the service sets it after validating the ZoneId.
        assertThat(business.getTimezone()).isEqualTo("America/Asuncion");
    }

}
