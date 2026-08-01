package com.victor.appointmentmanager.api.modules.services.mapper;

import com.victor.appointmentmanager.api.common.mapper.BaseMapper;
import com.victor.appointmentmanager.api.modules.services.dto.request.CreateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.request.UpdateServiceRequest;
import com.victor.appointmentmanager.api.modules.services.dto.response.ServiceResponse;
import com.victor.appointmentmanager.api.modules.services.entity.Service;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface ServiceMapper extends BaseMapper<Service, ServiceResponse> {

    @Mapping(target = "businessId", source = "business.id")
    @Override
    ServiceResponse toDto(Service entity);

    Service toEntity(CreateServiceRequest request);

    void updateEntityFromRequest(UpdateServiceRequest request, @MappingTarget Service service);

}
