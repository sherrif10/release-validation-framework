package org.ihtsdo.rvf.messaging;

import java.util.Arrays;
import java.util.Calendar;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.command.ActiveMQQueue;
import org.ihtsdo.rvf.autoscaling.InstanceManager;
import org.ihtsdo.rvf.execution.service.impl.ValidationRunConfig;
import org.ihtsdo.rvf.execution.service.impl.ValidationRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.amazonaws.services.ec2.model.Instance;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
@Service
public class RvfValidationMessageConsumer {
	private static final String CONSUMER_PREFETCH_SIZE = "?consumer.prefetchSize=1";
	private static final String EC2_INSTANCE_ID_URL = "http://169.254.169.254/latest/meta-data/instance-id";
	private static final long FITY_NINE_MINUTES = 59*60*1000;
	private static final long HOUR_IN_MILLIS = 60*60*1000;
	private String queueName;
	@Autowired
	private ValidationRunner runner;
	private Logger logger = LoggerFactory.getLogger(getClass());
	@Autowired
	private ConnectionFactory connectionFactory;
	private boolean isWorker;
	@Autowired
	private InstanceManager instanceManager;
	private boolean isEc2Instance;
	private static Instance instance;
	private static boolean isValidationRunning = false;
	
	public RvfValidationMessageConsumer( String queueName,Boolean isRvfWorker, Boolean ec2Instance) {
		isWorker = isRvfWorker.booleanValue();
		this.queueName = queueName;
		this.isEc2Instance = ec2Instance.booleanValue();
	}
	
	public void start() {
		logger.info("isRvfWorker instance:" + isWorker);
		if (isWorker) {
			Thread thread = new Thread (new Runnable() {
				
				@Override
				public void run() {
					consumeMessage();
				}
			});
			thread.start();
			logger.info("RvfWorker instance started at:" + Calendar.getInstance().getTime());
		}
		
	}
	
	private void consumeMessage() {
		Connection connection = null;
		MessageConsumer consumer = null;
		Destination destination = new ActiveMQQueue(queueName + CONSUMER_PREFETCH_SIZE);
		Session session = null;
		try {
			connection =  connectionFactory.createConnection();
			connection.start();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
			consumer = session.createConsumer(destination);
			consumer.setMessageListener(new MessageListener() {

				@Override
				public void onMessage(Message message) {
					if (message instanceof TextMessage) {
						runValidation((TextMessage)message);
					}
				}
			});
			
			while (!shutDown(consumer)) {
				try {
					Thread.sleep(30000);
				} catch (InterruptedException e) {
					logger.error("Consumer thread is interupted", e);
				}
			}
			
		} catch (JMSException e) {
			logger.error("Error when consuming RVF validaiton message.", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (JMSException e) {
					logger.error("Error when closing message queue connection.", e);
				}
			}
			if (consumer != null) {
				try {
					consumer.close();
				} catch (JMSException e) {
					logger.error("Error when closing message consumer.", e);
				}
			}
			if (session != null) {
				try {
					session.close();
				} catch (JMSException e) {
					logger.error("Error when closing session.", e);
				}
			}
		}
	}
	
	public boolean shutDown(MessageConsumer consumer) {
		if (!isEc2Instance) {
			return false;
		} else {
			if (instance == null) {
				instance = instanceManager.getInstanceById(getInstanceId());
			}
			if (!isValidationRunning) {
				//only shutdown when no message to process and close to the hourly mark
				if (((Calendar.getInstance().getTimeInMillis() - instance.getLaunchTime().getTime()) % HOUR_IN_MILLIS) >= FITY_NINE_MINUTES ) {
					logger.info("Shut down message consumer as no messages left to process in queue and it is approaching to hourly mark.");
					try {
						consumer.close();
					} catch (JMSException e) {
						logger.error("Failed to close message consumer!", e);
					}
					logger.info("Instance total running time in minutes:" + ((System.currentTimeMillis() - instance.getLaunchTime().getTime()) / 60*1000));
					logger.info("Instance will be terminated");
					instanceManager.terminate(Arrays.asList(instance.getInstanceId()));
					return true;
				}
			}
			return false;
		}
	}
	
	
	private String getInstanceId() {
		String instanceId = null;
		try {
			RestTemplate restTemplate = new RestTemplate();
			instanceId = restTemplate.getForObject(EC2_INSTANCE_ID_URL, String.class);
			logger.info("Current instance id is:" + instanceId);
		} catch(Exception e) {
			logger.error("Failed to get instance id", e);
		}
		return instanceId;
	}
	
	private void runValidation(final TextMessage incomingMessage) {
		isValidationRunning = true;
		Gson gson = new Gson();
		ValidationRunConfig config = null;
		try {
			config = gson.fromJson(incomingMessage.getText(), ValidationRunConfig.class);
			logger.info("validation config:" + config);
		} catch (JsonSyntaxException | JMSException e) {
			logger.error("JMS message listener error:", e);
		}
		if ( config != null) {
			long start = System.currentTimeMillis();
			runner.run(config);
			long end = System.currentTimeMillis();
			logger.info("last validation taken in seconds:" + (end-start) /1000);
		} else {
			logger.error("Null validation config found for message:" + incomingMessage);
		}
		isValidationRunning = false;
	}

}
