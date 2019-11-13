package com.example.jms.perf.demo;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jms.DefaultJmsListenerContainerFactoryConfigurer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.SimpleMessageConverter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@SpringBootApplication
@EnableJms
public class DemoApplication {

    public static final int SESSION_CACHE_SIZE = 2;
    public static final String CONCURRENT_LISTENERS = "16";
    public static final boolean CACHE_CONSUMERS = true;

    public static void main(String[] args) throws IOException {
        long t1 = System.currentTimeMillis();
        String typeProfile = "";
        typeProfile = "sender";
        typeProfile = "receiver";

        String connectionProfile = "";
        connectionProfile = "cached";
//        connectionProfile = "single";

        String profiles = typeProfile + "," + connectionProfile;
        System.setProperty(AbstractEnvironment.ACTIVE_PROFILES_PROPERTY_NAME, profiles);
        ConfigurableApplicationContext context = SpringApplication.run(DemoApplication.class, args);

        if (typeProfile.equals("sender")) {
            JmsTemplate jmsTemplate = context.getBean(JmsTemplate.class);
            sendMessage(jmsTemplate);
        }

        long t2 = System.currentTimeMillis();

        System.out.print("Diff: " + (t2 - t1) / 1000.0);
    }

    private static void sendMessage(JmsTemplate jmsTemplate) throws IOException {
        InputStream resourceAsStream = DemoApplication.class.getResourceAsStream("/message.xml");
        String messageContent = readFromInputStream(resourceAsStream);

        long lt1 = System.currentTimeMillis();
        for (int i = 0; i < 100000; ++i) {
            if (i % 100 == 0) {
                long lt2 = System.currentTimeMillis();
                System.out.println("Iteration: " + i + ": " + (lt2 - lt1) / 1000.0);
                lt1 = System.currentTimeMillis();
            }
            jmsTemplate.send("DEV.QUEUE.3", session -> session.createTextMessage(messageContent));
        }
    }

    private static String readFromInputStream(InputStream resourceAsStream) throws IOException {
        StringBuilder resultStringBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resourceAsStream))) {
            String line;
            while ((line = br.readLine()) != null) {
                resultStringBuilder.append(line).append("\n");
            }
        }
        return resultStringBuilder.toString();
    }

    @Bean
    public MQQueueConnectionFactory mqQueueConnectionFactory() {
        MQQueueConnectionFactory mqQueueConnectionFactory = new MQQueueConnectionFactory();
        mqQueueConnectionFactory.setHostName("localhost");
        try {
            mqQueueConnectionFactory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
            mqQueueConnectionFactory.setCCSID(1208);
            mqQueueConnectionFactory.setChannel("DEV.APP.SVRCONN");
            mqQueueConnectionFactory.setPort(1414);
            mqQueueConnectionFactory.setQueueManager("QM1");
//            mqQueueConnectionFactory.setSSLCipherSuite("TLS_RSA_WITH_AES_128_CBC_SHA256");
        } catch (JMSException e) {
            e.printStackTrace();
        }
        return mqQueueConnectionFactory;
    }

    @Bean
//    @Primary
    public UserCredentialsConnectionFactoryAdapter userCredentialsConnectionFactoryAdapter(MQConnectionFactory mqConnectionFactory) {
        UserCredentialsConnectionFactoryAdapter userCredentialsConnectionFactoryAdapter = new UserCredentialsConnectionFactoryAdapter();
        userCredentialsConnectionFactoryAdapter.setUsername("app");
        userCredentialsConnectionFactoryAdapter.setPassword("passw0rd");
        userCredentialsConnectionFactoryAdapter.setTargetConnectionFactory(mqConnectionFactory);
        return userCredentialsConnectionFactoryAdapter;
    }

    @Bean
    @Primary
    @Profile("cached")
    public CachingConnectionFactory cachingConnectionFactory(UserCredentialsConnectionFactoryAdapter userCredentialsConnectionFactoryAdapter) {
        CachingConnectionFactory cachingConnectionFactory = new CachingConnectionFactory();
        cachingConnectionFactory.setTargetConnectionFactory(userCredentialsConnectionFactoryAdapter);
        cachingConnectionFactory.setReconnectOnException(true);
        cachingConnectionFactory.setCacheConsumers(CACHE_CONSUMERS);
        cachingConnectionFactory.setSessionCacheSize(SESSION_CACHE_SIZE);
        return cachingConnectionFactory;
    }

    @Bean
    @Primary
    @Profile("single")
    public SingleConnectionFactory singleConnectionFactory(UserCredentialsConnectionFactoryAdapter userCredentialsConnectionFactoryAdapter) {
        SingleConnectionFactory singleConnectionFactory = new SingleConnectionFactory();
        singleConnectionFactory.setTargetConnectionFactory(userCredentialsConnectionFactoryAdapter);
        singleConnectionFactory.setReconnectOnException(true);
        return singleConnectionFactory;
    }

    @Bean
    public MessageConverter messageConverter() {
        return new SimpleMessageConverter();
    }

    @Bean
    public DefaultJmsListenerContainerFactory myFactory(ConnectionFactory singleConnectionFactory,
                                                        DefaultJmsListenerContainerFactoryConfigurer configurer,
                                                        PlatformTransactionManager jmsTransactionManager) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        configurer.configure(factory, singleConnectionFactory);
        factory.setConcurrency(CONCURRENT_LISTENERS);
        factory.setTransactionManager(jmsTransactionManager);
        return factory;
    }

    @Bean
    public PlatformTransactionManager jmsTransactionManager(ConnectionFactory singleConnectionFactory) {
        JmsTransactionManager transactionManager = new JmsTransactionManager();
        transactionManager.setConnectionFactory(singleConnectionFactory);
        return transactionManager;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory singleConnectionFactory) {
        JmsTemplate jmsTemplate = new JmsTemplate();
        jmsTemplate.setConnectionFactory(singleConnectionFactory);
        return jmsTemplate;
    }
}
