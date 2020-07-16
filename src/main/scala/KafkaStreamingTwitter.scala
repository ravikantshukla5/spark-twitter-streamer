package org.dreambig.aad.xpression

import java.util.Properties
import java.util.concurrent.LinkedBlockingDeque

import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming.{Seconds, StreamingContext}
import twitter4j._
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder

object KafkaStreamingTwitter extends App {

  //val sc = new SparkContext(new SparkConf().setAppName("KafkaTwitter"))
  //val ssc = new StreamingContext(sc, Seconds(5))
  var cb = new ConfigurationBuilder
  cb = cb.setDebugEnabled(true)
  cb = cb.setOAuthConsumerKey("i438u9UjKVsyFnAEhlf9kLGfe")
  cb = cb.setOAuthConsumerSecret("wTDWHtH3J3S1m3we0fu2p2WcB9AAgjyya2ZKKWhy0sEdjlKKsp")
  cb = cb.setOAuthAccessToken("307765997-7LN5WzFa55zSFmJILwy9LaoHT4ADt3rKbqw91z5G")
  cb = cb.setOAuthAccessTokenSecret("fJGQSFaSeGa8z3r7oIVBtjuYZm61dmz2ABa9sffDMSeB8")
  //val auth = new OAuthAuthorization(cb.build())
  val tweets = new TwitterStreamFactory(cb.build()).getInstance()
  val queue = new LinkedBlockingDeque[Status]()
  val listener = new StatusListener {
    override def onStatus(status: Status): Unit = {
      queue.add(status)
    }

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {

    }

    override def onTrackLimitationNotice(numberOfLimitedStatuses: Int): Unit = {

    }

    override def onScrubGeo(userId: Long, upToStatusId: Long): Unit = {

    }

    override def onStallWarning(warning: StallWarning): Unit = {

    }

    override def onException(ex: Exception): Unit = {
      ex.printStackTrace()
    }
  }

  tweets.addListener(listener)
  val query = new FilterQuery().track("narendramodi")
  tweets.filter(query)
  Thread.sleep(5000)
  Thread.currentThread().setContextClassLoader(null)
  val props = new Properties()
  props.put("bootstrap.servers", "localhost:9092");
  props.put("acks", "all");
  props.put("retries", new Integer(0))
  props.put("batch.size", new Integer(16384))
  props.put("linger.ms", new Integer(1))
  props.put("buffer.memory", new Integer(33554432))

  /*props.put("key.serializer",
    "org.apache.kafka.common.serializa-tion.StringSerializer")
  props.put("value.serializer",
    "org.apache.kafka.common.serializa-tion.StringSerializer")*/
  val producer = new KafkaProducer[String,String](props)

  var i = 0
  var j = 0

  while(i < 10) {
    val ret = queue.poll();

    if (ret == null) {
      Thread.sleep(100);
      i = i+1
    }else {
      for(hashtage <- ret.getHashtagEntities()) {
        System.out.println("Hashtag: " + hashtage.getText());
        j=j+1
        producer.send(new ProducerRecord("tweets", Integer.toString(j), hashtage.getText()))
      }
    }
  }
  producer.close()
  Thread.sleep(5000)
  tweets.shutdown()

}
