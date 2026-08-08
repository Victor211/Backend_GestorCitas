package com.victor.appointmentmanager.api.modules.settings.service.impl;

import com.victor.appointmentmanager.api.modules.settings.dto.request.UpdateBusinessSettingsRequest;
import com.victor.appointmentmanager.api.modules.settings.dto.response.BusinessSettingsResponse;
import com.victor.appointmentmanager.api.modules.settings.mapper.BusinessSettingsMapper;
import com.victor.appointmentmanager.api.modules.settings.mapper.BusinessSettingsMapperImpl;
import com.victor.appointmentmanager.api.security.CurrentUserProvider;
import com.victor.appointmentmanager.api.shared.entity.Business;
import com.victor.appointmentmanager.api.shared.repository.BusinessRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessSettingsServiceImplTest {

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessSettingsMapper businessSettingsMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private BusinessSettingsServiceImpl businessSettingsService;

    private Business business;

    @BeforeEach
    void setUp() {
        businessSettingsService =
                new BusinessSettingsServiceImpl(businessRepository, businessSettingsMapper, currentUserProvider);

        business = new Business();
        business.setId(1L);
        business.setName("Peluquería Elegance");
        business.setPhone("+595981123456");
        business.setEmail("contacto@peluqueriaelegance.com");
        business.setAddress("Asunción, Paraguay");
        business.setTimezone("America/Asuncion");

        lenient().when(currentUserProvider.getCurrentBusinessId()).thenReturn(1L);
    }

    private UpdateBusinessSettingsRequest buildValidRequest() {
        UpdateBusinessSettingsRequest request = new UpdateBusinessSettingsRequest();
        request.setName("Peluquería Elegance");
        request.setPhone("+595981123456");
        request.setEmail("contacto@peluqueriaelegance.com");
        request.setAddress("Asunción, Paraguay");
        request.setTimezone("America/Asuncion");
        return request;
    }

    // 1. Obtener Settings del Business autenticado.
    @Test
    void getSettingsReturnsCurrentBusinessSettings() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        BusinessSettingsResponse response = new BusinessSettingsResponse();
        response.setId(1L);
        response.setName("Peluquería Elegance");
        when(businessSettingsMapper.toResponse(business)).thenReturn(response);

        BusinessSettingsResponse result = businessSettingsService.getSettings();

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Peluquería Elegance");
    }

    // 2. Actualizar name.
    @Test
    void updatesName() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setName("Nuevo Nombre");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        verify(businessSettingsMapper).updateEntityFromRequest(request, business);
        verify(businessRepository).save(business);
    }

    // 3. Actualizar phone.
    @Test
    void updatesPhone() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone("+595981000000");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        assertThat(request.getPhone()).isEqualTo("+595981000000");
        verify(businessSettingsMapper).updateEntityFromRequest(request, business);
    }

    // 4. Quitar phone enviando vacío.
    @Test
    void removesPhoneWhenSentBlank() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone("   ");

        assertThat(request.getPhone()).isNull();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        verify(businessSettingsMapper).updateEntityFromRequest(request, business);
    }

    // 5. Actualizar email.
    @Test
    void updatesEmail() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setEmail("nuevo@example.com");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        assertThat(request.getEmail()).isEqualTo("nuevo@example.com");
        verify(businessSettingsMapper).updateEntityFromRequest(request, business);
    }

    // 6. Email inválido: normalización no afecta el valor, la validación de formato ocurre vía Bean Validation
    // (@Email) en el controller; a nivel de request, un valor con contenido nunca se convierte a null.
    @Test
    void emailWithContentIsNeverNormalizedToNull() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setEmail("no-es-un-email");

        assertThat(request.getEmail()).isEqualTo("no-es-un-email");
    }

    // 7. Quitar email.
    @Test
    void removesEmailWhenSentBlank() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setEmail("");

        assertThat(request.getEmail()).isNull();

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        verify(businessSettingsMapper).updateEntityFromRequest(request, business);
    }

    // 8. Actualizar address.
    @Test
    void updatesAddress() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setAddress("Nueva Dirección 123");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        assertThat(request.getAddress()).isEqualTo("Nueva Dirección 123");
        verify(businessSettingsMapper).updateEntityFromRequest(request, business);
    }

    // 9. Actualizar timezone válida.
    @Test
    void updatesTimezoneWhenValid() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setTimezone("America/Sao_Paulo");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);
        when(businessSettingsMapper.toResponse(business)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        assertThat(business.getTimezone()).isEqualTo("America/Sao_Paulo");
    }

    // 10. Timezone inválida.
    @Test
    void throwsBadRequestForInvalidTimezone() {
        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setTimezone("Not/AZone");

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));

        assertThatThrownBy(() -> businessSettingsService.updateSettings(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("La zona horaria indicada no es válida");

        verify(businessRepository, never()).save(any());
    }

    // 11. Usuario solo modifica su Business (el negocio se resuelve exclusivamente vía CurrentUserProvider).
    @Test
    void updateIsScopedToAuthenticatedBusinessOnly() {
        when(currentUserProvider.getCurrentBusinessId()).thenReturn(2L);

        Business otherBusiness = new Business();
        otherBusiness.setId(2L);
        otherBusiness.setTimezone("America/Asuncion");

        UpdateBusinessSettingsRequest request = buildValidRequest();

        when(businessRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(otherBusiness));
        when(businessRepository.save(otherBusiness)).thenReturn(otherBusiness);
        when(businessSettingsMapper.toResponse(otherBusiness)).thenReturn(new BusinessSettingsResponse());

        businessSettingsService.updateSettings(request);

        verify(businessRepository).findByIdAndActiveTrue(2L);
        verify(businessRepository, never()).findByIdAndActiveTrue(1L);
        verify(businessRepository).save(otherBusiness);
    }

    // 12 & 13. whatsappConfigured se delega en el mapper (probado en BusinessSettingsMapperTest);
    // acá se verifica que el service simplemente propaga lo que produce el mapper.
    @Test
    void propagatesWhatsappConfiguredFromMapper() {
        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        BusinessSettingsResponse response = new BusinessSettingsResponse();
        response.setWhatsappConfigured(true);
        when(businessSettingsMapper.toResponse(business)).thenReturn(response);

        BusinessSettingsResponse result = businessSettingsService.getSettings();

        assertThat(result.isWhatsappConfigured()).isTrue();
    }

    // 14. Response nunca expone secretos: BusinessSettingsResponse solo declara los campos permitidos.
    @Test
    void responseNeverExposesSecretFields() {
        var declaredFieldNames = java.util.Arrays.stream(BusinessSettingsResponse.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();

        assertThat(declaredFieldNames)
                .containsExactlyInAnyOrder("id", "name", "phone", "email", "address", "timezone",
                        "whatsappConfigured");
    }

    // Regresión del bug reportado: PUT con phone/email/address = null debía devolver 500 por una
    // restricción NOT NULL obsoleta en la base de datos. Con el mapper real (no mockeado), este test
    // reproduce el flujo completo del service y confirma que el Business termina con los tres campos en
    // null, sin NullPointerException ni valores ignorados.
    @Test
    void updateSettingsWithAllOptionalFieldsNullSetsThemToNullUsingRealMapper() {
        BusinessSettingsServiceImpl serviceWithRealMapper = new BusinessSettingsServiceImpl(
                businessRepository, new BusinessSettingsMapperImpl(), currentUserProvider);

        UpdateBusinessSettingsRequest request = buildValidRequest();
        request.setPhone(null);
        request.setEmail(null);
        request.setAddress(null);

        when(businessRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(business));
        when(businessRepository.save(business)).thenReturn(business);

        BusinessSettingsResponse result = serviceWithRealMapper.updateSettings(request);

        assertThat(business.getPhone()).isNull();
        assertThat(business.getEmail()).isNull();
        assertThat(business.getAddress()).isNull();
        assertThat(result.getPhone()).isNull();
        assertThat(result.getEmail()).isNull();
        assertThat(result.getAddress()).isNull();
    }

}
