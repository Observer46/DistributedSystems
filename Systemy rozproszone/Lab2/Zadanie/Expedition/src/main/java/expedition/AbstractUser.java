package expedition;

import com.googlecode.lanterna.TextColor;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public abstract class AbstractUser {
    public static final String EXCHANGE_NAME = "expedition-exchange";

    protected String name;
    protected ChatTerminal terminal;
    protected Connection connection;
    protected Channel channel;
    protected String myQueue;

    protected AbstractUser(String role, TextColor chatColor) throws IOException, TimeoutException {
        this.terminal = new ChatTerminal(chatColor);
        this.terminal.printBufferForClient(role);
        this.terminal.printBufferForClient("Enter " + role.substring(1, role.length() - 1) + " name: ");
        this.name = this.terminal.getInputBlocking();

        // connection & channel
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();

        // exchange
        this.channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC);
    }

    protected void declareQueue(String queueName) throws IOException {
        // queue
        this.channel.queueDeclare(queueName, false, false, false, null);

    }

    protected void bindQueue(String queueName, String key) throws IOException {
        this.channel.queueBind(queueName, EXCHANGE_NAME, key);
    }

    protected Consumer defaultConsumer(){
        final ChatTerminal terminal = this.terminal;
        return new DefaultConsumer(this.channel){
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                String[] msgContent = new String(body, StandardCharsets.UTF_8).split("&");
                String role = msgContent[0];
                String name = msgContent[1];
                String msg  = msgContent[2];
                terminal.printBufferForClient(role + "@" + name + ": " + msg);
            }
        };
    }

    protected void runQueueThread(final String queueName, final Consumer consumer){
        Thread adminListener = new Thread(){
            @Override
            public void run() {
                try {
                    channel.basicConsume(queueName, true, consumer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        adminListener.start();
    }

    protected void sendMsg(String msg, String key) throws IOException {
        channel.basicPublish(EXCHANGE_NAME, key, null, msg.getBytes(StandardCharsets.UTF_8));
        terminal.printBufferForClient("Sent msg with key: " + key);
    }
}
