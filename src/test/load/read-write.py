# locust.py

import json
import string
import random

from locust import Locust, TaskSet, task




# Usage:
# locust -f read-write.py -H http://hub.svc.prod

# Be sure to update gevent to get around DNS issue
# pip install https://github.com/surfly/gevent/releases/download/1.0rc3/gevent-1.0rc3.tar.gz

#todo variable timing from command line

class WebsiteTasks(TaskSet):
    channelNum = 0

    def on_start(self):
        WebsiteTasks.channelNum += 1
        #todo make byte size this a command line var
        self.number = WebsiteTasks.channelNum * 1000
        self.payload = self.payload_generator(self.number)
        print("payload size " + str(self.payload.__sizeof__()))
        self.channel = "riak_test_" + str(WebsiteTasks.channelNum)
        self.count = 0
        payload = {"name": self.channel, "ttlDays": "100"}
        self.client.post("/channel",
                         data=json.dumps(payload),
                         headers={"Content-Type": "application/json"}
        )

    @task
    def index(self):
        payload = {"name": self.payload, "count": self.count}
        response = self.client.post("/channel/" + self.channel,
                         data=json.dumps(payload),
                         headers={"Content-Type": "application/json"}
        )
        print "Response status code:", response.status_code
        print "Response content:", response.content
        self.count += 1

    def payload_generator(self, size, chars=string.ascii_uppercase + string.digits):
        return ''.join(random.choice(chars) for x in range(size))


class WebsiteUser(Locust):
    task_set = WebsiteTasks
    min_wait = 500
    max_wait = 1000
