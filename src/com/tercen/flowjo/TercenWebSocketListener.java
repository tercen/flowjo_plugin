package com.tercen.flowjo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tercen.model.base.TaskEventBase;
import com.tercen.model.base.Vocabulary;
import com.tercen.model.impl.CanceledState;
import com.tercen.model.impl.DoneState;
import com.tercen.model.impl.FailedState;
import com.tercen.model.impl.State;
import com.tercen.model.impl.TaskEvent;
import com.tercen.model.impl.TaskStateEvent;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import tercen.tson.TsonError;
import tercen.tson.jtson;

public final class TercenWebSocketListener extends WebSocketListener {

	private static final Logger logger = LogManager.getLogger();
	private CountDownLatch latch;
	private long baseTime = System.currentTimeMillis();
	public Throwable throwable = null;

	public TercenWebSocketListener() {
	}

	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		logger.debug("onOpen");
	}

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		logger.trace("MESSAGE: " + text);
	}

	@Override
	public void onMessage(WebSocket webSocket, ByteString bytes) {
		logger.trace("MESSAGE: " + bytes.hex());

		long currentTime = System.currentTimeMillis();
		if ((currentTime - baseTime) > 1000) {
			logger.debug("send ping");
			webSocket.send("__ping__");
			baseTime = currentTime;
		}

		LinkedHashMap map;
		try {
			map = (LinkedHashMap) jtson.decodeTSON(bytes.toByteArray());
			String kind = (String) map.get(Vocabulary.KIND);
			if (kind.equals("websocketdone")) {
				webSocket.send(bytes);
				webSocket.close(1000, "Disconnecting websocket");
				logger.debug("onMessage websocketdone received");
			} else {
				TaskEvent taskEvent = TaskEventBase.fromJson(map);
				if (taskEvent instanceof TaskStateEvent) {
					TaskStateEvent tse = (TaskStateEvent) taskEvent;
					State state = tse.state;
					if (state instanceof FailedState || state instanceof CanceledState || state instanceof DoneState) {
						logger.debug("onMessage final state");
						setThrowable(null);
						latch.countDown();
					}
				}
			}
		} catch (TsonError e) {
			logger.error(e.getMessage());
			setThrowable(e);
		} catch (IOException e) {
			logger.error(e.getMessage());
			setThrowable(e);
		}
	}

	@Override
	public void onClosing(WebSocket webSocket, int code, String reason) {
		logger.debug("CLOSE: " + code + " " + reason);
		webSocket.close(1000, null);
		setThrowable(null);
		latch.countDown();
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response) {
		logger.debug("onFailure");
		webSocket.close(1000, null);
		setThrowable(t);
		latch.countDown();
	}

	public void setThrowable(Throwable t) {
		this.throwable = t;
	}

	public Throwable getThrowable() {
		return this.throwable;
	}

	public void setCountDownLatch(CountDownLatch latch) {
		this.latch = latch;
	}

	public boolean hasError() {
		return (getThrowable() != null);
	}
}