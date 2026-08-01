package com.victor.appointmentmanager.api.modules.customers.mapper;

import com.victor.appointmentmanager.api.common.mapper.BaseMapper;
import com.victor.appointmentmanager.api.modules.customers.dto.request.CreateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.request.UpdateCustomerRequest;
import com.victor.appointmentmanager.api.modules.customers.dto.response.CustomerResponse;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer, CustomerResponse> {

    @Mapping(target = "businessId", source = "business.id")
    @Override
    CustomerResponse toDto(Customer entity);

    Customer toEntity(CreateCustomerRequest request);

    void updateEntityFromRequest(UpdateCustomerRequest request, @MappingTarget Customer customer);

}
