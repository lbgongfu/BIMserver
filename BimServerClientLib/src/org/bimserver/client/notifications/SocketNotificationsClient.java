package org.bimserver.client.notifications;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

import org.bimserver.shared.meta.SService;
import org.bimserver.shared.pb.ProtocolBuffersMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SocketNotificationsClient extends NotificationsClient {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocketNotificationsClient.class);
	private ProtocolBuffersMetaData protocolBuffersMetaData;
	private InetSocketAddress address;
	private SService sService;
	private boolean running;
	private ServerSocket serverSocket;
	private final Set<Handler> handlers = new HashSet<Handler>();
	private Thread thread;

	public void start() {
		thread = new Thread(new Runnable() {
			@Override
			public void run() {
				running = true;
				try {
					while (running) {
						Socket socket = serverSocket.accept();
						notifyConnect();
						Handler handler = new Handler(SocketNotificationsClient.this, socket, multiCastNotificationImpl, protocolBuffersMetaData, sService);
						handlers.add(handler);
						handler.start();
					}
				} catch (IOException e) {
					LOGGER.error("", e);
				}
				notifyDisconnect();
			}
		});
		thread.start();
	}

	public void connect(ProtocolBuffersMetaData protocolBuffersMetaData, SService sService, InetSocketAddress address) {
		this.protocolBuffersMetaData = protocolBuffersMetaData;
		this.sService = sService;
		this.address = address;
	}

	public void disconnect() {
		running = false;
		thread.interrupt();
		for (Handler handler : handlers) {
			handler.close();
		}
		try {
			serverSocket.close();
		} catch (IOException e) {
			LOGGER.error("", e);
		}
	}

	public boolean isRunning() {
		return running;
	}

	public void startAndWaitForInit() {
		try {
			running = true;
			serverSocket = new ServerSocket();
			serverSocket.bind(address);
			start();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
}