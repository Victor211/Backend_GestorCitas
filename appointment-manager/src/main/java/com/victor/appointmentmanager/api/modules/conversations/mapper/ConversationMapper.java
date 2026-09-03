package com.victor.appointmentmanager.api.modules.conversations.mapper;

import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationMessageResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationModeResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationReadResponse;
import com.victor.appointmentmanager.api.modules.conversations.dto.response.ConversationSummaryResponse;
import com.victor.appointmentmanager.api.modules.conversations.entity.Conversation;
import com.victor.appointmentmanager.api.modules.conversations.entity.ConversationMessage;
import com.victor.appointmentmanager.api.modules.customers.entity.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * {@code Conversation.customerId} es un {@code Long} plano (sin relación JPA, ver Fase 1), por lo
 * que {@code customerName} no puede resolverse con un simple {@code source = "customer.xxx"} como
 * en {@link com.victor.appointmentmanager.api.modules.dashboard.mapper.DashboardMapper}: acá el
 * Customer (o {@code null}, si todavía no existe) se resuelve aparte en
 * {@code ConversationQueryServiceImpl} y se pasa como segundo parámetro de origen.
 */
@Mapper
public interface ConversationMapper {

    @Mapping(target = "id", source = "conversation.id")
    @Mapping(target = "customerId", source = "conversation.customerId")
    @Mapping(target = "customerName", source = "customer", qualifiedByName = "toCustomerFullName")
    @Mapping(target = "senderPhone", source = "conversation.senderPhone")
    @Mapping(target = "status", source = "conversation.status")
    @Mapping(target = "mode", source = "conversation.mode")
    @Mapping(target = "lastMessageAt", source = "conversation.lastMessageAt")
    @Mapping(target = "lastMessagePreview", source = "conversation.lastMessagePreview")
    @Mapping(target = "unreadCount", source = "conversation.unreadCount")
    ConversationSummaryResponse toSummaryResponse(Conversation conversation, Customer customer);

    ConversationMessageResponse toMessageResponse(ConversationMessage message);

    ConversationReadResponse toReadResponse(Conversation conversation);

    ConversationModeResponse toModeResponse(Conversation conversation);

    @Named("toCustomerFullName")
    default String toCustomerFullName(Customer customer) {
        if (customer == null) {
            return null;
        }
        String firstName = customer.getFirstName() != null ? customer.getFirstName() : "";
        String lastName = customer.getLastName() != null ? customer.getLastName() : "";
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }

}
