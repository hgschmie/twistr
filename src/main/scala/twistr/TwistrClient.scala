package twistr

import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.Executors
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.hash.{BloomFilter, PrimitiveSink, Funnel}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Inject
import com.nesscomputing.lifecycle.guice.OnStage
import com.nesscomputing.logging.Log
import com.squareup.okhttp.OkHttpClient
import kafka.producer.KeyedMessage
import kafka.producer.Producer
import kafka.producer.ProducerConfig
import oauth.signpost.basic.DefaultOAuthConsumer
import com.nesscomputing.lifecycle.LifecycleStage
import twistr.TwistrClient._
import kafka.serializer.StringEncoder
import scala.collection.JavaConverters._
object TwistrClient {
  private val LOG = Log.findLog
  val MAX_TWEETS = 500000
}

class TwistrClient @Inject() (config: TwistrConfig, objectMapper: ObjectMapper) extends Runnable
{
  @volatile private var running = true
  private val executor = Executors.newFixedThreadPool(1, (new ThreadFactoryBuilder).setNameFormat("twitter-pipe-%s").setDaemon(false).build)
  private val consumer = new DefaultOAuthConsumer(config.getConsumerKey(), config.getConsumerSecret())

  private val idFunnel = new Funnel[Long]
  {
    def funnel(value: Long, into: PrimitiveSink)
    {
      into.putLong(value)
    }
  }

  private val filter = BloomFilter.create(idFunnel, MAX_TWEETS, 0.1)

  consumer.setTokenWithSecret(config.getAccessTokenKey(), config.getAccessTokenSecret())

  val props = new Properties()
  props.put("metadata.broker.list", config.getBrokers())
  props.put("serializer.class", classOf[StringEncoder].getName)
  props.put("key.serializer.class", classOf[LongEncoder].getName)
  props.put("partitioner.class", classOf[LongPartitioner].getName)
  props.put("serializer.encoding", "UTF8")
  props.put("request.required.acks", "1")
  private val producerConfig = new ProducerConfig(props)

  @OnStage(LifecycleStage.START)
  def start()
  {
    executor.submit(this)
  }

  @OnStage(LifecycleStage.STOP)
  def stop()
  {
    this.running = false
    executor.shutdown
  }

  def closer[A, R <: Closeable](r: R)(f: R => A): A =
    try {
      f(r)
    }
    finally {
      r.close
    }

  override def run()
  {
    val client = new OkHttpClient
    val producer = new Producer[Long, String](producerConfig)

    while (running) {
      try {
        val connection = client .open(new URL("https://stream.twitter.com/1.1/statuses/sample.json"))
        consumer.sign(connection)
        connection.connect

        val is = connection.getInputStream

        closer(new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 8192))(reader => processStream(reader, producer))
      }
      catch {
        case t: Throwable => LOG.error(t, "Error:")
      }
    }
  }

  def processStream(reader: BufferedReader, producer: Producer[Long, String])
  {
    var count = 0
    while (count < MAX_TWEETS) {
      val msg = reader.readLine
      val tree = objectMapper.readTree(msg)

      if (tree.has("delete")) {
        val id = tree.at("/delete/status/id").longValue;
        val keyedMessage = new KeyedMessage("twitter_deletes", id, msg)
        producer.send(keyedMessage)
      }
      else {
        val id = tree.at("/id").longValue

        if (filter.mightContain(id)) {
          LOG.warn("Tweet " + id + " was already sent in!")
          LOG.info("Tweet: " + tree.toString)
        }
        else {
          filter.put(id)
          val keyedMessage = new KeyedMessage("twitter_feed", id, msg)
          producer.send(keyedMessage)
          count = count + 1
          if (count % 1000 == 0) {
            LOG.info("Tweet count: " + count)
          }
        }
      }
    }
  }
}
