package com.flightstats.hub.model;

import com.flightstats.hub.util.TimeUtil;
import com.google.common.base.Optional;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ContentKeyTest {

    @Test
    public void testUrlKey() throws Exception {
        ContentKey contentKey = new ContentKey();
        ContentKey cycled = ContentKey.fromUrl(contentKey.toUrl()).get();
        assertEquals(contentKey, cycled);
    }

    @Test
    public void testCompareTime() throws Exception {
        DateTime now = TimeUtil.now();
        TreeSet<ContentKey> keys = new TreeSet<>();
        for (int i = 0; i < 10; i++) {
            keys.add(new ContentKey(now.plusMinutes(i), "A"));
        }
        assertEquals(10, keys.size());
        long previous = 0;
        for (ContentKey key : keys) {
            long current = key.getMillis();
            assertTrue(current > previous);
            previous = current;
        }
    }

    @Test
    public void testCompareHash() throws Exception {
        DateTime now = TimeUtil.now();
        TreeSet<ContentKey> keys = new TreeSet<>();
        for (int i = 0; i < 10; i++) {
            keys.add(new ContentKey(now, "A" + i));
        }
        assertEquals(10, keys.size());
        String previous = "";
        for (ContentKey key : keys) {
            String current = key.toUrl();
            assertTrue(current.compareTo(previous) > 0);
            previous = current;
        }
    }

    @Test
    public void testZkCycle() throws Exception {
        ContentKey key = new ContentKey();
        assertEquals(key, key.fromZk(key.toZk()));
    }

    @Test
    public void testFullUrl() {
        Optional<ContentKey> optional = ContentKey.fromFullUrl("http://hub/channel/load_test_2/2015/01/23/21/11/19/407/L7QtaY");
        assertTrue(optional.isPresent());
        ContentKey contentKey = optional.get();
        assertEquals("2015/01/23/21/11/19/407/L7QtaY", contentKey.toString());
    }

    @Test
    public void testCompareMinutePath() {
        MinutePath minutePath = new MinutePath();
        ContentKey contentKey = new ContentKey(minutePath.getTime(), "0");
        assertTrue(contentKey.compareTo(minutePath) < 0);

        ContentKey nextSeconds = new ContentKey(minutePath.getTime().plusSeconds(59), "0");
        assertTrue(nextSeconds.compareTo(minutePath) < 0);

        ContentKey nextMinute = new ContentKey(minutePath.getTime().plusMinutes(1), "0");
        assertTrue(nextMinute.compareTo(minutePath) > 0);
    }
}

