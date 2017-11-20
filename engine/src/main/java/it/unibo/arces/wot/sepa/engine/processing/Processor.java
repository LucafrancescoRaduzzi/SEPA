/*  This class implements the processing of the requests coming form the scheduler
 * 
 * Author: Luca Roffia (luca.roffia@unibo.it)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package it.unibo.arces.wot.sepa.engine.processing;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.NoSuchElementException;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import org.apache.logging.log4j.Logger;

import com.github.andrewoma.dexx.collection.List;

import it.unibo.arces.wot.sepa.commons.protocol.SPARQL11Properties;

import it.unibo.arces.wot.sepa.commons.request.QueryRequest;
import it.unibo.arces.wot.sepa.commons.request.SubscribeRequest;
import it.unibo.arces.wot.sepa.commons.request.UnsubscribeRequest;
import it.unibo.arces.wot.sepa.commons.request.UpdateRequest;
import it.unibo.arces.wot.sepa.commons.response.QueryResponse;
import it.unibo.arces.wot.sepa.commons.response.Response;
import it.unibo.arces.wot.sepa.commons.response.UpdateResponse;
import it.unibo.arces.wot.sepa.engine.bean.ProcessorBeans;
import it.unibo.arces.wot.sepa.engine.bean.SEPABeans;
import it.unibo.arces.wot.sepa.engine.core.EngineProperties;
import it.unibo.arces.wot.sepa.engine.core.EventHandler;

import org.apache.logging.log4j.LogManager;

public class Processor extends Observable implements ProcessorMBean, Observer {
	private static final Logger logger = LogManager.getLogger("Processor");

	// Processors
	private final UpdateProcessor updateProcessor;
	private final QueryProcessor queryProcessor;
	private SPUManager spuManager;

	// Update queue
	private ConcurrentLinkedQueue<UpdateRequest> updateRequestQueue = new ConcurrentLinkedQueue<UpdateRequest>();

	// Response queue
	private ConcurrentLinkedQueue<Response> responseQueue = new ConcurrentLinkedQueue<Response>();

	public Processor(SPARQL11Properties endpointProperties, EngineProperties properties)
			throws MalformedObjectNameException, InstanceAlreadyExistsException, MBeanRegistrationException,
			NotCompliantMBeanException, InvalidKeyException, FileNotFoundException, NoSuchElementException,
			NullPointerException, ClassCastException, NoSuchAlgorithmException, NoSuchPaddingException,
			IllegalBlockSizeException, BadPaddingException, IOException, IllegalArgumentException, URISyntaxException {

		// Update processor
		updateProcessor = new UpdateProcessor(endpointProperties);

		// Query processor
		queryProcessor = new QueryProcessor(endpointProperties);

		// SPU manager
		spuManager = new SPUManager(endpointProperties, properties);
		spuManager.addObserver(this);

		Thread th = new Thread() {
			public void run() {
				while (true) {
					Response ret;
					while ((ret = responseQueue.poll()) != null) {
						setChanged();
						notifyObservers(ret);
					}
					synchronized (responseQueue) {
						try {
							responseQueue.wait();
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			}
		};
		th.setName("SEPA Processor responder");
		th.start();
		
		Thread updateProcessing = new Thread() {
			public void run() {
				while (true) {
					UpdateRequest request;
					while ((request = updateRequestQueue.poll()) != null) {
						// Process update request
						Response ret = updateProcessor.process(request, 0); 						

						if (ret.isUpdateResponse()) {
							spuManager.process((UpdateResponse) ret);

							// Wait for subscriptions processing end
							synchronized (updateProcessor) {
								try {
									updateProcessor.wait();
								} catch (InterruptedException e) {
									return;
								}
							}
						}

						// Notify END-OF-UPDATE
						synchronized (responseQueue) {
							responseQueue.offer(ret);
							responseQueue.notify();
						}
					}

					synchronized (updateRequestQueue) {
						try {
							updateRequestQueue.wait();
						} catch (InterruptedException e) {
							return;
						}
					}
				}
			}
		};
		updateProcessing.setName("SEPA Update processing");
		updateProcessing.start();

		SEPABeans.registerMBean("SEPA:type=" + this.getClass().getSimpleName(), this);

		ProcessorBeans.setEndpoint(endpointProperties);
	}

	public void processQuery(QueryRequest request) {
		logger.debug(request);
		ProcessorBeans.newRequest(request);
		Thread queryProcessing = new Thread() {
			public void run() {
				Response ret = queryProcessor.process(request,0);
				synchronized (responseQueue) {
					responseQueue.offer(ret);
					responseQueue.notify();
				}
			}
		};
		queryProcessing.setName("QueryProcessing#"+request.getToken());
		queryProcessing.start();
	}

	public void processSubscribe(SubscribeRequest request, EventHandler handler) {
		logger.debug(request);
		ProcessorBeans.newRequest(request);
		try {
			spuManager.subscribe(request, handler);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void processUnsubscribe(UnsubscribeRequest request) {
		logger.debug(request);
		ProcessorBeans.newRequest(request);
		spuManager.unsubscribe(request);
	}

	public void processUpdate(UpdateRequest request) {
		logger.debug(request);
		ProcessorBeans.newRequest(request);

		synchronized (updateRequestQueue) {
			updateRequestQueue.offer(request);
			updateRequestQueue.notify();
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		if (arg.getClass().equals(SPUManagerNotify.class)) {
			// SPU Manager
			SPUManagerNotify notify = (SPUManagerNotify) arg;

			if (notify.isEndOfProcessing()) {
				// UPDATE PROCESSING ENDED
				synchronized (updateProcessor) {
					updateProcessor.notify();
				}
			} else {
				// SUBSCRIBE/UNSUBSCRIBE response
				synchronized (responseQueue) {
					responseQueue.offer(notify.getResponse());
					responseQueue.notify();
				}
			}
			return;
		}

		synchronized (responseQueue) {
			responseQueue.offer((Response) arg);
			responseQueue.notify();
		}
	}

	@Override
	public void reset() {
		ProcessorBeans.reset();
	}

	@Override
	public float getTimings_UpdateTime_ms() {
		return ProcessorBeans.getUpdateTime_ms();
	}

	@Override
	public float getTimings_QueryTime_ms() {
		return ProcessorBeans.getQueryTime_ms();
	}

	@Override
	public long getProcessedRequests() {
		return ProcessorBeans.getProcessedRequests();
	}

	@Override
	public long getProcessedQueryRequests() {
		return ProcessorBeans.getProcessedQueryRequests();
	}

	@Override
	public long getProcessedSPURequests() {
		return ProcessorBeans.getProcessedSPURequests();
	}

	@Override
	public long getProcessedUpdateRequests() {
		return ProcessorBeans.getProcessedUpdateRequests();
	}

	@Override
	public float getTimings_UpdateTime_Min_ms() {
		return ProcessorBeans.getTimings_UpdateTime_Min_ms();
	}

	@Override
	public float getTimings_UpdateTime_Average_ms() {
		return ProcessorBeans.getTimings_UpdateTime_Average_ms();
	}

	@Override
	public float getTimings_UpdateTime_Max_ms() {
		return ProcessorBeans.getTimings_UpdateTime_Max_ms();
	}

	@Override
	public float getTimings_QueryTime_Min_ms() {
		return ProcessorBeans.getTimings_QueryTime_Min_ms();
	}

	@Override
	public float getTimings_QueryTime_Average_ms() {
		return ProcessorBeans.getTimings_QueryTime_Average_ms();
	}

	@Override
	public float getTimings_QueryTime_Max_ms() {
		return ProcessorBeans.getTimings_QueryTime_Max_ms();
	}
}
