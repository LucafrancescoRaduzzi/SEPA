package it.unibo.arces.wot.sepa.apps.chat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPAPropertiesException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPAProtocolException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;

public class BasicClient extends ChatClient {
	private static final Logger logger = LogManager.getLogger();

	private String user;
	private Users users;
	private int messages = 10;
	private int notifications = 0;
	private int expectedNotifications = 0;
	
	public BasicClient(String userURI, Users users, int messages,Timings timings)
			throws SEPAProtocolException, SEPASecurityException, SEPAPropertiesException {
		super(userURI,timings);
		this.user = userURI;
		this.users = users;
		this.messages = messages;
		if (users.getUsers().size() > 1)
			this.expectedNotifications = messages * (users.getUsers().size()-1);
		else
			this.expectedNotifications = messages;
	}

	@Override
	public void run() {
		while (!joinChat()) {
			try {
				logger.info("Joining the chat...");
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				return;
			}
		}
		logger.info("Chat joined!");

		for (int i = 0; i < messages; i++) {
			for (String receiver : users.getUsers()) {
				if (receiver.equals(user) && users.getUsers().size() > 1)
					continue;
				sendMessage(receiver, "MSG #" + i);
			}
		}

		try {
			synchronized (this) {
				wait();
			}
		} catch (InterruptedException e) {
		}
	}

	@Override
	public void onMessage(String from, String message) {
			
	}

	@Override
	public void onMessageRemoved(String messageURI) {
		notifications++;
		if (notifications == expectedNotifications) {
			synchronized(this) {
				notify();
			}
		}
		
	}
}
