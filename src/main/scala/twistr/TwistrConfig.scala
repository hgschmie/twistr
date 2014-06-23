package twistr

;

import org.skife.config.Config;

trait TwistrConfig {
  @Config(Array("twistr.consumer-key"))
  def getConsumerKey(): String

  @Config(Array("twistr.consumer-secret"))
  def getConsumerSecret(): String

  @Config(Array("twistr.access-token-key"))
  def getAccessTokenKey(): String

  @Config(Array("twistr.access-token-secret"))
  def getAccessTokenSecret(): String
  
  @Config(Array("twistr.kafka.brokers"))
  def getBrokers(): String
}
