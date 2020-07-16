import java.sql.DriverManager
import java.util.Properties

import com.cybozu.labs.langdetect.DetectorFactory
import org.apache.spark.sql.SQLContext
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import twitter4j.auth.OAuthAuthorization
import twitter4j.conf.ConfigurationBuilder

import scala.util.Try

object SparkTwitter {

  def main(args: Array[String]): Unit = {
    val in = getClass.getResourceAsStream("/application.conf")
    val props = new Properties()
    props.load(in)
    DetectorFactory.loadProfile("src/main/resources/profiles")
    val sc = new SparkContext(new SparkConf().setMaster(props.getProperty("app.master")).setAppName("SparkTwitter"))
    val ssc = new StreamingContext(sc, Seconds(5))
    var cb = new ConfigurationBuilder
    cb = cb.setDebugEnabled(true)
    cb = cb.setOAuthConsumerKey("i438u9UjKVsyFnAEhlf9kLGfe")
    cb = cb.setOAuthConsumerSecret("wTDWHtH3J3S1m3we0fu2p2WcB9AAgjyya2ZKKWhy0sEdjlKKsp")
    cb = cb.setOAuthAccessToken("307765997-7LN5WzFa55zSFmJILwy9LaoHT4ADt3rKbqw91z5G")
    cb = cb.setOAuthAccessTokenSecret("fJGQSFaSeGa8z3r7oIVBtjuYZm61dmz2ABa9sffDMSeB8")
    val auth = new OAuthAuthorization(cb.build())
    val tweets = TwitterUtils.createStream(ssc, Some(auth))
    // JDBC related code
    val url = "jdbc:mysql://localhost:3306/spark"
    val propSql = new Properties()
    propSql.setProperty("user","root")
    propSql.setProperty("password","rootpasswordgiven")
    val sparkSql = new SQLContext(sc)
    val conn= DriverManager.getConnection(url,propSql.getProperty("user"),propSql.getProperty("password"))
    val sql = conn.prepareStatement("INSERT INTO 'spark'.'tweets' ('id', 'user', 'created_at', 'user_location', 'location', 'text', 'hashtags', 'retweet', 'language', 'sentiment') VALUES (?,?,?,?,?,?,?,?,?,?)")
    //tweets.saveAsTextFiles("file:////home/ravi/tweets/","json")
   // val enTweets = tweets.filter(_.getLang == "en").map(t => (t.getUser.getLocation,t.getUser.getName))
    //enTweets.print()
    //tweets.saveAsTextFiles("file:////home/ravi/tweets/")
    /*val hashTag = tweets.flatMap(
      t => t.getHashtagEntities)
    val hashTagCount = hashTag.map(t => ("#"+t.getText,1))
    val aggrTweets = hashTagCount.reduceByKeyAndWindow((l, r) => {l + r},Seconds(10))
    val sortedTopCounts10 = aggrTweets.transform(rdd =>
      rdd.sortBy(hashtagPair => hashtagPair._2, false))
    sortedTopCounts10.foreachRDD(rdd => {
      val topList = rdd.take(10)
      println("\nPopular topics in last 10 seconds (%s total):".format(rdd.count()))
      topList.foreach{case (tag, count) => println("%s (%d tweets)".format(tag, count))}
    })*/

    //filterTweets.saveAsTextFiles("file:////home/ravi/tweets/")

    tweets.foreachRDD { (rdd, time) =>
      val tweetRdd = rdd.filter(_.getLang == "en").filter(t => t.getUser.getLocation != null).map(t => {
        TweetSchema(
          t.getUser.getScreenName,
          t.getCreatedAt.toInstant.toString,
          t.getUser.getLocation,
          Option(t.getGeoLocation).map(geo => {
            s"${geo.getLatitude},${geo.getLongitude}"
          }).toString,
          t.getText,
          t.getHashtagEntities.map(_.getText).toString,
          t.getRetweetCount,
          detectLanguage(t.getText),
          SentimentAnalysisUtils.detectSentiment(t.getText).toString
        )

      })
      import sparkSql.implicits._
      tweetRdd.toDF.write.mode("append").jdbc(url,"tweets",propSql)
    }
    ssc.start()
    ssc.awaitTermination()
  }
  def detectLanguage(text: String) : String = {

    Try {
      val detector = DetectorFactory.create()
      detector.append(text)
      detector.detect()
    }.getOrElse("unknown")

  }

}
