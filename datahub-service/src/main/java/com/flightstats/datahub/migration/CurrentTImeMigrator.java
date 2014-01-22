package com.flightstats.datahub.migration;

import com.flightstats.datahub.dao.ChannelService;
import com.flightstats.datahub.model.ChannelConfiguration;
import com.flightstats.datahub.model.Content;
import com.flightstats.datahub.model.ContentKey;
import com.flightstats.datahub.model.SequenceContentKey;
import com.flightstats.datahub.util.Sleeper;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CurrentTimeMigrator implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(CurrentTimeMigrator.class);


    private final ChannelService channelService;
    private final String channel;
    private final ChannelUtils channelUtils;
    private String channelUrl;
    private ChannelConfiguration configuration;

    public CurrentTimeMigrator(ChannelService channelService, String host, String channel, ChannelUtils channelUtils) {
        this.channelService = channelService;
        this.channel = channel;
        this.channelUtils = channelUtils;
        channelUrl = "http://" + host + "/channel/" + channel + "/";
    }

    @Override
    public void run() {
        try {
            if (initialize()) return;
        } catch (IOException e) {
            logger.warn("unable to parse json for " + channelUrl, e);
            return;
        }
        while (migrate()) {
            Sleeper.sleep(5000);
        }
    }

    private boolean initialize() throws IOException {
        configuration = channelUtils.getConfiguration(channelUrl);
        logger.info("found config " + this.configuration);
        //todo - gfm - 1/20/14 - this should verify the TTL hasn't changed
        if (!channelService.channelExists(channel)) {
            channelService.createChannel(this.configuration);
        }
        return false;
    }

    private boolean migrate() {

        long sequence = getStartingSequence();
        if (sequence == ChannelUtils.NOT_FOUND) {
            return false;
        }
        logger.info("starting " + channelUrl + " migration at " + sequence);
        Optional<Content> content = channelUtils.getContent(channelUrl, sequence);
        while (content.isPresent()) {
            channelService.insert(channel, content.get());
            sequence++;
            content = channelUtils.getContent(channelUrl, sequence);
        }

        return true;
    }


    private long getStartingSequence() {
        Optional<ContentKey> lastUpdatedKey = channelService.findLastUpdatedKey(channel);
        if (lastUpdatedKey.isPresent()) {
            SequenceContentKey contentKey = (SequenceContentKey) lastUpdatedKey.get();
            if (contentKey.getSequence() == SequenceContentKey.START_VALUE) {
                return searchForStartingKey();
            }
            return contentKey.getSequence() + 1;
        }
        logger.warn("problem getting starting sequence " + channelUrl);
        return ChannelUtils.NOT_FOUND;
    }

    private long searchForStartingKey() {
        //this may not play well with discontinuous sequences
        logger.info("searching the key space for " + channelUrl);
        Optional<Long> latestSequence = channelUtils.getLatestSequence(channelUrl);
        if (!latestSequence.isPresent()) {
            return SequenceContentKey.START_VALUE + 1;
        }
        long high = latestSequence.get();
        //todo - gfm - 1/20/14 - would be useful to pull this out into something that can be rigorously tested
        long low = SequenceContentKey.START_VALUE;
        long lastExists = high;
        while (low <= high && (high - low) > 1) {
            long middle = low + (high - low) / 2;
            //do get on middle
            if (existsAndNotYetExpired(middle)) {
                high = middle - 1;
                lastExists = middle;
            } else {
                low = middle;
            }
            logger.info("low=" + low + " high=" + high + " middle=" + middle);
        }
        logger.info("returning starting key " + lastExists);
        return lastExists;
    }

    /**
     * We want to return a starting id that exists, and isn't going to be expired immediately.
     */
    private boolean existsAndNotYetExpired(long id) {
        Optional<DateTime> creationDate = channelUtils.getCreationDate(channelUrl, id);
        if (!creationDate.isPresent()) {
            return false;
        }
        if (configuration.getTtlMillis() == null) {
            return true;
        }
        long ttlMillis = configuration.getTtlMillis();
        DateTime tenMinuteOffset = new DateTime().minusMillis((int) ttlMillis).plusMinutes(10);
        return creationDate.get().isAfter(tenMinuteOffset);
    }

}
