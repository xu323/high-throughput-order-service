package com.xu.orderservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology：
 * <pre>
 *   exchange: order.exchange  (topic)
 *     ├─ order.created       → order.created.queue
 *     ├─ order.paid          → order.paid.queue
 *     ├─ order.cancelled     → order.cancelled.queue
 *     └─ inventory.deducted  → inventory.deducted.queue
 *
 *   每個 queue 配一個 dead letter queue：
 *   <queue>.dlq via order.dlx (direct)
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "order.exchange";
    public static final String DLX_EXCHANGE = "order.dlx";

    public static final String RK_ORDER_CREATED = "order.created";
    public static final String RK_ORDER_PAID = "order.paid";
    public static final String RK_ORDER_CANCELLED = "order.cancelled";
    public static final String RK_INVENTORY_DEDUCTED = "inventory.deducted";

    public static final String Q_ORDER_CREATED = "order.created.queue";
    public static final String Q_ORDER_PAID = "order.paid.queue";
    public static final String Q_ORDER_CANCELLED = "order.cancelled.queue";
    public static final String Q_INVENTORY_DEDUCTED = "inventory.deducted.queue";

    public static final String DLQ_SUFFIX = ".dlq";

    // ---------- Exchange ----------
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange dlxExchange() {
        return new TopicExchange(DLX_EXCHANGE, true, false);
    }

    // ---------- Queues + DLQs ----------
    @Bean public Queue orderCreatedQueue()      { return buildQueue(Q_ORDER_CREATED); }
    @Bean public Queue orderPaidQueue()         { return buildQueue(Q_ORDER_PAID); }
    @Bean public Queue orderCancelledQueue()    { return buildQueue(Q_ORDER_CANCELLED); }
    @Bean public Queue inventoryDeductedQueue() { return buildQueue(Q_INVENTORY_DEDUCTED); }

    @Bean public Queue orderCreatedDlq()      { return new Queue(Q_ORDER_CREATED + DLQ_SUFFIX, true); }
    @Bean public Queue orderPaidDlq()         { return new Queue(Q_ORDER_PAID + DLQ_SUFFIX, true); }
    @Bean public Queue orderCancelledDlq()    { return new Queue(Q_ORDER_CANCELLED + DLQ_SUFFIX, true); }
    @Bean public Queue inventoryDeductedDlq() { return new Queue(Q_INVENTORY_DEDUCTED + DLQ_SUFFIX, true); }

    private Queue buildQueue(String name) {
        return QueueBuilder.durable(name)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", name + DLQ_SUFFIX)
                .build();
    }

    // ---------- Bindings ----------
    @Bean public Binding bOrderCreated()      { return BindingBuilder.bind(orderCreatedQueue()).to(orderExchange()).with(RK_ORDER_CREATED); }
    @Bean public Binding bOrderPaid()         { return BindingBuilder.bind(orderPaidQueue()).to(orderExchange()).with(RK_ORDER_PAID); }
    @Bean public Binding bOrderCancelled()    { return BindingBuilder.bind(orderCancelledQueue()).to(orderExchange()).with(RK_ORDER_CANCELLED); }
    @Bean public Binding bInventoryDeducted() { return BindingBuilder.bind(inventoryDeductedQueue()).to(orderExchange()).with(RK_INVENTORY_DEDUCTED); }

    @Bean public Binding bDlqCreated()       { return BindingBuilder.bind(orderCreatedDlq()).to(dlxExchange()).with(Q_ORDER_CREATED + DLQ_SUFFIX); }
    @Bean public Binding bDlqPaid()          { return BindingBuilder.bind(orderPaidDlq()).to(dlxExchange()).with(Q_ORDER_PAID + DLQ_SUFFIX); }
    @Bean public Binding bDlqCancelled()     { return BindingBuilder.bind(orderCancelledDlq()).to(dlxExchange()).with(Q_ORDER_CANCELLED + DLQ_SUFFIX); }
    @Bean public Binding bDlqInventory()     { return BindingBuilder.bind(inventoryDeductedDlq()).to(dlxExchange()).with(Q_INVENTORY_DEDUCTED + DLQ_SUFFIX); }

    // ---------- Converter / Template ----------
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(converter);
        return t;
    }
}
