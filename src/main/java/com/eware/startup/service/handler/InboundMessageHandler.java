package com.eware.startup.service.handler;


import com.eware.startup.service.mapper.event.UserEvent;
import com.eware.startup.service.mapper.pojo.User;
import com.eware.startup.domain.entity.ClientEntity;
import com.eware.startup.service.Processor;
import com.eware.startup.service.business.WebSocketSessionService;
import com.eware.startup.common.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;


@Component
public class InboundMessageHandler implements WebSocketHandler {

    private Processor chatServiceStreams;
    private WebSocketSessionService webSocketSessionService;

    private static final Logger LOG = LoggerFactory.getLogger(InboundMessageHandler.class);

    @Autowired
    public InboundMessageHandler(Processor chatServiceStreams, WebSocketSessionService webSocketSessionService) {
        this.chatServiceStreams = chatServiceStreams;
        this.webSocketSessionService = webSocketSessionService;
    }

    /**
     * Handle incoming messages
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        ClientEntity clientEntity = webSocketSessionService.map(session);
        return session
            .receive()
            .log(session.getId() + "-i-incoming-chat-message")
            .map(WebSocketMessage::getPayloadAsText)
            .log(session.getId() + "-i-convert-to-text")
            .flatMap(message -> broadcast(message, clientEntity))
            .log(session.getId() + "-i-broadcast-to-broker")
            .doOnComplete(() -> disconnect(clientEntity))
            .then();
    }

    private void disconnect(ClientEntity clientEntity) {
        //send notification trough the broker that the user disconnected

        String user = clientEntity.getUsername();

        UserEvent userEvent = UserEvent.type(UserEvent.Type.USER_LEFT)
                                       .withPayload()
                                       .user(new User(user, "dsf"))
                                       .build();

        chatServiceStreams.clientToBroker().send(
            MessageBuilder
                .withPayload(UserUtil.toJSON(userEvent))
                .setHeader(Processor.USER_HEADER, user)
                .build());
        LOG.info("Client disconnected  {}", clientEntity);
    }


    /**
     * send incoming websocket message payload to the broker
     *
     * @param message
     * @param clientEntity
     * @return
     */
    private Mono<?> broadcast(String message, ClientEntity clientEntity) {
        return Mono.fromRunnable(() ->
                                     chatServiceStreams.clientToBroker().send(
                                         MessageBuilder
                                             .withPayload(message)
                                             //set the header on the broker side that contains the user name to be verified later
                                             .setHeader(Processor.USER_HEADER, clientEntity.getUsername())
                                             .build())
        );
    }

}