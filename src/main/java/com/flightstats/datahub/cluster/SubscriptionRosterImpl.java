package com.flightstats.datahub.cluster;

import com.flightstats.datahub.service.InsertionTopicProxy;
import com.flightstats.datahub.service.eventing.Consumer;
import com.flightstats.datahub.service.eventing.SubscriptionRoster;
import com.flightstats.datahub.util.DataHubKeyRenderer;
import com.google.inject.Inject;
import com.hazelcast.core.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionRosterImpl implements SubscriptionRoster {

	private final static Logger logger = LoggerFactory.getLogger(SubscriptionRosterImpl.class);
	private final InsertionTopicProxy insertionTopicProxy;
	private final DataHubKeyRenderer keyRenderer;
	private final ConcurrentHashMap<ChannelConsumer, MessageListener<String>> consumerToMessageListener = new ConcurrentHashMap<>();

	@Inject
	public SubscriptionRosterImpl(InsertionTopicProxy insertionTopicProxy, DataHubKeyRenderer keyRenderer) {
		this.keyRenderer = keyRenderer;
		this.insertionTopicProxy = insertionTopicProxy;
	}

	@Override
	public void subscribe(final String channelName, Consumer<String> consumer) {
		MessageListener<String> messageListener = addTopicListenerForChannel( channelName, consumer );
		consumerToMessageListener.put( new ChannelConsumer( channelName, consumer ), messageListener );
	}

	private MessageListener<String> addTopicListenerForChannel(final String channelName, final Consumer<String> consumer) {
		logger.info("Adding new message listener for websocket hazelcast queue for channel " + channelName);
		MessageListener<String> messageListener = new HazelcastSubscriber(consumer, keyRenderer);
		insertionTopicProxy.subscribe(channelName, messageListener);
		return messageListener;
	}

	@Override
	public void unsubscribe(String channelName, Consumer<String> subscription) {
		MessageListener<String> messageListener = consumerToMessageListener.remove(new ChannelConsumer(channelName, subscription));
		logger.info("Removing message listener for websocket hazelcast queue for channel " + channelName);
		insertionTopicProxy.unsubscribe(channelName, messageListener);
	}

	@Override
	public int getTotalSubscriberCount() {
		return consumerToMessageListener.size();
	}

	@Override
	public Collection<Consumer<String>> getSubscribers(String channelName) {
		List<Consumer<String>> result = new ArrayList<>();
        for (ChannelConsumer channelConsumer : consumerToMessageListener.keySet()) {
            if (channelConsumer.channelName.equals(channelName)) {
                result.add(channelConsumer.consumer);
            }
        }
        return result;
	}

	private static class ChannelConsumer {
		private final String channelName;
		private final Consumer<String> consumer;

		ChannelConsumer( String channelName, Consumer<String> consumer ) {

			this.channelName = channelName;
			this.consumer = consumer;
		}

		public boolean equals( Object o ) {
			if ( this == o ) return true;
			if ( !(o instanceof ChannelConsumer) ) return false;

			ChannelConsumer that = (ChannelConsumer) o;

			if ( !channelName.equals( that.channelName ) ) return false;
			if ( !consumer.equals( that.consumer ) ) return false;

			return true;
		}

		public int hashCode() {
			int result = channelName.hashCode();
			result = 31 * result + consumer.hashCode();
			return result;
		}
	}
}
