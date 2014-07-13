/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package twistr

import java.io.{BufferedReader, Closeable, InputStreamReader}
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Properties
import java.util.concurrent.Executors

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.hash.{BloomFilter, Funnel, PrimitiveSink}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.inject.Inject
import com.nesscomputing.lifecycle.LifecycleStage
import com.nesscomputing.lifecycle.guice.OnStage
import com.nesscomputing.logging.Log
import com.squareup.okhttp.OkHttpClient
import kafka.producer.{Producer, ProducerConfig, ProducerData}
import kafka.serializer.StringEncoder
import oauth.signpost.basic.DefaultOAuthConsumer
import twistr.TwistrClient._

object TwistrClient {
  private val LOG = Log.findLog
  val MAX_TWEETS = 500000
}

class TwistrClient @Inject()(config: TwistrConfig, objectMapper: ObjectMapper) extends Runnable {
  @volatile private var running = true
  private val executor = Executors.newFixedThreadPool(1, (new ThreadFactoryBuilder).setNameFormat("twitter-pipe-%s").setDaemon(false).build)
  private val consumer = new DefaultOAuthConsumer(config.getConsumerKey(), config.getConsumerSecret())

  private val idFunnel = new Funnel[Long] {
    def funnel(value: Long, into: PrimitiveSink) {
      into.putLong(value)
    }
  }

  private val filter = BloomFilter.create(idFunnel, MAX_TWEETS, 0.1)

  consumer.setTokenWithSecret(config.getAccessTokenKey(), config.getAccessTokenSecret())

  val props = new Properties()
  props.put("zk.connect", config.getZookeeper())
  props.put("partitioner.class", classOf[LongPartitioner[Long]].getName)
  props.put("serializer.class", classOf[StringEncoder].getName)
  props.put("serializer.encoding", "UTF8")
  props.put("request.required.acks", "1")
  private val producerConfig = new ProducerConfig(props)

  @OnStage(LifecycleStage.START)
  def start() {
    executor.submit(this)
  }

  @OnStage(LifecycleStage.STOP)
  def stop() {
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

  override def run() {
    val client = new OkHttpClient
    val producer = new Producer[String, String](producerConfig)

    while (running) {
      try {
        val connection = client.open(new URL("https://stream.twitter.com/1.1/statuses/sample.json"))
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

  def processStream(reader: BufferedReader, producer: Producer[String, String]) {
    var count = 0
    while (count < MAX_TWEETS) {
      val msg = reader.readLine
      val tree = objectMapper.readTree(msg)

      if (tree.has("delete")) {
        val id = tree.path("delete").path("status").path("id").longValue
        val message = new ProducerData[String, String]("twitter_deletes", msg)
        producer.send(message)
      }
      else {
        val id = tree.path("id").longValue

        if (filter.mightContain(id)) {
          LOG.warn("Tweet " + id + " was already sent in!")
          LOG.info("Tweet: " + tree.toString)
        }
        else {
          filter.put(id)
          val message = new ProducerData[String, String]("twitter_feed", msg)
          producer.send(message)
          count = count + 1
          if (count % 1000 == 0) {
            LOG.info("Tweet count: " + count)
          }
        }
      }
    }
  }
}
