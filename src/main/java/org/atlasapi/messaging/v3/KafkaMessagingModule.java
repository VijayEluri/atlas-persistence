package org.atlasapi.messaging.v3;

import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.metabroadcast.common.queue.MessageConsumerFactory;
import com.metabroadcast.common.queue.MessageSenderFactory;
import com.metabroadcast.common.queue.kafka.KafkaConsumer;
import com.metabroadcast.common.queue.kafka.KafkaMessageConsumerFactory;
import com.metabroadcast.common.queue.kafka.KafkaMessageSenderFactory;

import static com.google.common.base.Preconditions.checkNotNull;

@Configuration
public class KafkaMessagingModule implements MessagingModule {
    
    @Value("${messaging.broker.url}")
    private String brokerUrl;
    @Value("${messaging.zookeeper}")
    private String zookeeper;
    @Value("${messaging.system}")
    private String messagingSystem;

    @Value("${messaging.backOffIntervalMillis}")
    private Long backOffIntervalMillis;
    @Value("${messaging.maxBackOffMillis}")
    private Long maxBackOffMillis;

    public KafkaMessagingModule(
            String brokerUrl,
            String zookeeper,
            String messagingSystem,
            Long backOffIntervalMillis,
            Long maxBackOffMillis
    ) {
        this.brokerUrl = checkNotNull(brokerUrl);
        this.zookeeper = checkNotNull(zookeeper);
        this.messagingSystem = checkNotNull(messagingSystem);
        this.backOffIntervalMillis = checkNotNull(backOffIntervalMillis);
        this.maxBackOffMillis = checkNotNull(maxBackOffMillis);
    }

    public KafkaMessagingModule() { }
    
    @Override
    @Bean
    public MessageSenderFactory messageSenderFactory() {
        return new KafkaMessageSenderFactory(brokerUrl, messagingSystem);
    }
    
    @Override
    @Bean
    public MessageConsumerFactory<KafkaConsumer> messageConsumerFactory() {
        return new KafkaMessageConsumerFactory(
                zookeeper,
                messagingSystem,
                Duration.millis(backOffIntervalMillis),
                Duration.millis(maxBackOffMillis)
        );
    }
    
}